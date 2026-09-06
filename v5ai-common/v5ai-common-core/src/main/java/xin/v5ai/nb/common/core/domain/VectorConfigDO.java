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

    /**
     * 引擎维度上限覆盖（可选，null=用引擎类型默认值 pgvector 2000 / Milvus 32768 / ES 2048）。
     * 用于适配部署版本差异（如 pgvector 0.5+ 可至 16000）。
     */
    private Integer maxDimension;
}
