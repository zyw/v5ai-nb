package xin.v5ai.nb.common.core.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class VectorConfigDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否启用该 Elasticsearch 配置 */
    private boolean enabled = true;

    /** 主机地址，支持直接填写带 scheme 的完整 endpoint */
    private String host = "localhost";

    /** 端口 */
    private int port = 9200;

    /** 认证用户名（为空表示不启用 Basic 认证） */
    private String username;

    /** 认证密码 */
    private String password;
}
