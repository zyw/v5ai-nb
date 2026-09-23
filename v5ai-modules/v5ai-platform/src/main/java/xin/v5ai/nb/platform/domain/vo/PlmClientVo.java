package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.PlmClient;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;


/**
 * 系统授权视图对象 sys_client
 *
 * @author zyw
 * @date 2026-08-26
 */
@Data
@AutoMapper(target = PlmClient.class)
public class PlmClientVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 客户端id
     */
    private String clientId;

    /**
     * 客户端key
     */
    private String clientKey;

    /**
     * 客户端秘钥
     */
    private String clientSecret;

    /**
     * 授权类型
     */
    private List<String> grantTypeList;

    /**
     * 授权类型
     */
    private String grantType;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 允许访问路径
     */
    private String accessPath;

    /**
     * 允许访问路径列表
     */
    private List<String> accessPathList;

    /**
     * IP白名单
     */
    private String ipWhitelist;

    /**
     * IP白名单列表
     */
    private List<String> ipWhitelistList;

    /**
     * token活跃超时时间
     */
    private Long activeTimeout;

    /**
     * token固定超时时间
     */
    private Long timeout;

    /**
     * 状态（0正常 1停用）
     */
    private String status;


}
