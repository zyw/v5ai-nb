package xin.v5ai.nb.rag.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.domain.bo.ImportUrlBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeDocumentVo;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * 知识库文档管理 API：上传、URL 导入、文档列表、删除与任务重试。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class KnowledgeDocumentController extends BaseController {

    private final IKnowledgeBaseService knowledgeBaseService;

    @PostMapping(value = "/knowledge-bases/{kbId}/documents", consumes = "multipart/form-data")
    public R<KnowledgeDocumentVo> uploadDocument(
            @PathVariable("kbId") Long kbId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        var filename = file.getOriginalFilename();
        var resolvedTitle = title == null || title.isBlank() ? filename : title;
        var fileType = detectFileType(filename);
        try {
            return R.ok(knowledgeBaseService.uploadDocument(
                    kbId, resolvedTitle, fileType, file.getBytes()));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded file", exception);
        }
    }

    @PostMapping("/knowledge-bases/{kbId}/documents/url")
    public R<KnowledgeDocumentVo> importUrl(@PathVariable("kbId") Long kbId, @RequestBody ImportUrlBo request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        return R.ok(knowledgeBaseService.importUrl(kbId, request.title(), request.url()));
    }

    @GetMapping("/knowledge-bases/{kbId}/documents")
    public R<PageResult<KnowledgeDocumentVo>> listDocuments(@PathVariable("kbId") Long kbId, PageQuery pageQuery) {
        return R.ok(knowledgeBaseService.listDocuments(kbId, pageQuery));
    }

    @DeleteMapping("/documents/{id}")
    public R<Void> deleteDocument(@PathVariable("id") Long id) {
        knowledgeBaseService.deleteDocument(id);
        return R.ok();
    }

    @PostMapping("/documents/{id}/retry")
    public R<Void> retryDocument(@PathVariable("id") Long id) {
        var tasks = knowledgeBaseService.listTasksByDocument(id);
        for (var task : tasks) {
            knowledgeBaseService.retryTask(task.getId());
        }
        return R.ok();
    }

    private DocumentFileType detectFileType(String filename) {
        var lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return DocumentFileType.PDF;
        }
        if (lower.endsWith(".docx")) {
            return DocumentFileType.DOCX;
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return DocumentFileType.MARKDOWN;
        }
        return DocumentFileType.TXT;
    }
}
