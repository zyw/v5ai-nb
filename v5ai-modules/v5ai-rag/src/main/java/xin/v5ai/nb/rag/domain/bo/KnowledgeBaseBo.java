package xin.v5ai.nb.rag.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import io.github.linpeilie.annotations.AutoMapping;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.domain.KnowledgeBase;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库请求体（列表查询条件 / 创建 / 编辑共用）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = KnowledgeBase.class, reverseConvertGenerate = false)
public class KnowledgeBaseBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，编辑时必填
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 知识库名称（模糊匹配 / 必填）
     */
    @NotBlank(message = "知识库名称不能为空")
    private String name;

    /**
     * 启用状态（ACTIVE / DISABLED）
     */
    private String status;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 图标
     */
    private String icon;

    /**
     * 向量模型id
     */
    @NotNull(message = "向量模型不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long embeddingModelId;

    /**
     * 向量存储实例id
     */
    @NotNull(message = "向量库不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long vectorStoreInstanceId;

    /**
     * 向量维度
     */
    @NotNull(message = "向量维度不能为空", groups = {AddGroup.class, EditGroup.class})
    private Integer dimensionOfVectorModel;

    /**
     * 重排序模型id
     */
    private Long rerankModelId;

    /**
     * 搜索引擎启用标志
     */
    private Boolean searchEngineEnable;

    /**
     * 搜索引擎实例id
     */
    private Long searchEngineInstanceId;

    /**
     * 文档分割符
     */
    private String delimiter;

    /**
     * RAG增强配置
     */
    private String ragEnhancement;

    /**
     * RAG检索和问答的页面配置参数；自动映射忽略，由服务序列化为 JSON 落库
     */
    @AutoMapping(ignore = true)
    private RagConfigDO config;

    /**
     * 去重策略: 0=NONE 1=BY_NAME 2=BY_CONTENT 3=BY_NAME_OR_CONTENT
     */
    private Integer dedupStrategy;

    /**
     * 冲突动作: 0=REJECT 1=SKIP 2=OVERWRITE
     */
    private Integer dedupAction;
}
