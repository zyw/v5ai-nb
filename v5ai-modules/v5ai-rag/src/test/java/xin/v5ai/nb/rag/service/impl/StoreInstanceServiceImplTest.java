package xin.v5ai.nb.rag.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.StaticApplicationContext;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.rag.core.StoreConnectionTester;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.domain.bo.StoreConnectionTestBo;
import xin.v5ai.nb.rag.domain.bo.StoreInstanceBo;
import xin.v5ai.nb.rag.domain.vo.StoreConnectionTestVo;
import xin.v5ai.nb.rag.domain.vo.StoreInstanceVo;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreInstanceServiceImpl} 的 Mockito 单测：mock Mapper 并校验 CRUD 与默认实例业务。
 *
 * @author ZYW
 * @since 2026-08-30
 */
class StoreInstanceServiceImplTest {

    private final List<StoreInstance> instances = new ArrayList<>();

    private StoreInstanceMapper storeInstanceMapper;
    private StoreConnectionTester connectionTester;
    private VectorStoreResolver vectorStoreResolver;
    private xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper knowledgeBaseMapper;
    private StoreInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        // 纯单测环境没有 MyBatis 启动流程，这里手动初始化实体 TableInfo，
        // 使服务内 LambdaQueryWrapper / LambdaUpdateWrapper 能解析列名。
        var configuration = new MybatisConfiguration();
        var assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, StoreInstance.class);
        TableInfoHelper.initTableInfo(assistant, xin.v5ai.nb.rag.domain.KnowledgeBase.class);

        // MapstructUtils 在类加载时通过 SpringUtil 取 Converter Bean，
        // 这里注册一个按属性名拷贝的 Converter 使 BO/VO 转换可独立测试。
        var context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("converter", new CopyConverter());
        context.refresh();
        new SpringUtil().setApplicationContext(context);

        storeInstanceMapper = mock(StoreInstanceMapper.class);
        connectionTester = mock(StoreConnectionTester.class);
        vectorStoreResolver = mock(VectorStoreResolver.class);
        knowledgeBaseMapper = mock(xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper.class);
        // 默认无知识库引用该实例，删除校验放行；引用用例单独 stub
        when(knowledgeBaseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(storeInstanceMapper.selectById(any())).thenAnswer(inv -> findById(instances, inv.getArgument(0)));
        when(storeInstanceMapper.selectByIds(any())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return instances.stream().filter(i -> ids.contains(i.getId())).toList();
        });
        doAnswer(inv -> {
            var entity = inv.getArgument(0, StoreInstance.class);
            entity.setId((long) (instances.size() + 1));
            instances.add(entity);
            return 1;
        }).when(storeInstanceMapper).insert(any(StoreInstance.class));
        doAnswer(inv -> {
            StoreInstance update = inv.getArgument(0, StoreInstance.class);
            instances.stream().filter(i -> i.getId().equals(update.getId())).forEach(i -> {
                if (update.getName() != null) {
                    i.setName(update.getName());
                }
                if (update.getDescription() != null) {
                    i.setDescription(update.getDescription());
                }
                if (update.getCategory() != null) {
                    i.setCategory(update.getCategory());
                }
                if (update.getType() != null) {
                    i.setType(update.getType());
                }
                if (update.getConfig() != null) {
                    i.setConfig(update.getConfig());
                }
                if (update.getStatus() != null) {
                    i.setStatus(update.getStatus());
                }
                if (update.getIsDefault() != null) {
                    i.setIsDefault(update.getIsDefault());
                }
            });
            return 1;
        }).when(storeInstanceMapper).updateById(any(StoreInstance.class));
        // 模拟 clearDefault 的按条件更新：解析 wrapper 参数（category / excludeId）后清除同分类其它默认。
        // getSqlSegment() 会触发 MPGENVAL 参数的生成与填充，与真实 SQL 执行路径一致。
        doAnswer(inv -> {
            var wrapper = inv.getArgument(1, Wrapper.class);
            AbstractWrapper<?, ?, ?> abstractWrapper = (AbstractWrapper<?, ?, ?>) wrapper;
            abstractWrapper.getSqlSegment();
            Map<String, Object> params = abstractWrapper.getParamNameValuePairs();
            Integer category = params.values().stream()
                    .filter(Integer.class::isInstance).map(Integer.class::cast).findFirst().orElse(null);
            Long excludeId = params.values().stream()
                    .filter(Long.class::isInstance).map(Long.class::cast).findFirst().orElse(null);
            instances.forEach(i -> {
                if (category != null && category.equals(i.getCategory())
                        && (excludeId == null || !excludeId.equals(i.getId()))) {
                    i.setIsDefault(false);
                }
            });
            return 1;
        }).when(storeInstanceMapper).update(any(StoreInstance.class), any(Wrapper.class));
        doAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            instances.removeIf(i -> ids.contains(i.getId()));
            return ids.size();
        }).when(storeInstanceMapper).deleteByIds(any());

        service = new StoreInstanceServiceImpl(storeInstanceMapper, connectionTester, vectorStoreResolver, knowledgeBaseMapper);
    }

    @Test
    void insertByBoAssignsIdAndDefaultStatus() {
        var bo = bo("pg", 1, 1, null, null, false);
        bo.setDescription("生产主库");

        var flag = service.insertByBo(bo);

        assertThat(flag).isTrue();
        assertThat(bo.getId()).isEqualTo(1L);
        assertThat(instances).hasSize(1);
        assertThat(instances.get(0).getStatus()).isEqualTo(1);
        assertThat(instances.get(0).getIsDefault()).isFalse();
        assertThat(instances.get(0).getDescription()).isEqualTo("生产主库");
    }

    @Test
    void insertByBoDefaultClearsOtherDefaultsInSameCategory() {
        seed(1L, "pg-old", 1, 1, true);
        var bo = bo("milvus-new", 1, 2, null, 1, true);

        service.insertByBo(bo);

        assertThat(instances).hasSize(2);
        assertThat(countDefault(1)).isEqualTo(1);
        assertThat(findById(instances, 2L).getIsDefault()).isTrue();
        verify(storeInstanceMapper).update(any(StoreInstance.class), any(Wrapper.class));
    }

    @Test
    void insertByBoRejectsMismatchedCategoryAndType() {
        var bo = bo("bad", 1, 4, null, 1, false);

        assertThatThrownBy(() -> service.insertByBo(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("类型与分类不匹配");
    }

    @Test
    void insertByBoAcceptsVectorCategoryWithElasticsearchType() {
        var bo = bo("es-vector", 1, 3, "{\"scheme\":\"https\"}", 1, false);

        var flag = service.insertByBo(bo);

        assertThat(flag).isTrue();
        assertThat(instances.get(0).getCategory()).isEqualTo(1);
        assertThat(instances.get(0).getType()).isEqualTo(3);
        assertThat(instances.get(0).getConfig()).isEqualTo("{\"scheme\":\"https\"}");
    }

    @Test
    void insertByBoRejectsInvalidCategory() {
        var bo = bo("bad-cat", 9, 1, null, 1, false);

        assertThatThrownBy(() -> service.insertByBo(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("分类不合法");
    }

    @Test
    void insertByBoRejectsInvalidConfigJson() {
        var bo = bo("bad-config", 1, 1, "not-json", 1, false);

        assertThatThrownBy(() -> service.insertByBo(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("连接参数必须为合法 JSON");
    }

    @Test
    void updateByBoAppliesChanges() {
        seed(1L, "pg", 1, 1, true);

        var bo = bo("pg-renamed", 1, 2, "{}", 0, false);
        bo.setId(1L);
        bo.setDescription("测试描述");

        service.updateByBo(bo);

        StoreInstance updated = findById(instances, 1L);
        assertThat(updated.getName()).isEqualTo("pg-renamed");
        assertThat(updated.getType()).isEqualTo(2);
        assertThat(updated.getStatus()).isZero();
        assertThat(updated.getIsDefault()).isFalse();
        assertThat(updated.getDescription()).isEqualTo("测试描述");
    }

    @Test
    void updateByBoUnknownIdThrows() {
        var bo = bo("x", 1, 1, null, 1, false);
        bo.setId(99L);

        assertThatThrownBy(() -> service.updateByBo(bo))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存储实例不存在");
    }

    @Test
    void updateByBoKeepsExistingSensitiveWhenBlank() {
        seed(1L, "pg", 1, 1, false);
        findById(instances, 1L).setConfig("{\"host\":\"h\",\"password\":\"old-pwd\",\"token\":\"old-tk\",\"indexPrefix\":\"x\"}");
        var bo = bo("pg", 1, 1, "{\"host\":\"h2\",\"password\":\"\"}", 1, false);
        bo.setId(1L);

        service.updateByBo(bo);

        StoreInstance updated = findById(instances, 1L);
        assertThat(updated.getConfig()).contains("old-pwd", "old-tk", "\"host\":\"h2\"");
    }

    @Test
    void updateByBoReplacesSensitiveWhenNotEmpty() {
        seed(1L, "pg", 1, 1, false);
        findById(instances, 1L).setConfig("{\"host\":\"h\",\"password\":\"old-pwd\"}");
        var bo = bo("pg", 1, 1, "{\"host\":\"h\",\"password\":\"new-pwd\"}", 1, false);
        bo.setId(1L);

        service.updateByBo(bo);

        StoreInstance updated = findById(instances, 1L);
        assertThat(updated.getConfig()).contains("new-pwd");
        assertThat(updated.getConfig()).doesNotContain("old-pwd");
    }

    @Test
    void updateByBoBlankConfigKeepsExistingConfig() {
        seed(1L, "pg", 1, 1, false);
        findById(instances, 1L).setConfig("{\"host\":\"h\",\"password\":\"old-pwd\"}");
        var bo = bo("pg", 1, 1, null, 1, false);
        bo.setId(1L);

        service.updateByBo(bo);

        assertThat(findById(instances, 1L).getConfig()).isEqualTo("{\"host\":\"h\",\"password\":\"old-pwd\"}");
    }

    @Test
    void updateDefaultSetsDefaultAndClearsOthersInSameCategory() {
        seed(1L, "pg-old", 1, 1, true);
        seed(2L, "milvus", 1, 2, false);

        var flag = service.updateDefault(2L, true);

        assertThat(flag).isTrue();
        assertThat(findById(instances, 2L).getIsDefault()).isTrue();
        assertThat(findById(instances, 1L).getIsDefault()).isFalse();
        assertThat(countDefault(1)).isEqualTo(1);
    }

    @Test
    void updateDefaultUnsetsDefaultWithoutClearingOthers() {
        seed(1L, "pg", 1, 1, false);
        seed(2L, "milvus", 1, 2, true);

        var flag = service.updateDefault(2L, false);

        assertThat(flag).isTrue();
        assertThat(findById(instances, 2L).getIsDefault()).isFalse();
        assertThat(findById(instances, 1L).getIsDefault()).isFalse();
        assertThat(countDefault(1)).isZero();
    }

    @Test
    void updateDefaultUnknownIdThrows() {
        assertThatThrownBy(() -> service.updateDefault(99L, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存储实例不存在");
    }

    @Test
    void queryByIdReturnsVo() {
        seed(1L, "pg", 1, 1, false);

        StoreInstanceVo vo = service.queryById(1L);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getName()).isEqualTo("pg");
        assertThat(vo.getCategory()).isEqualTo(1);
    }

    @Test
    void queryByIdUnknownThrows() {
        assertThatThrownBy(() -> service.queryById(99L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存储实例不存在");
    }

    @Test
    void queryPageListReturnsPageResult() {
        seed(1L, "pg", 1, 1, false);
        var vo = new StoreInstanceVo();
        vo.setId(1L);
        vo.setName("pg");
        var page = new Page<StoreInstanceVo>(1, 10, 1);
        page.setRecords(List.of(vo));
        when(storeInstanceMapper.selectVoPage(any(), any())).thenReturn(page);

        var result = service.queryPageList(new StoreInstanceBo(), new PageQuery(10, 1));

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void queryByIdMasksSensitiveConfig() {
        seed(1L, "pg", 1, 1, false);
        findById(instances, 1L).setConfig("{\"host\":\"http://localhost\",\"port\":5432,\"password\":\"secret\",\"token\":\"tk\",\"indexPrefix\":\"x\"}");

        StoreInstanceVo vo = service.queryById(1L);

        assertThat(vo.getConfig()).doesNotContain("secret", "tk", "password", "token");
        assertThat(vo.getConfig()).contains("http://localhost", "indexPrefix");
    }

    @Test
    void queryPageListMasksSensitiveConfig() {
        var vo = new StoreInstanceVo();
        vo.setId(1L);
        vo.setConfig("{\"host\":\"h\",\"password\":\"secret\",\"token\":\"tk\"}");
        var page = new Page<StoreInstanceVo>(1, 10, 1);
        page.setRecords(List.of(vo));
        when(storeInstanceMapper.selectVoPage(any(), any())).thenReturn(page);

        var result = service.queryPageList(new StoreInstanceBo(), new PageQuery(10, 1));

        assertThat(result.getRows()).hasSize(1);
        String masked = result.getRows().stream().findFirst().orElseThrow().getConfig();
        assertThat(masked).doesNotContain("secret", "tk");
        assertThat(masked).contains("\"host\":\"h\"");
    }

    @Test
    void deleteWithValidByIdsRemovesRows() {
        seed(1L, "pg", 1, 1, false);
        seed(2L, "es", 2, 3, false);

        var flag = service.deleteWithValidByIds(List.of(1L, 2L), true);

        assertThat(flag).isTrue();
        assertThat(instances).isEmpty();
    }

    @Test
    void deleteWithValidByIdsRejectsMissingRows() {
        seed(1L, "pg", 1, 1, false);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(1L, 99L), true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存储实例不存在或已被删除");
    }

    @Test
    void deleteWithValidByIdsRejectsEmptyIds() {
        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(), true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("主键不能为空");
    }

    @Test
    void deleteWithValidByIdsRejectsReferencedByKnowledgeBase() {
        seed(1L, "pg", 1, 1, false);
        when(knowledgeBaseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(1L), true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("正被知识库使用");
    }

    @Test
    void testConnectionMilvusTypeDelegatesToTester() {
        when(connectionTester.test(2, "{}")).thenReturn(StoreConnectionTestVo.success("连接成功"));

        var result = service.testConnection(new StoreConnectionTestBo(2, "{}", null));

        assertThat(result.ok()).isTrue();
        verify(connectionTester).test(2, "{}");
    }

    @Test
    void testConnectionElasticsearchTypeDelegatesToTester() {
        when(connectionTester.test(3, "{}")).thenReturn(StoreConnectionTestVo.success("连接成功"));

        var result = service.testConnection(new StoreConnectionTestBo(3, "{}", null));

        assertThat(result.ok()).isTrue();
        verify(connectionTester).test(3, "{}");
    }

    @Test
    void testConnectionInvalidTypeReturnsFailure() {
        var result = service.testConnection(new StoreConnectionTestBo(9, "{}", null));

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("类型不合法");
    }

    @Test
    void testConnectionNewInstancePassesConfigAsIs() {
        when(connectionTester.test(1, "{\"host\":\"h\"}")).thenReturn(StoreConnectionTestVo.success("连接成功"));

        var result = service.testConnection(new StoreConnectionTestBo(1, "{\"host\":\"h\"}", null));

        assertThat(result.ok()).isTrue();
        verify(connectionTester).test(1, "{\"host\":\"h\"}");
    }

    @Test
    void testConnectionEditMergesBlankSensitiveFromSaved() {
        seed(1L, "pg", 1, 1, false);
        findById(instances, 1L).setConfig("{\"host\":\"h\",\"password\":\"old-pwd\",\"token\":\"old-tk\"}");
        when(connectionTester.test(eq(1), any())).thenReturn(StoreConnectionTestVo.success("连接成功"));

        service.testConnection(new StoreConnectionTestBo(1, "{\"host\":\"h2\",\"password\":\"\"}", 1L));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connectionTester).test(eq(1), captor.capture());
        assertThat(captor.getValue()).contains("old-pwd", "old-tk", "\"host\":\"h2\"");
    }

    private static StoreInstanceBo bo(String name, int category, int type, String config, Integer status, boolean isDefault) {
        var bo = new StoreInstanceBo();
        bo.setName(name);
        bo.setCategory(category);
        bo.setType(type);
        bo.setConfig(config);
        bo.setStatus(status);
        bo.setIsDefault(isDefault);
        return bo;
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

    private void seed(Long id, String name, int category, int type, boolean isDefault) {
        var entity = new StoreInstance();
        entity.setId(id);
        entity.setName(name);
        entity.setCategory(category);
        entity.setType(type);
        entity.setStatus(1);
        entity.setIsDefault(isDefault);
        instances.add(entity);
    }

    private long countDefault(int category) {
        return instances.stream().filter(i -> category == i.getCategory() && Boolean.TRUE.equals(i.getIsDefault())).count();
    }

    private static StoreInstance findById(List<StoreInstance> list, Long id) {
        return list.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
    }
}
