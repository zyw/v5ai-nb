package xin.v5ai.nb.common.milvus.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xin.v5ai.nb.common.core.domain.VectorConfigDO;

/**
 * Milvus 连接与集合/索引参数，来自存储实例 {@code config} JSON。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MilvusVectorConfigDO extends VectorConfigDO {

//    /** 是否启用该 Milvus 向量库配置 */
//    private boolean enabled = true;
//
//    /** 主机地址 */
//    private String host = "localhost";
//
//    /** 端口 */
//    private int port = 19530;
//
//    /** 认证 token（为空表示不启用鉴权） */
//    private String token;

    /** 数据库名 */
    private String database = "default";

    /** 实际 collection：{collectionPrefix}_{knowledgeBaseId} */
    private String collectionPrefix = "v5ai_rag_vector";

    /** COSINE | L2 | IP */
    private String metricType = "COSINE";

    /** IVF_FLAT | IVF_SQ8 | HNSW | AUTOINDEX */
    private String indexType = "AUTOINDEX";

    /** IVF 类索引的聚类数（nlist） */
    private int nlist = 1024;

    /** 检索时探测的聚类数（nprobe），越大召回越准但越慢 */
    private int nprobe = 16;
}
