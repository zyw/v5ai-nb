package xin.v5ai.nb.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.rag.core.StoreConnectionTester;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.domain.bo.StoreConnectionTestBo;
import xin.v5ai.nb.rag.domain.bo.StoreInstanceBo;
import xin.v5ai.nb.rag.domain.vo.StoreConnectionTestVo;
import xin.v5ai.nb.rag.domain.vo.StoreInstanceVo;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;
import xin.v5ai.nb.rag.service.IStoreInstanceService;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 存储实例服务实现类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreInstanceServiceImpl implements IStoreInstanceService {

    /**
     * 启用状态
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 分类: 向量库
     */
    private static final int CATEGORY_VECTOR = 1;

    /**
     * 分类: 搜索引擎
     */
    private static final int CATEGORY_SEARCH = 2;

    /**
     * 类型: PG_VECTOR（向量库）
     */
    private static final int TYPE_PG_VECTOR = 1;

    /**
     * 类型: MILVUS（向量库）
     */
    private static final int TYPE_MILVUS = 2;

    /**
     * 类型: ELASTICSEARCH（搜索引擎）
     */
    private static final int TYPE_ELASTICSEARCH = 3;

    /**
     * 类型: DB_FULLTEXT（搜索引擎）——业务库原生 BM25，不连外部服务；
     * 存储实例表沿用历史编号 4（历史名 PG_FULLTEXT），MySQL 业务库同样适用（见 docs/adr/0012）。
     */
    private static final int TYPE_DB_FULLTEXT = 4;

    /**
     * config JSON 中的敏感字段：查询时脱敏移除，编辑提交为空时保留库中原值。
     */
    private static final Set<String> SENSITIVE_CONFIG_KEYS = Set.of("password", "token");

    private final StoreInstanceMapper storeInstanceMapper;

    private final StoreConnectionTester connectionTester;

    private final VectorStoreResolver vectorStoreResolver;

    private final xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public PageResult<StoreInstanceVo> queryPageList(StoreInstanceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<StoreInstance> lqw = buildQueryWrapper(bo);
        Page<StoreInstanceVo> result = storeInstanceMapper.selectVoPage(pageQuery.build(), lqw);
        result.getRecords().forEach(vo -> vo.setConfig(maskSensitiveConfig(vo.getConfig())));
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public List<StoreInstanceVo> queryList(StoreInstanceBo bo) {
        LambdaQueryWrapper<StoreInstance> lqw = buildQueryWrapper(bo);
        List<StoreInstanceVo> list = storeInstanceMapper.selectVoList(lqw);
        list.forEach(vo -> vo.setConfig(maskSensitiveConfig(vo.getConfig())));
        return list;
    }

    @Override
    public StoreInstanceVo queryById(Long id) {
        StoreInstance entity = storeInstanceMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException("存储实例不存在: " + id);
        }
        StoreInstanceVo vo = MapstructUtils.convert(entity, StoreInstanceVo.class);
        assert vo != null;
        vo.setConfig(maskSensitiveConfig(vo.getConfig()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(StoreInstanceBo bo) {
        validEntityBeforeSave(bo);
        StoreInstance add = MapstructUtils.convert(bo, StoreInstance.class);
        assert add != null;
        add.setId(null);
        add.setStatus(bo.getStatus() == null ? STATUS_ENABLED : bo.getStatus());
        add.setIsDefault(Boolean.TRUE.equals(bo.getIsDefault()));
        boolean flag = storeInstanceMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
            if (add.getIsDefault()) {
                clearDefault(add.getCategory(), add.getId());
            }
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(StoreInstanceBo bo) {
        StoreInstance existing = exist(bo.getId());
        validEntityBeforeSave(bo);
        StoreInstance update = MapstructUtils.convert(bo, StoreInstance.class);
        assert update != null;
        update.setId(bo.getId());
        // 敏感字段（password/token 等）提交为空时保留库中原值，非空时替换
        String mergedConfig = mergeSensitiveConfig(bo.getConfig(), existing.getConfig());
        update.setConfig(StrUtil.isBlank(mergedConfig) ? null : mergedConfig);
        boolean flag = storeInstanceMapper.updateById(update) > 0;
        if (flag) {
            vectorStoreResolver.invalidate(bo.getId());
            if (Boolean.TRUE.equals(update.getIsDefault())) {
                clearDefault(update.getCategory(), update.getId());
            }
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateDefault(Long id, Boolean isDefault) {
        StoreInstance existing = exist(id);
        StoreInstance update = new StoreInstance();
        update.setId(id);
        update.setIsDefault(Boolean.TRUE.equals(isDefault));
        boolean flag = storeInstanceMapper.updateById(update) > 0;
        if (flag && Boolean.TRUE.equals(update.getIsDefault())) {
            clearDefault(existing.getCategory(), id);
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("主键不能为空");
        }
        if (storeInstanceMapper.selectByIds(ids).size() != ids.size()) {
            throw new ServiceException("存储实例不存在或已被删除");
        }
        // 被知识库引用（向量库/搜索引擎绑定）的实例禁止删除，避免悬空引用
        Long referenced = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<xin.v5ai.nb.rag.domain.KnowledgeBase>()
                .and(wrapper -> wrapper
                        .in(xin.v5ai.nb.rag.domain.KnowledgeBase::getVectorStoreInstanceId, ids)
                        .or()
                        .in(xin.v5ai.nb.rag.domain.KnowledgeBase::getSearchEngineInstanceId, ids)));
        if (referenced != null && referenced > 0) {
            throw new ServiceException("该存储实例正被知识库使用，请先解除绑定后再删除");
        }
        boolean deleted = storeInstanceMapper.deleteByIds(ids) > 0;
        if (deleted) {
            ids.forEach(vectorStoreResolver::invalidate);
        }
        return deleted;
    }

    @Override
    public StoreConnectionTestVo testConnection(StoreConnectionTestBo bo) {
        Integer type = bo.type();
        if (type == null || type < TYPE_PG_VECTOR || type > TYPE_DB_FULLTEXT) {
            return StoreConnectionTestVo.failure("类型不合法: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-DB_FULLTEXT（业务库原生 BM25）");
        }
        // 编辑态合并库中脱敏的敏感字段（password/token 留空时沿用原值），新建态直接用表单值
        String effectiveConfig = bo.config();
        if (bo.id() != null) {
            StoreInstance existing = storeInstanceMapper.selectById(bo.id());
            if (existing != null) {
                effectiveConfig = mergeSensitiveConfig(bo.config(), existing.getConfig());
            }
        }
        return connectionTester.test(type, effectiveConfig);
    }

    /**
     * 查询脱敏：移除 config JSON 中的敏感字段（password/token 等）。
     *
     * @param configJson 存储的 config JSON
     * @return 脱敏后的 JSON 字符串；无敏感字段时原样返回
     */
    private String maskSensitiveConfig(String configJson) {
        if (StrUtil.isBlank(configJson)) {
            return configJson;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(configJson);
            if (SENSITIVE_CONFIG_KEYS.stream().noneMatch(obj::containsKey)) {
                return configJson;
            }
            SENSITIVE_CONFIG_KEYS.forEach(obj::remove);
            return obj.toString();
        } catch (Exception e) {
            log.warn("存储实例 config 脱敏失败，原样返回: {}", e.getMessage());
            return configJson;
        }
    }

    /**
     * 编辑时合并敏感字段：提交 JSON 中敏感字段为空（缺失/空串）时沿用库中原值，非空时替换。
     *
     * @param submittedJson 提交的 config JSON
     * @param existingJson  库中已有 config JSON
     * @return 合并后的 JSON 字符串；任一为空时按提交值处理
     */
    private String mergeSensitiveConfig(String submittedJson, String existingJson) {
        if (StrUtil.isBlank(submittedJson) || StrUtil.isBlank(existingJson)) {
            return submittedJson;
        }
        try {
            JSONObject submitted = JSONUtil.parseObj(submittedJson);
            JSONObject existing = JSONUtil.parseObj(existingJson);
            for (String key : SENSITIVE_CONFIG_KEYS) {
                Object submittedValue = submitted.get(key);
                boolean submittedBlank = submittedValue == null
                        || (submittedValue instanceof String str && StrUtil.isBlank(str));
                if (submittedBlank && existing.get(key) != null) {
                    submitted.set(key, existing.get(key));
                }
            }
            return submitted.toString();
        } catch (Exception e) {
            log.warn("存储实例 config 敏感字段合并失败，按提交值保存: {}", e.getMessage());
            return submittedJson;
        }
    }

    /**
     * 保存前校验：分类/类型取值及其组合合法性，config 必须为合法 JSON。
     */
    private void validEntityBeforeSave(StoreInstanceBo bo) {
        Integer category = bo.getCategory();
        Integer type = bo.getType();
        if (category == null
                || (category != CATEGORY_VECTOR && category != CATEGORY_SEARCH)) {
            throw new ServiceException("分类不合法: 1-向量库 2-搜索引擎");
        }
        if (type == null || (type < TYPE_PG_VECTOR || type > TYPE_DB_FULLTEXT)) {
            throw new ServiceException("类型不合法: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-DB_FULLTEXT（业务库原生 BM25）");
        }
        boolean categoryMatch = category == CATEGORY_VECTOR
                ? (type == TYPE_PG_VECTOR || type == TYPE_MILVUS || type == TYPE_ELASTICSEARCH)
                : (type == TYPE_ELASTICSEARCH || type == TYPE_DB_FULLTEXT);
        if (!categoryMatch) {
            throw new ServiceException("类型与分类不匹配: 向量库支持 PG_VECTOR/MILVUS/ELASTICSEARCH，搜索引擎仅支持 ELASTICSEARCH/DB_FULLTEXT");
        }
        if (StrUtil.isNotBlank(bo.getConfig()) && !JSONUtil.isTypeJSON(bo.getConfig())) {
            throw new ServiceException("连接参数必须为合法 JSON");
        }
    }

    /**
     * 构造存储实例列表查询条件。
     */
    private LambdaQueryWrapper<StoreInstance> buildQueryWrapper(StoreInstanceBo bo) {
        return QueryBuilder.lambda(StoreInstance.class)
                .likeIfText(StoreInstance::getName, bo == null ? null : bo.getName())
                .eqIfPresent(StoreInstance::getCategory, bo == null ? null : bo.getCategory())
                .eqIfPresent(StoreInstance::getType, bo == null ? null : bo.getType())
                .eqIfPresent(StoreInstance::getStatus, bo == null ? null : bo.getStatus())
                .eqIfPresent(StoreInstance::getIsDefault, bo == null ? null : bo.getIsDefault())
                .orderByAsc(StoreInstance::getId)
                .build();
    }

    /**
     * 取消同分类下其它默认实例，保证每个分类至多一个默认实例。
     */
    private void clearDefault(Integer category, Long excludeId) {
        StoreInstance reset = new StoreInstance();
        reset.setIsDefault(false);
        storeInstanceMapper.update(reset, new LambdaUpdateWrapper<StoreInstance>()
                .eq(StoreInstance::getCategory, category)
                .ne(StoreInstance::getId, excludeId)
                .eq(StoreInstance::getIsDefault, true));
    }

    private StoreInstance exist(Long id) {
        StoreInstance existing = storeInstanceMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("存储实例不存在: " + id);
        }
        return existing;
    }
}
