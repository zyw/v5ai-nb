package xin.v5ai.nb.api.rag;

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
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.KnowledgeManagementService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin")
public class KnowledgeDocumentController {
    private final KnowledgeManagementService knowledgeManagementService;

    public KnowledgeDocumentController(KnowledgeManagementService knowledgeManagementService) {
        this.knowledgeManagementService = knowledgeManagementService;
    }

    @PostMapping(value = "/knowledge-bases/{kbId}/documents", consumes = "multipart/form-data")
    public R<DocumentResponse> uploadDocument(
            @PathVariable("kbId") Long kbId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        var filename = file.getOriginalFilename();
        var resolvedTitle = title == null || title.isBlank() ? filename : title;
        var fileType = detectFileType(filename);
        try {
            return R.ok(DocumentResponse.from(knowledgeManagementService.uploadDocument(
                    kbId, resolvedTitle, fileType, file.getBytes(), filename)));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded file", exception);
        }
    }

    @PostMapping("/knowledge-bases/{kbId}/documents/url")
    public R<DocumentResponse> importUrl(@PathVariable("kbId") Long kbId, @RequestBody ImportUrlRequest request) {
        return R.ok(DocumentResponse.from(knowledgeManagementService.importUrl(kbId, request.title(), request.url())));
    }

    @GetMapping("/knowledge-bases/{kbId}/documents")
    public R<PageResult<DocumentResponse>> listDocuments(@PathVariable("kbId") Long kbId, PageQuery pageQuery) {
        var list = knowledgeManagementService.listDocuments(kbId).stream()
                .map(DocumentResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @DeleteMapping("/documents/{id}")
    public R<Void> deleteDocument(@PathVariable("id") Long id) {
        knowledgeManagementService.deleteDocument(id);
        return R.ok();
    }

    @PostMapping("/documents/{id}/retry")
    public R<Void> retryDocument(@PathVariable("id") Long id) {
        var tasks = knowledgeManagementService.listTasksByDocument(id);
        for (var task : tasks) {
            knowledgeManagementService.retryTask(task.id());
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
