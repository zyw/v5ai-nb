package xin.v5ai.nb.rag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.platform.api.ResourceContentPort;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.core.parser.ParserHealthService;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.bo.DocumentIdsBo;
import xin.v5ai.nb.rag.domain.bo.ImportUrlBo;
import xin.v5ai.nb.rag.domain.vo.DocumentBatchResultVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeDocumentVo;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;
import xin.v5ai.nb.rag.service.IKnowledgeDocumentService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
    private final IKnowledgeDocumentService documentService;
    private final ResourceContentPort resourceContentPort;
    private final ParserHealthService parserHealthService;

    @SaCheckPermission("rag:document:list")
    @GetMapping("/parser-engines/health")
    public R<java.util.Map<String, java.util.Map<String, Object>>> parserHealth() {
        return R.ok(parserHealthService.check());
    }

    @SaCheckPermission("rag:document:add")
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
                    kbId, resolvedTitle, filename, fileType, file.getBytes(), file.getContentType()));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded file", exception);
        }
    }

    @SaCheckPermission("rag:document:add")
    @PostMapping("/knowledge-bases/{kbId}/documents/url")
    public R<KnowledgeDocumentVo> importUrl(@PathVariable("kbId") Long kbId, @RequestBody ImportUrlBo request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        return R.ok(knowledgeBaseService.importUrl(kbId, request.title(), request.url()));
    }

    @SaCheckPermission("rag:document:list")
    @GetMapping("/knowledge-bases/{kbId}/documents")
    public R<PageResult<KnowledgeDocumentVo>> listDocuments(@PathVariable("kbId") Long kbId, PageQuery pageQuery) {
        return R.ok(knowledgeBaseService.listDocuments(kbId, pageQuery));
    }

    @SaCheckPermission("rag:document:remove")
    @DeleteMapping("/documents/{id}")
    public R<Void> deleteDocument(@PathVariable("id") Long id) {
        knowledgeBaseService.deleteDocument(id);
        return R.ok();
    }

    @SaCheckPermission("rag:document:reparse")
    @PostMapping("/documents/{id}/retry")
    public R<Void> retryDocument(@PathVariable("id") Long id) {
        var tasks = knowledgeBaseService.listTasksByDocument(id);
        for (var task : tasks) {
            knowledgeBaseService.retryTask(task.getId());
        }
        return R.ok();
    }

    @SaCheckPermission("rag:document:reparse")
    @PostMapping("/documents/{id}/reparse")
    public R<Boolean> reparse(@PathVariable("id") Long id) {
        return R.ok(knowledgeBaseService.reparseDocument(id));
    }

    @SaCheckPermission("rag:document:download")
    @GetMapping("/documents/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable("id") Long id) {
        var document = requireDocument(id);
        if (document.getResourceId() != null) {
            var content = readResourceSafely(document.getResourceId());
            return ResponseEntity.ok()
                    .contentType(resolveMediaType(content.mimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(content.bytes());
        }
        return ResponseEntity.ok()
                .contentType(resolvePreviewMediaType(document.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(contentOf(document));
    }

    @SaCheckPermission("rag:document:download")
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable("id") Long id) {
        var document = requireDocument(id);
        if (document.getResourceId() != null) {
            var content = readResourceSafely(document.getResourceId());
            String name = content.originalName() == null || content.originalName().isBlank()
                    ? "document" : content.originalName();
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .body(content.bytes());
        }
        String encoded = URLEncoder.encode(resolveDownloadName(document), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(contentOf(document));
    }

    @SaCheckPermission("rag:document:reparse")
    @PostMapping("/documents/batch-reparse")
    public R<DocumentBatchResultVo> batchReparse(@RequestBody DocumentIdsBo bo) {
        var failures = new ArrayList<DocumentBatchResultVo.Failure>();
        int succeeded = 0;
        int skipped = 0;
        for (Long id : safeIds(bo)) {
            try {
                if (knowledgeBaseService.reparseDocument(id)) {
                    succeeded++;
                } else {
                    skipped++;
                }
            } catch (Exception exception) {
                failures.add(new DocumentBatchResultVo.Failure(id, reason(exception)));
            }
        }
        return R.ok(new DocumentBatchResultVo(succeeded, skipped, failures));
    }

    @SaCheckPermission("rag:document:remove")
    @PostMapping("/documents/batch-delete")
    public R<DocumentBatchResultVo> batchDelete(@RequestBody DocumentIdsBo bo) {
        var failures = new ArrayList<DocumentBatchResultVo.Failure>();
        int succeeded = 0;
        for (Long id : safeIds(bo)) {
            try {
                knowledgeBaseService.deleteDocument(id);
                succeeded++;
            } catch (Exception exception) {
                failures.add(new DocumentBatchResultVo.Failure(id, reason(exception)));
            }
        }
        return R.ok(new DocumentBatchResultVo(succeeded, 0, failures));
    }

    private static List<Long> safeIds(DocumentIdsBo bo) {
        return bo == null || bo.ids() == null ? List.of() : bo.ids();
    }

    private static String reason(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private KnowledgeDocument requireDocument(Long id) {
        var document = documentService.findById(id);
        if (document == null) {
            throw new IllegalArgumentException("document does not exist: " + id);
        }
        return document;
    }

    private static byte[] contentOf(KnowledgeDocument document) {
        var content = document.getContent();
        return content == null ? new byte[0] : content;
    }

    private ResourceContentPort.Content readResourceSafely(Long resourceId) {
        try {
            return resourceContentPort.read(resourceId);
        } catch (Exception exception) {
            throw new ServiceException("源文件已不存在，请删除后重新导入");
        }
    }

    private static MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static MediaType resolvePreviewMediaType(String fileType) {
        try {
            var type = DocumentFileType.valueOf((fileType == null ? "" : fileType).toUpperCase(Locale.ROOT));
            return MediaType.parseMediaType(type.defaultContentType());
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String resolveDownloadName(KnowledgeDocument document) {
        String title = document.getTitle() == null || document.getTitle().isBlank()
                ? "document" : document.getTitle().trim();
        String extension = extensionOf(document.getFileType());
        if (extension.isEmpty()) {
            return title;
        }
        return title.toLowerCase(Locale.ROOT).endsWith(extension) ? title : title + extension;
    }

    private static String extensionOf(String fileType) {
        try {
            return DocumentFileType.valueOf((fileType == null ? "" : fileType).toUpperCase(Locale.ROOT)).extension();
        } catch (Exception e) {
            return "";
        }
    }

    private DocumentFileType detectFileType(String filename) {
        return DocumentFileType.fromFilename(filename);
    }
}
