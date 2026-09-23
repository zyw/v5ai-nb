package xin.v5ai.nb.rag.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.rag.domain.StoreInstance;

/**
 * 存储实例请求体。
 *
 * @author ZYW
 * @since 2026-08-30
 */
@Data
@AutoMapper(target = StoreInstance.class, reverseConvertGenerate = false)
public class StoreInstanceBo {

    /**
     * 主键，编辑时必填
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 实例名称
     */
    @NotBlank(message = "实例名称不能为空")
    private String name;

    /**
     * 实例描述
     */
    private String description;

    /**
     * 分类: 1-向量库 2-搜索引擎
     */
    @NotNull(message = "分类不能为空")
    private Integer category;

    /**
     * 类型: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-DB_FULLTEXT（业务库原生 BM25，历史名 PG_FULLTEXT）
     */
    @NotNull(message = "类型不能为空")
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
}
