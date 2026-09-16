package xin.v5ai.nb.rag.domain.vo;

import lombok.Data;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.domain.KnowledgeBase;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * <p>
 * 知识库视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
public class KnowledgeBaseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

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
     * RAG检索和问答的页面配置参数；自动映射忽略，由服务反序列化填充
     */
    private RagConfigDO config;

    /**
     * 去重策略: 0=NONE 1=BY_NAME 2=BY_CONTENT 3=BY_NAME_OR_CONTENT
     */
    private Integer dedupStrategy;

    /**
     * 冲突动作: 0=REJECT 1=SKIP 2=OVERWRITE
     */
    private Integer dedupAction;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /**
     * 库内文档数（列表 / 详情接口均统计填充）
     */
    private Long docCount;

    /**
     * 库内切片数（列表 / 详情接口均统计填充）
     */
    private Long chunkCount;

    /**
     * 引用该知识库的 AgentDTO 标识列表（列表接口批量回填；被引用时删除须拦截、禁用仅警告）
     */
    private List<String> referencedAgentKeys;
}
