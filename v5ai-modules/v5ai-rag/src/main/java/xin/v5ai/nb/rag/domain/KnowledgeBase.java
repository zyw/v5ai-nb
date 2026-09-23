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
 * 知识库实体（v5ai_knowledge_base）：status 为 ACTIVE/DISABLED。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_knowledge_base")
public class KnowledgeBase extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 启用状态（ACTIVE / DISABLED）
     */
    private String status;

    /**
     * 图标
     */
    private String icon;

    /**
     * 向量模型id
     */
    private Long embeddingModelId;

    /**
     * 向量存储实例id
     */
    private Long vectorStoreInstanceId;

    /**
     * 向量维度
     */
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
     * RAG检索和问答的页面配置参数（RagConfigDO JSON）
     */
    private String config;

    /**
     * 去重策略: 0=NONE 1=BY_NAME 2=BY_CONTENT 3=BY_NAME_OR_CONTENT
     */
    private Integer dedupStrategy;

    /**
     * 冲突动作: 0=REJECT 1=SKIP 2=OVERWRITE
     */
    private Integer dedupAction;
}
