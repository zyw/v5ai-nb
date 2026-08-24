package xin.v5ai.nb.model.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.domain.OptionDTO;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.encrypt.core.CredentialCipher;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.domain.bo.ModelBo;
import xin.v5ai.nb.model.domain.vo.TestModelConnectionVo;
import xin.v5ai.nb.model.domain.vo.V5aiModelVo;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.model.mapper.V5aiModelProviderMapper;
import xin.v5ai.nb.model.core.ModelConnectionTester;
import xin.v5ai.nb.model.service.IV5aiModelService;

import java.util.List;

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
public class V5aiModelServiceImpl implements IV5aiModelService {

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
                .eq(bo.getType() != null, V5aiModel::getModelType, bo.getType() == null ? null : bo.getType().name())
                .orderByAsc(V5aiModel::getId)
                .build());
        return models.stream()
                .map(model -> new OptionDTO(model.getId(),
                        model.getProviderId() + "/" + model.getModelKey() + " (" + model.getModelType() + ")"))
                .toList();
    }

    @Override
    public Boolean insertByBo(ModelBo bo) {
        var provider = providerMapper.selectOne(new LambdaQueryWrapper<V5aiModelProvider>()
                .eq(V5aiModelProvider::getProviderKey, bo.getProviderKey()));
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            throw new IllegalArgumentException("provider is not enabled");
        }
        V5aiModel add = MapstructUtils.convert(bo, V5aiModel.class);
        assert add != null;
        add.setId(null);
        add.setProviderId(provider.getId());
        add.setModelType(bo.getType().name());
        add.setEnabled(Boolean.TRUE.equals(bo.getEnabled()));
        add.setCredentialsCiphertext(credentialCipher.encrypt(bo.getCredentials()));
        boolean flag = mapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(ModelBo bo) {
        var existing = exist(bo.getId());
        V5aiModel update = MapstructUtils.convert(bo, V5aiModel.class);
        assert update != null;
        update.setId(bo.getId());
        update.setProviderId(existing.getProviderId());
        if (bo.getType() != null) {
            update.setModelType(bo.getType().name());
        }
        if (StrUtil.isNotBlank(bo.getCredentials())) {
            update.setCredentialsCiphertext(credentialCipher.encrypt(bo.getCredentials()));
        }
        return mapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidById(Long id, Boolean isValid) {
        exist(id);
        boolean referenced = mapper.checkAgentReferenced(id);
        if (referenced) {
            throw new V5aiException(ErrorCode.CONFLICT,
                    "model is used by an application and cannot be deleted");
        }
        return mapper.deleteById(id) > 0;
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
        Long providerId = StrUtil.isNotBlank(bo.getProviderKey()) ? resolveProviderId(bo.getProviderKey()) : null;
        return QueryBuilder.lambda(V5aiModel.class)
                .likeIfText(V5aiModel::getModelKey, bo.getModelKey())
                .eq(bo.getType() != null, V5aiModel::getModelType, bo.getType() == null ? null : bo.getType().name())
                .eq(bo.getEnabled() != null, V5aiModel::getEnabled, bo.getEnabled())
                .eq(providerId != null, V5aiModel::getProviderId, providerId)
                .orderByAsc(V5aiModel::getId)
                .build();
    }

    /**
     * 根据供应商标识解析 providerId，不存在时返回 {@code null}。
     */
    private Long resolveProviderId(String providerKey) {
        var provider = providerMapper.selectOne(new LambdaQueryWrapper<V5aiModelProvider>()
                .eq(V5aiModelProvider::getProviderKey, providerKey));
        return provider == null ? null : provider.getId();
    }

    private V5aiModel exist(Long id) {
        var existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("model does not exist: " + id);
        }
        return existing;
    }
}
