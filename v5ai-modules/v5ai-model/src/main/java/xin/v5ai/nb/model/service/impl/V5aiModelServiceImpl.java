package xin.v5ai.nb.model.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.enums.ModelType;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.model.api.ModelCatalogPort;
import xin.v5ai.nb.model.core.ModelConnectionTester;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.domain.bo.ModelBo;
import xin.v5ai.nb.model.domain.vo.TestModelConnectionVo;
import xin.v5ai.nb.model.domain.vo.V5aiModelVo;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.model.mapper.V5aiModelProviderMapper;
import xin.v5ai.nb.model.service.IV5aiModelService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 模型配置服务实现类
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class V5aiModelServiceImpl implements IV5aiModelService, Predicate<Long>, ModelCatalogPort {

    /**
     * 模型作用域: 全局
     */
    private static final String SCOPE_GLOBAL = "GLOBAL";

    /**
     * 模型作用域: 个人
     */
    private static final String SCOPE_PERSONAL = "PERSONAL";

    private final V5aiModelMapper mapper;
    private final V5aiModelProviderMapper providerMapper;
    private final CredentialCipher credentialCipher;

    private final ModelConnectionTester connectionTester;

    @Override
    public PageResult<V5aiModelVo> queryPageList(ModelBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<V5aiModel> lqw = buildQueryWrapper(bo);
        Page<V5aiModelVo> result = mapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public List<V5aiModelVo> queryList(ModelBo bo) {
        LambdaQueryWrapper<V5aiModel> lqw = buildQueryWrapper(bo);
        return mapper.selectVoList(lqw);
    }

    @Override
    public List<OptionDTO> queryOptionList(ModelBo bo) {
        List<V5aiModelVo> models = mapper.selectVoList(QueryBuilder.lambda(V5aiModel.class)
                .eq(V5aiModel::getEnabled, true)
                .eq(bo.getModelType() != null, V5aiModel::getModelType, bo.getModelType() == null ? null : bo.getModelType().name())
                .orderByDesc(V5aiModel::getIsDefault)
                .orderByAsc(V5aiModel::getId)
                .build());
        return models.stream()
                .map(model -> new OptionDTO(model.getId(),
                        model.getId() + "/" + (StrUtil.isBlank(model.getModelName()) ? model.getModelKey() : model.getModelName())
                                + " (" + model.getModelType() + ")",
                        model.getIsDefault()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ModelBo bo) {
        var provider = providerMapper.selectById(bo.getProviderId());
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            throw new ServiceException("供应商不存在或者已停用");
        }
        V5aiModel add = MapstructUtils.convert(bo, V5aiModel.class);
        assert add != null;
        add.setId(null);
        add.setProviderId(provider.getId());
        add.setModelName(StrUtil.isBlank(bo.getModelName()) ? bo.getModelKey() : bo.getModelName().trim());
        add.setScope(normalizeScope(bo.getScope()));
        add.setConfig(validateConfig(bo.getConfig()));
        add.setCredentialsCiphertext(credentialCipher.encrypt(bo.getCredentials()));
        // 启用缺省为 true（与列默认一致），保证下方默认校验拿到的状态真实
        add.setEnabled(bo.getEnabled() == null || bo.getEnabled());
        // 需求1：创建即设默认要求已启用（同类型唯一）
        boolean requestedDefault = Boolean.TRUE.equals(bo.getIsDefault());
        if (requestedDefault && !Boolean.TRUE.equals(add.getEnabled())) {
            throw new V5aiException(ErrorCode.CONFLICT, "模型未启用，不能设为默认");
        }
        boolean flag = mapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
            if (requestedDefault) {
                mapper.clearModelTypeDefaults(add.getModelType(), add.getId());
            }
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ModelBo bo) {
        var existing = exist(bo.getId());
        V5aiModel update = MapstructUtils.convert(bo, V5aiModel.class);
        assert update != null;
        update.setId(bo.getId());
        update.setProviderId(bo.getProviderId());
        if (bo.getModelType() != null) {
            update.setModelType(bo.getModelType().name());
        }
        // 名称留空不修改；作用域留空不修改；config 留空不修改，非空则校验 JSON
        update.setModelName(StrUtil.isBlank(bo.getModelName()) ? null : bo.getModelName().trim());
        update.setScope(StrUtil.isBlank(bo.getScope()) ? null : normalizeScope(bo.getScope()));
        update.setConfig(validateConfig(bo.getConfig()));
        if (StrUtil.isNotBlank(bo.getCredentials())) {
            update.setCredentialsCiphertext(credentialCipher.encrypt(bo.getCredentials()));
        }

        // —— 启用/默认不变式（与专用端点同一套守卫，杜绝 PUT 旁路）——
        boolean existingEnabled = Boolean.TRUE.equals(existing.getEnabled());
        boolean existingDefault = Boolean.TRUE.equals(existing.getIsDefault());
        boolean newEnabled = bo.getEnabled() == null ? existingEnabled : bo.getEnabled();
        boolean finalDefault = bo.getIsDefault() == null ? existingDefault : bo.getIsDefault();
        // 需求2：启用→停用需通过引用校验；停用默认模型自动清除默认标记
        if (existingEnabled && !newEnabled) {
            verifyNotReferenced(bo.getId(), "禁用");
            if (existingDefault && bo.getIsDefault() == null) {
                update.setIsDefault(false);
                finalDefault = false;
            }
        }
        // 需求1 + 约定：最终为默认必须已启用
        if (finalDefault && !newEnabled) {
            throw new V5aiException(ErrorCode.CONFLICT, "模型未启用，不能设为默认");
        }
        if (bo.getEnabled() != null) {
            update.setEnabled(newEnabled);
        }
        boolean flag = mapper.updateById(update) > 0;
        if (flag && finalDefault) {
            // 同类型唯一：清掉其它同类型模型的默认标记
            mapper.clearModelTypeDefaults(existing.getModelType(), bo.getId());
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidById(Long id, Boolean isValid) {
        exist(id);
        verifyNotReferenced(id, "删除");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateEnabled(Long id, Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        V5aiModel existing = exist(id);
        boolean current = Boolean.TRUE.equals(existing.getEnabled());
        if (current == enabled) {
            return true; // 幂等
        }
        V5aiModel update = new V5aiModel();
        update.setId(id);
        update.setEnabled(enabled);
        if (!enabled) {
            // 需求2：禁用前引用校验
            verifyNotReferenced(id, "禁用");
            // 停用默认模型：自动清除其默认标记（同一次写）
            if (Boolean.TRUE.equals(existing.getIsDefault())) {
                update.setIsDefault(false);
            }
        }
        return mapper.updateById(update) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateDefault(Long id, Boolean isDefault) {
        V5aiModel existing = exist(id);
        boolean target = Boolean.TRUE.equals(isDefault); // null → 取消默认（与 StoreDefaultBo 语义一致）
        boolean current = Boolean.TRUE.equals(existing.getIsDefault());
        if (current == target) {
            return true; // 幂等
        }
        if (target && !Boolean.TRUE.equals(existing.getEnabled())) {
            throw new V5aiException(ErrorCode.CONFLICT, "模型未启用，不能设为默认");
        }
        V5aiModel update = new V5aiModel();
        update.setId(id);
        update.setIsDefault(target);
        if (target) {
            // 需求1：同类型至多一个默认 → 先清同类型其它默认
            mapper.clearModelTypeDefaults(existing.getModelType(), id);
        }
        return mapper.updateById(update) > 0;
    }

    @Override
    public TestModelConnectionVo testConnection(Long modelId) {
        if (connectionTester == null) {
            return TestModelConnectionVo.failed(modelId, "model connection testing is not configured");
        }
        return connectionTester.test(modelId);
    }

    /**
     * 构造模型列表查询条件。
     *
     * @param bo 模型筛选条件
     * @return 包含 modelKey、type、enabled、providerKey 等条件的查询包装器
     */
    private LambdaQueryWrapper<V5aiModel> buildQueryWrapper(ModelBo bo) {
//        Long providerId = StrUtil.isNotBlank(bo.getProviderKey()) ? resolveProviderId(bo.getProviderKey()) : null;
        return QueryBuilder.lambda(V5aiModel.class)
                .likeIfText(V5aiModel::getModelKey, bo.getModelKey())
                .likeIfText(V5aiModel::getModelName, bo.getModelName())
                .and(StrUtil.isNotBlank(bo.getKeyword()),
                        w -> w.like(V5aiModel::getModelName, bo.getKeyword())
                                .or()
                                .like(V5aiModel::getModelKey, bo.getKeyword()))
                .eqIfText(V5aiModel::getScope, bo.getScope())
                .eq(bo.getModelType() != null, V5aiModel::getModelType, bo.getModelType() == null ? null : bo.getModelType().name())
                .eq(bo.getEnabled() != null, V5aiModel::getEnabled, bo.getEnabled())
                .eq(bo.getProviderId() != null, V5aiModel::getProviderId, bo.getProviderId())
                .orderByAsc(V5aiModel::getId)
                .build();
    }

    /**
     * 校验并归一化模型作用域（GLOBAL / PERSONAL，缺省 GLOBAL）。
     */
    private static String normalizeScope(String scope) {
        if (StrUtil.isBlank(scope)) {
            return SCOPE_GLOBAL;
        }
        var normalized = scope.trim().toUpperCase();
        if (!SCOPE_GLOBAL.equals(normalized) && !SCOPE_PERSONAL.equals(normalized)) {
            throw new IllegalArgumentException("model scope must be GLOBAL or PERSONAL");
        }
        return normalized;
    }

    /**
     * 校验模型 config 必须是合法 JSON；空白输入返回 {@code null}（不更新）。
     */
    private static String validateConfig(String config) {
        if (StrUtil.isBlank(config)) {
            return null;
        }
        try {
            ModelExtConfigAttrs.fromJson(config);
        } catch (Exception exception) {
            throw new IllegalArgumentException("model config must be a valid JSON object");
        }
        return config;
    }

    /**
     * 根据供应商标识解析 providerId，不存在时返回 {@code null}。
     */
//    private Long resolveProviderId(String providerKey) {
//        var provider = providerMapper.selectOne(new LambdaQueryWrapper<V5aiModelProvider>()
//                .eq(V5aiModelProvider::getProviderKey, providerKey));
//        return provider == null ? null : provider.getId();
//    }

    /**
     * 引用校验：模型被 AgentDTO（任意状态）或知识库（embedding/rerank 任一引用）使用时抛 409，
     * 并给出按引用来源计数的明细。
     *
     * @param id      模型 ID
     * @param opName  操作名（禁用 / 删除），用于错误文案
     */
    private void verifyNotReferenced(Long id, String opName) {
        Long agents = mapper.countAgentUsage(id);
        Long kbs = mapper.countKnowledgeBaseUsage(id);
        long agentCount = agents == null ? 0 : agents;
        long kbCount = kbs == null ? 0 : kbs;
        if (agentCount > 0 || kbCount > 0) {
            throw new V5aiException(ErrorCode.CONFLICT,
                    "模型被 " + agentCount + " 个 AgentDTO、" + kbCount + " 个知识库使用，无法" + opName);
        }
    }

    private V5aiModel exist(Long id) {
        var existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模型不存在，ID: " + id);
        }
        return existing;
    }

    @Override
    public boolean test(Long modelId) {
        if (modelId == null) {
            return false;
        }
        V5aiModel entity = mapper.selectById(modelId);
        return entity != null && Boolean.TRUE.equals(entity.getEnabled());
    }

    /**
     * 跨模块端口：该模型是否存在、为 CHAT 类型且已启用。
     *
     * <p>与 {@link #test(Long)} 的差别是多了类型这一维。类型不对的运行期异常会被
     * 静默吞掉（见 {@link ModelCatalogPort}），所以保存配置时必须拦下。</p>
     */
    @Override
    public boolean isEnabledChatModel(Long modelId) {
        if (modelId == null) {
            return false;
        }
        V5aiModel entity = mapper.selectById(modelId);
        return entity != null
                && Boolean.TRUE.equals(entity.getEnabled())
                && ModelType.CHAT.name().equals(entity.getModelType());
    }

    /**
     * 跨模块端口：批量返回模型展示名称；未填写名称时回退模型 key。
     */
    @Override
    public Map<Long, String> findModelNamesByIds(Collection<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<V5aiModel>()
                        .select(V5aiModel::getId, V5aiModel::getModelName, V5aiModel::getModelKey)
                        .in(V5aiModel::getId, modelIds))
                .stream()
                .collect(Collectors.toMap(V5aiModel::getId,
                        model -> StrUtil.isBlank(model.getModelName())
                                ? model.getModelKey() : model.getModelName()));
    }
}
