package xin.v5ai.nb.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.platform.domain.V5aiApiKey;
import xin.v5ai.nb.platform.domain.bo.V5aiApiKeyBo;
import xin.v5ai.nb.platform.domain.vo.V5aiApiKeyVo;
import xin.v5ai.nb.platform.mapper.V5aiApiKeyMapper;
import xin.v5ai.nb.platform.service.IV5aiApiKeyService;

import java.security.SecureRandom;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class V5aiApiKeyServiceImpl implements IV5aiApiKeyService {

    private static final String KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final V5aiApiKeyMapper baseMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public String findSecretHash(String agentKey) {
        var entity = baseMapper.selectByAgentKey(agentKey);
        return entity == null || !Boolean.TRUE.equals(entity.getEnabled()) ? null : entity.getSecretHash();
    }

    @Override
    public int upsertSecret(String agentKey, String secretHash) {
        var entity = new V5aiApiKey();
        entity.setAgentKey(agentKey);
        entity.setSecretHash(secretHash);
        entity.setEnabled(true);
        return baseMapper.upsert(entity);
    }

    @Override
    public List<V5aiApiKeyVo> list() {
        return baseMapper.selectVoList();
    }

    @Override
    public int delete(String agentKey) {
        return baseMapper.deleteByAgentKey(agentKey);
    }

//    @Override
//    public CreateApiKeyResult createApiKey(String agentKey) {
//        var rawKey = generateApiKey();
//        upsertSecret(agentKey, passwordEncoder.encode(rawKey));
//        return new CreateApiKeyResult(agentKey, rawKey);
//    }

    @Override
    public PageResult<V5aiApiKeyVo> selectPageList(V5aiApiKeyBo bo, PageQuery pageQuery) {
        Page<V5aiApiKeyVo> page = baseMapper.selectVoPage(pageQuery.build(), this.buildQueryWrapper(bo));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public int insertApiKey(String agentKey) {
        var rawKey = generateApiKey();
        return upsertSecret(agentKey, passwordEncoder.encode(rawKey));
    }

//    @Override
//    public void deleteApiKey(String agentKey) {
//        delete(agentKey);
//    }

    private String generateApiKey() {
        var sb = new StringBuilder("v5ai_");
        for (int i = 0; i < 32; i++) {
            sb.append(KEY_ALPHABET.charAt(RANDOM.nextInt(KEY_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * 构造角色列表查询条件。
     *
     * @param bo 角色筛选条件
     * @return 包含名称、权限字符、状态和创建时间区间的查询包装器
     */
    private Wrapper<V5aiApiKey> buildQueryWrapper(V5aiApiKeyBo bo) {
        return QueryBuilder.lambda(V5aiApiKey.class)
                .eqIfPresent(V5aiApiKey::getId, bo.getId())
                .likeIfText(V5aiApiKey::getAgentKey, bo.getAgentKey())
                .eq(V5aiApiKey::getEnabled, bo.getEnabled())
                .orderByAsc(V5aiApiKey::getCreatedAt)
                .build();
    }
}
