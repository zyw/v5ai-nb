package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.V5aiApiKeyBo;
import xin.v5ai.nb.platform.domain.vo.ApiKeyRespVo;
import xin.v5ai.nb.platform.domain.vo.V5aiApiKeyVo;

import java.util.List;

public interface IV5aiApiKeyService {

    /**
     * 分页查询角色列表
     *
     * @param bo      查询条件
     * @param pageQuery 分页参数
     * @return 角色分页列表
     */
    PageResult<V5aiApiKeyVo> selectPageList(V5aiApiKeyBo bo, PageQuery pageQuery);

    /**
     * 新增保存Agent Key
     *
     * @param agentKey Agent Key
     * @return 影响行数
     */
    ApiKeyRespVo insertApiKey(String agentKey);

    String findSecretHash(String agentKey);

    int upsertSecret(String agentKey, String secretHash);

    List<V5aiApiKeyVo> list();

    int delete(String agentKey);

//    CreateApiKeyResult createApiKey(String agentKey);


//    List<V5aiApiKeyVo> listApiKeys();

//    void deleteApiKey(String agentKey);

//    record ApiKeyRecord(String agentKey, boolean enabled) {
//    }
//
//    record CreateApiKeyResult(String agentKey, String apiKey) {
//    }
}
