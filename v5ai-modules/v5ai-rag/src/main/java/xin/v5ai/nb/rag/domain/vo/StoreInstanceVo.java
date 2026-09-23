package xin.v5ai.nb.rag.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.rag.domain.StoreInstance;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * 存储实例视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-30
 */
@Data
@AutoMapper(target = StoreInstance.class)
public class StoreInstanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

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

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
