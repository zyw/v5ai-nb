package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.linpeilie.Converter;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.storage.properties.MinioProperties;
import xin.v5ai.nb.platform.domain.PlmResource;
import xin.v5ai.nb.platform.domain.bo.PlmResourceBo;
import xin.v5ai.nb.platform.domain.vo.PlmResourceVo;
import xin.v5ai.nb.platform.mapper.PlmResourceMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlmResourceServiceImpl} 的 Mockito 单测：mock Mapper 与 MinioClient，
 * 本地临时目录验证上传/查询/下载/删除与路径穿越防护。
 *
 * @author ZYW
 * @since 2026-09-02
 */
class PlmResourceServiceImplTest {

    private final List<PlmResource> resources = new ArrayList<>();

    private Path uploadRoot;
    private PlmResourceMapper resourceMapper;
    private MinioClient minioClient;
    private PlmResourceServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        // 纯单测环境没有 MyBatis 启动流程，手动初始化实体 TableInfo，
        // 使服务内 LambdaQueryWrapper 能解析列名。
        var configuration = new MybatisConfiguration();
        var assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PlmResource.class);

        // MapstructUtils 在类加载时通过 SpringUtil 取 Converter Bean，
        // 注册一个按属性名拷贝的 Converter 使 BO/VO 转换可独立测试。
        var context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("converter", new CopyConverter());
        context.refresh();
        new SpringUtil().setApplicationContext(context);

        uploadRoot = Files.createTempDirectory("plm-resource-test");
        resourceMapper = mock(PlmResourceMapper.class);
        when(resourceMapper.selectById(any())).thenAnswer(inv -> findById(resources, inv.getArgument(0)));
        when(resourceMapper.selectByIds(any())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return resources.stream().filter(r -> ids.contains(r.getId())).toList();
        });
        doAnswer(inv -> {
            var entity = inv.getArgument(0, PlmResource.class);
            entity.setId((long) (resources.size() + 1));
            resources.add(entity);
            return 1;
        }).when(resourceMapper).insert(any(PlmResource.class));
        doAnswer(inv -> {
            var update = inv.getArgument(0, PlmResource.class);
            var existing = findById(resources, update.getId());
            if (existing != null) {
                if (update.getBizType() != null) {
                    existing.setBizType(update.getBizType());
                }
                if (update.getBizId() != null) {
                    existing.setBizId(update.getBizId());
                }
                if (update.getUpdateDt() != null) {
                    existing.setUpdateDt(update.getUpdateDt());
                }
            }
            return 1;
        }).when(resourceMapper).updateById(any(PlmResource.class));
        doAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            resources.removeIf(r -> ids.contains(r.getId()));
            return ids.size();
        }).when(resourceMapper).deleteByIds(any());

        minioClient = mock(MinioClient.class);
        var props = new MinioProperties();
        props.setBucket("v5ai");
        service = new PlmResourceServiceImpl(resourceMapper, minioClient, props);
        ReflectionTestUtils.setField(service, "localDir", uploadRoot.toString());
        // 存储类型由配置 v5ai.storage.type 决定，单测默认 LOCAL
        ReflectionTestUtils.setField(service, "storageType", "LOCAL");
    }

    @Test
    void uploadStoresFileLocallyAndRegistersRecord() throws Exception {
        byte[] content = "hello v5ai".getBytes(StandardCharsets.UTF_8);

        var vo = service.upload("测试报告.txt", content, "text/plain", "DOCUMENT", 42L);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getOriginalName()).isEqualTo("测试报告.txt");
        assertThat(vo.getFileSize()).isEqualTo(content.length);
        assertThat(vo.getMimeType()).isEqualTo("text/plain");
        assertThat(vo.getStorageType()).isEqualTo("LOCAL");
        assertThat(vo.getBizType()).isEqualTo("DOCUMENT");
        assertThat(vo.getBizId()).isEqualTo(42L);
        assertThat(vo.getCreateDt()).isNotNull();
        assertThat(vo.getAccessUrl()).isEqualTo("/api/admin/resources/1/preview");
        // 文件实际写入本地目录，存储键为 yyyyMM/uuid.txt 相对路径
        assertThat(vo.getStorageKey()).matches("\\d{6}/[0-9a-f]{32}\\.txt");
        assertThat(Files.readAllBytes(uploadRoot.resolve(vo.getStorageKey()))).isEqualTo(content);
    }

    @Test
    void uploadRejectsInvalidParameters() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.upload("a.txt", content, null, "INVALID", null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("业务类型不合法");
        assertThatThrownBy(() -> service.upload("a.txt", new byte[0], null, "GENERAL", null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能为空");
        // 失败路径不落库
        assertThat(resources).isEmpty();
    }

    @Test
    void uploadRejectsInvalidConfiguredStorageType() {
        // 存储类型来自配置，配置不合法时上传直接失败
        ReflectionTestUtils.setField(service, "storageType", "S3");
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.upload("a.txt", content, null, "GENERAL", null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存储类型配置不合法");
        assertThat(resources).isEmpty();
    }

    @Test
    void uploadWithMinioUsesObjectStore() throws Exception {
        ReflectionTestUtils.setField(service, "storageType", "MINIO");
        // bucket 不存在时自动创建后写入（makeBucket 为 void，mock 默认 no-op）
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        byte[] content = "png".getBytes(StandardCharsets.UTF_8);

        var vo = service.upload("avatar.png", content, "image/png", "AVATAR", null);

        assertThat(vo.getStorageType()).isEqualTo("MINIO");
        // access_url 与 LOCAL 一致，统一为应用内鉴权路径（不存 MinIO 对象直链）
        assertThat(vo.getAccessUrl()).isEqualTo("/api/admin/resources/1/preview");
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadWithMinioReusesExistingBucket() throws Exception {
        ReflectionTestUtils.setField(service, "storageType", "MINIO");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        service.upload("a.png", "x".getBytes(StandardCharsets.UTF_8), "image/png", "AVATAR", null);

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void queryPageListBuildsFilters() {
        final Wrapper<PlmResource>[] captured = new Wrapper[1];
        when(resourceMapper.selectVoPage(any(), any())).thenAnswer(inv -> {
            captured[0] = inv.getArgument(1);
            return new Page<PlmResourceVo>(1, 10, 0);
        });
        var bo = new PlmResourceBo();
        bo.setOriginalName("报告");
        bo.setBizType("DOCUMENT");
        bo.getParams().put("beginTime", "2026-08-01 00:00:00");
        bo.getParams().put("endTime", "2026-08-31 23:59:59");

        var result = service.queryPageList(bo, new PageQuery(10, 1));

        assertThat(result.getTotal()).isZero();
        String sql = captured[0].getSqlSegment();
        assertThat(sql).contains("original_name LIKE");
        assertThat(sql).contains("biz_type =");
        assertThat(sql).contains("create_dt BETWEEN");
    }

    @Test
    void loadContentReadsLocalFile() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        var vo = service.upload("a.txt", content, "text/plain", "GENERAL", null);

        var loaded = service.loadContent(vo.getId());

        assertThat(loaded.bytes()).isEqualTo(content);
        assertThat(loaded.originalName()).isEqualTo("a.txt");
        assertThat(loaded.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void loadContentRejectsPathTraversal() {
        var evil = new PlmResource();
        evil.setId(1L);
        evil.setStorageKey("../escape.txt");
        evil.setStorageType("LOCAL");
        resources.add(evil);

        assertThatThrownBy(() -> service.loadContent(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非法的存储键");
    }

    @Test
    void deleteWithValidByIdsRemovesFileAndRecord() {
        byte[] content = "bye".getBytes(StandardCharsets.UTF_8);
        var vo = service.upload("b.txt", content, "text/plain", "ATTACHMENT", null);
        Path file = uploadRoot.resolve(vo.getStorageKey());
        assertThat(file).exists();

        var flag = service.deleteWithValidByIds(List.of(vo.getId()), true);

        assertThat(flag).isTrue();
        assertThat(resources).isEmpty();
        assertThat(file).doesNotExist();
    }

    @Test
    void deleteWithValidByIdsRejectsMissingId() {
        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(999L), true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("资源不存在");
    }

    /**
     * 按属性名拷贝的 Converter：单测中替代 mapstruct-plus 的 Spring 装配。
     */
    private static class CopyConverter extends Converter {
        @Override
        public <S, T> T convert(S source, Class<T> desc) {
            if (source == null) {
                return null;
            }
            try {
                T target = desc.getDeclaredConstructor().newInstance();
                BeanUtil.copyProperties(source, target);
                return target;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static PlmResource findById(List<PlmResource> list, Long id) {
        return list.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }
}
