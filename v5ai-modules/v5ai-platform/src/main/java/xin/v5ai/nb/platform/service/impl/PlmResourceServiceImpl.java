package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.common.storage.properties.MinioProperties;
import xin.v5ai.nb.platform.domain.PlmResource;
import xin.v5ai.nb.platform.domain.bo.PlmResourceBo;
import xin.v5ai.nb.platform.domain.vo.PlmResourceVo;
import xin.v5ai.nb.platform.mapper.PlmResourceMapper;
import xin.v5ai.nb.platform.service.IPlmResourceService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * <p>
 * 通用资源存储服务实现类：本地磁盘 / MinIO 双后端文件读写 + 资源元数据 CRUD。
 * </p>
 *
 * @author ZYW
 * @since 2026-09-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlmResourceServiceImpl implements IPlmResourceService {

    /**
     * 存储类型: 本地磁盘
     */
    private static final String STORAGE_LOCAL = "LOCAL";

    /**
     * 存储类型: MinIO
     */
    private static final String STORAGE_MINIO = "MINIO";

    /**
     * 业务类型合法取值
     */
    private static final Set<String> BIZ_TYPES = Set.of("GENERAL", "AVATAR", "ATTACHMENT", "DOCUMENT");

    /**
     * 常见扩展名 -> MIME 类型（上传未携带 Content-Type 时的兜底）
     */
    private static final Map<String, String> EXT_MIME = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("zip", "application/zip"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("wav", "audio/wav")
    );

    private final PlmResourceMapper resourceMapper;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    /**
     * 本地存储根目录（相对应用运行目录）
     */
    @Value("${v5ai.storage.local-dir:./v5ai-upload}")
    private String localDir;

    /**
     * 上传存储类型（由配置文件 v5ai.storage.type 决定，LOCAL=本地磁盘 MINIO=MinIO）
     */
    @Value("${v5ai.storage.type:LOCAL}")
    private String storageType;

    @Override
    public PageResult<PlmResourceVo> queryPageList(PlmResourceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PlmResource> lqw = buildQueryWrapper(bo);
        Page<PlmResourceVo> result = resourceMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public PlmResourceVo queryById(Long id) {
        return MapstructUtils.convert(exist(id), PlmResourceVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlmResourceVo upload(String originalFilename, byte[] content, String contentType,
                                String bizType, Long bizId) {
        return upload(originalFilename, content, contentType, bizType, bizId, LoginHelper.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlmResourceVo upload(String originalFilename, byte[] content, String contentType,
                                String bizType, Long bizId, Long createdBy) {
        if (content == null || content.length == 0) {
            throw new ServiceException("上传文件内容不能为空");
        }
        String name = sanitizeFilename(originalFilename);
        if (StrUtil.isBlank(name)) {
            throw new ServiceException("文件名不能为空");
        }
        String type = StrUtil.isBlank(storageType) ? STORAGE_LOCAL : storageType.toUpperCase(Locale.ROOT);
        if (!STORAGE_LOCAL.equals(type) && !STORAGE_MINIO.equals(type)) {
            throw new ServiceException("存储类型配置不合法: v5ai.storage.type 仅支持 LOCAL/MINIO");
        }
        String biz = StrUtil.isBlank(bizType) ? "GENERAL" : bizType.toUpperCase(Locale.ROOT);
        if (!BIZ_TYPES.contains(biz)) {
            throw new ServiceException("业务类型不合法: 仅支持 AVATAR/ATTACHMENT/DOCUMENT/GENERAL");
        }

        String mime = StrUtil.isNotBlank(contentType) ? contentType : detectMimeType(name);
        String storageKey = generateStorageKey(name);
        storeFile(type, storageKey, content, mime);

        LocalDateTime now = LocalDateTime.now();
        PlmResource add = new PlmResource();
        add.setStorageKey(storageKey);
        add.setOriginalName(name);
        add.setFileSize((long) content.length);
        add.setMimeType(mime);
        add.setStorageType(type);
        add.setBizType(biz);
        add.setBizId(bizId);
        add.setCreatedBy(createdBy);
        add.setCreateDt(now);
        add.setUpdateDt(now);
        try {
            resourceMapper.insert(add);
        } catch (RuntimeException e) {
            removeFileQuietly(type, storageKey);
            throw e;
        }
        // 入库成功后回填访问 URL（统一为应用内鉴权路径，与存储后端无关）
        add.setAccessUrl(buildAccessUrl(add.getId()));
        resourceMapper.updateById(add);
        return MapstructUtils.convert(add, PlmResourceVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(PlmResourceBo bo) {
        PlmResource existing = exist(bo.getId());
        String biz = bo.getBizType() == null ? null : bo.getBizType().toUpperCase(Locale.ROOT);
        if (biz != null && !BIZ_TYPES.contains(biz)) {
            throw new ServiceException("业务类型不合法: 仅支持 AVATAR/ATTACHMENT/DOCUMENT/GENERAL");
        }
        PlmResource update = new PlmResource();
        update.setId(existing.getId());
        update.setBizType(biz == null ? existing.getBizType() : biz);
        update.setBizId(bo.getBizId() == null ? existing.getBizId() : bo.getBizId());
        update.setUpdateDt(LocalDateTime.now());
        return resourceMapper.updateById(update) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("主键不能为空");
        }
        List<PlmResource> resources = resourceMapper.selectByIds(ids);
        if (resources.size() != ids.size()) {
            throw new ServiceException("资源不存在或已被删除");
        }
        for (PlmResource resource : resources) {
            removeFileQuietly(resource.getStorageType(), resource.getStorageKey());
        }
        return resourceMapper.deleteByIds(ids) > 0;
    }

    @Override
    public ResourceContent loadContent(Long id) {
        PlmResource resource = exist(id);
        byte[] bytes = readFile(resource.getStorageType(), resource.getStorageKey());
        return new ResourceContent(bytes, resource.getMimeType(), resource.getOriginalName(),
                resource.getStorageType());
    }

    /**
     * 构造资源列表查询条件：文件名模糊、业务类型、关联业务ID、存储类型、创建时间区间。
     */
    private LambdaQueryWrapper<PlmResource> buildQueryWrapper(PlmResourceBo bo) {
        Map<String, Object> params = bo == null ? Map.of() : bo.getParams();
        return QueryBuilder.lambda(PlmResource.class)
                .likeIfText(PlmResource::getOriginalName, bo == null ? null : bo.getOriginalName())
                .eqIfText(PlmResource::getBizType, bo == null ? null : bo.getBizType())
                .eqIfPresent(PlmResource::getBizId, bo == null ? null : bo.getBizId())
                .eqIfText(PlmResource::getStorageType, bo == null ? null : bo.getStorageType())
                .betweenParams(PlmResource::getCreateDt, params, "beginTime", "endTime")
                .orderByDesc(PlmResource::getId)
                .build();
    }

    // ---------- 文件存储 ----------

    /**
     * 写入文件内容到指定后端。
     */
    private void storeFile(String storageType, String storageKey, byte[] content, String mime) {
        if (STORAGE_MINIO.equals(storageType)) {
            storeMinio(storageKey, content, mime);
            return;
        }
        try {
            Path target = resolveLocalPath(storageKey);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("写入本地文件失败: " + storageKey, e);
        }
    }

    /**
     * 读取文件内容。
     */
    private byte[] readFile(String storageType, String storageKey) {
        if (STORAGE_MINIO.equals(storageType)) {
            return readMinio(storageKey);
        }
        try {
            return Files.readAllBytes(resolveLocalPath(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("读取本地文件失败: " + storageKey, e);
        }
    }

    /**
     * 删除文件，文件不存在视为已删除。
     */
    private void removeFileQuietly(String storageType, String storageKey) {
        try {
            if (STORAGE_MINIO.equals(storageType)) {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(storageKey)
                        .build());
            } else {
                Files.deleteIfExists(resolveLocalPath(storageKey));
            }
        } catch (Exception e) {
            log.warn("删除资源文件失败，storageKey={}", storageKey, e);
        }
    }

    /**
     * 解析本地存储路径并校验在根目录内，防止路径穿越。
     */
    private Path resolveLocalPath(String storageKey) {
        Path root = Paths.get(localDir).toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new ServiceException("非法的存储键: " + storageKey);
        }
        return target;
    }

    private void storeMinio(String objectKey, byte[] content, String mime) {
        ensureBucket();
        try (InputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(in, content.length, -1)
                    .contentType(mime)
                    .build());
        } catch (Exception e) {
            log.error("写入 MinIO Key: {} 失败: {}", objectKey,e.getMessage(), e);
            throw new ServiceException("写入 MinIO 失败: " + e.getMessage());
        }
    }

    /**
     * 确保 MinIO bucket 存在：不存在时自动创建（首次上传自愈，避免 NoSuchBucket）。
     */
    private void ensureBucket() {
        String bucket = minioProperties.getBucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已自动创建 MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.error("检查/创建 MinIO bucket 失败: {}， {}", bucket, e.getMessage(), e);
            throw new ServiceException("检查/创建 MinIO bucket 失败: " + e.getMessage());
        }
    }

    private byte[] readMinio(String objectKey) {
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.getBucket())
                .object(objectKey)
                .build())) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取 MinIO 对象失败: " + objectKey, e);
        } catch (Exception e) {
            throw new ServiceException("读取 MinIO 对象失败: " + e.getMessage());
        }
    }

    /**
     * 生成存储键：yyyyMM/{uuid}.{ext}（相对路径或对象 Key）。
     */
    private String generateStorageKey(String originalName) {
        String ext = extensionOf(originalName);
        String file = IdUtil.simpleUUID() + (ext.isEmpty() ? "" : "." + ext);
        return DateTimeFormatter.ofPattern("yyyyMM").format(LocalDateTime.now()) + "/" + file;
    }

    /**
     * 清洗原始文件名：去除路径片段，仅保留文件名。
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    /**
     * 提取小写扩展名（不含点），无扩展名返回空串。
     */
    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}") ? ext : "";
    }

    /**
     * 根据扩展名兜底识别 MIME 类型。
     */
    private String detectMimeType(String filename) {
        return EXT_MIME.getOrDefault(extensionOf(filename), "application/octet-stream");
    }

    /**
     * 构建访问 URL：始终为应用内鉴权路径（统一引用资源 id，与存储后端无关；
     * 经 /preview 由应用代理读取，MinIO 桶设为私有也不影响访问）。
     */
    private String buildAccessUrl(Long id) {
        return "/api/admin/resources/" + id + "/preview";
    }

    private PlmResource exist(Long id) {
        PlmResource existing = resourceMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("资源不存在: " + id);
        }
        return existing;
    }
}
