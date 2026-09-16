package xin.v5ai.nb.rag.service;

import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.domain.bo.KbConfigUpdateBo;
import xin.v5ai.nb.rag.domain.bo.KnowledgeBaseBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeDocumentVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeTaskVo;

import java.util.List;

/**
 * 知识库管理服务：知识库 CRUD、文档上传/URL 导入、索引任务查询、AgentDTO 绑定。
 *
 * @author ZYW
 * @since 2026-08-22
 */
public interface IKnowledgeBaseService {

    /**
     * 分页查询知识库。
     */
    PageResult<KnowledgeBaseVo> queryPageList(KnowledgeBaseBo bo, PageQuery pageQuery);

    /**
     * 知识库下拉选项（value + label，label 形如 "name (#id)"）。
     */
    List<OptionDTO> queryOptionList();

    /**
     * 创建知识库（默认 ACTIVE）。
     */
    KnowledgeBaseVo createKnowledgeBase(KnowledgeBaseBo bo);

    /**
     * 编辑知识库（覆盖写入配置类字段）。
     */
    KnowledgeBaseVo updateKnowledgeBase(KnowledgeBaseBo bo);

    /**
     * 轻量更新知识库检索/问答配置：仅整对象替换 {@code config} 中的 searchParams / modelParams
     * （对应键为空则保留存量），chunkParams / parseParams 与基本信息不受影响；
     * 不做维度/向量库/范围校验。两个键均为空时抛 {@link IllegalArgumentException}。
     */
    void updateRagConfig(Long id, KbConfigUpdateBo bo);

    /**
     * 查询单个知识库。
     */
    KnowledgeBaseVo getKnowledgeBase(Long id);

    /**
     * 查询单个知识库详情（含 docCount / chunkCount 统计）。
     */
    KnowledgeBaseVo getKnowledgeBaseDetail(Long id);

    /**
     * 禁用知识库。
     */
    void disableKnowledgeBase(Long id);

    /**
     * 启用知识库。
     */
    void enableKnowledgeBase(Long id);

    /**
     * 删除知识库（级联清理文档/切片/任务/绑定与向量数据）。
     */
    void deleteKnowledgeBase(Long id);

    /**
     * 上传文档：落库文档行（源文件写入资源存储，BYTEA 不写）并提交一个 PENDING 索引任务。
     *
     * @param originalFilename 原始文件名（用于资源存储 original_name 与扩展名/去重）
     * @param contentType      MIME 类型（写入资源存储）
     */
    KnowledgeDocumentVo uploadDocument(Long knowledgeBaseId, String title, String originalFilename,
                                       DocumentFileType fileType, byte[] content, String contentType);

    /**
     * URL 导入：抓取网页内容后按 URL 类型文档处理（内容写入资源存储，BYTEA 不写）。
     */
    KnowledgeDocumentVo importUrl(Long knowledgeBaseId, String title, String url);

    /**
     * 分页查询某知识库的文档列表。
     */
    PageResult<KnowledgeDocumentVo> listDocuments(Long knowledgeBaseId, PageQuery pageQuery);

    /**
     * 删除文档：清理向量、索引任务与文档行。
     */
    void deleteDocument(Long documentId);

    /**
     * 重置任务为 PENDING 并清空错误信息。
     */
    void retryTask(Long taskId);

    /**
     * 重新解析单个文档：状态为「处理完成/失败」时重置其索引任务为 PENDING 并返回 {@code true}；
     * 待处理/解析中/处理中返回 {@code false}（跳过）；文档不存在抛异常。
     */
    boolean reparseDocument(Long documentId);

    /**
     * 查询某知识库的索引任务列表（新任务在前）。
     */
    List<KnowledgeTaskVo> listTasks(Long knowledgeBaseId);

    /**
     * 查询某文档的索引任务列表。
     */
    List<KnowledgeTaskVo> listTasksByDocument(Long documentId);

    /**
     * 全量替换某 AgentDTO 绑定的知识库集合。
     */
    void bindKnowledgeBases(String agentKey, List<Long> knowledgeBaseIds);

    /**
     * 查询某 AgentDTO 绑定的知识库 ID 集合。
     */
    List<Long> getKnowledgeBindings(String agentKey);
}
