package xin.v5ai.nb.rag.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

/**
 * <p>
 * 存储实例实体（v5ai_store_instance）：向量库 / 搜索引擎实例配置。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-30
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_store_instance")
public class StoreInstance extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 实例名称
     */
    private String name;

    /**
     * 实例描述
     */
    private String description;

    /**
     * 分类: 1-向量库 2-搜索引擎
     */
    private Integer category;

    /**
     * 类型: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-DB_FULLTEXT（业务库原生 BM25，历史名 PG_FULLTEXT）
     */
    private Integer type;

    /**
     * 连接参数 JSON
     * type=1时，PgVectorConfigDO（xin.v5ai.nb.rag.core.config）
     * type=2时，MilvusVectorConfigDO（xin.v5ai.nb.common.milvus）
     * type=3时，ElasticsearchVectorConfigDO（xin.v5ai.nb.common.elasticsearch）
     * type=4时，PgVectorConfigDO
     */
    private String config;

    /**
     * 状态: 0-停用 1-启用
     */
    private Integer status;

    /**
     * 是否为该 category 下默认实例
     */
    private Boolean isDefault;
}
