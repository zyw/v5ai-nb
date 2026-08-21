package xin.v5ai.nb.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.encrypt.core.CredentialCipher;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.domain.bo.ModelBo;
import xin.v5ai.nb.model.domain.vo.V5aiModelVo;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.model.mapper.V5aiModelProviderMapper;
import xin.v5ai.nb.model.service.IV5aiModelService;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 *  服务实现类
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

    @Override
    public PageResult<V5aiModelVo> queryPageList(ModelBo bo, PageQuery pageQuery) {
        return null;
    }

    @Override
    public List<V5aiModelVo> queryList(ModelBo bo) {
        return List.of();
    }

    @Override
    public Boolean insertByBo(ModelBo bo) {
        var provider = providerMapper.selectOne(new LambdaQueryWrapper<V5aiModelProvider>()
                .eq(V5aiModelProvider::getProviderKey, bo.getProviderKey()));
        if (provider == null || !provider.getEnabled()) {
            throw new IllegalArgumentException("provider is not enabled");
        }
        V5aiModel add = MapstructUtils.convert(bo, V5aiModel.class);
        assert add != null;
        add.setCredentialsCiphertext(credentialCipher.encrypt(bo.getCredentials()));
        boolean flag = mapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(ModelBo bo) {
        return null;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return null;
    }
}
