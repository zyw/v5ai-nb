package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.PlmClient;

import java.io.Serial;
import java.io.Serializable;


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
    private String grantType;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * token活跃超时时间
     */
    private Long activeTimeout;

    /**
     * token固定超时
     */
    private Long timeout;

    /**
     * 状态（0正常 1停用）
     */
    private String status;


}
