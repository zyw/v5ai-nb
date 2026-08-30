package xin.v5ai.nb.rag.service;

import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.domain.bo.KnowledgeBaseBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeDocumentVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeTaskVo;

import java.util.List;

/**
 * 知识库管理服务：知识库 CRUD、文档上传/URL 导入、索引任务查询、Agent 绑定。
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
     * 查询单个知识库。
     */
    KnowledgeBaseVo getKnowledgeBase(Long id);

    /**
     * 禁用知识库。
     */
    void disableKnowledgeBase(Long id);

    /**
     * 上传文档：落库文档行（来源 UPLOAD，含原始内容/大小/SHA-256）并提交一个 PENDING 索引任务。
     */
    KnowledgeDocumentVo uploadDocument(Long knowledgeBaseId, String title, DocumentFileType fileType,
                                       byte[] content);

    /**
     * URL 导入：抓取网页内容后按 URL 类型文档处理。
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
     * 查询某知识库的索引任务列表（新任务在前）。
     */
    List<KnowledgeTaskVo> listTasks(Long knowledgeBaseId);

    /**
     * 查询某文档的索引任务列表。
     */
    List<KnowledgeTaskVo> listTasksByDocument(Long documentId);

    /**
     * 全量替换某 Agent 绑定的知识库集合。
     */
    void bindKnowledgeBases(String agentKey, List<Long> knowledgeBaseIds);

    /**
     * 查询某 Agent 绑定的知识库 ID 集合。
     */
    List<Long> getKnowledgeBindings(String agentKey);
}
