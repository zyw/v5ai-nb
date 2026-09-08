package xin.v5ai.nb.rag.core.enums;

import java.util.Locale;

/**
 * 知识库文档文件类型。作为解析器分发的唯一键，值以字符串形式落库（varchar，无需迁移）。
 */
public enum DocumentFileType {
    TXT,
    MARKDOWN,
    PDF,
    DOCX,
    XLSX,
    PPTX,
    HTML,
    CSV,
    /**
     * 历史遗留：早期 URL 导入统一落为 URL。新导入按下载内容判定为具体类型（HTML/XLSX/…），
     * 该值仅用于兼容存量行，不再由新导入产生。
     */
    URL;

    /**
     * 该类型对应的文件扩展名（含点号）；无固定扩展名返回空串。
     */
    public String extension() {
        return switch (this) {
            case PDF -> ".pdf";
            case DOCX -> ".docx";
            case XLSX -> ".xlsx";
            case PPTX -> ".pptx";
            case HTML -> ".html";
            case CSV -> ".csv";
            case MARKDOWN -> ".md";
            case TXT -> ".txt";
            default -> "";
        };
    }

    /**
     * 该类型默认的 MIME Content-Type（用于资源存储/预览/下载兜底）。
     */
    public String defaultContentType() {
        return switch (this) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case PPTX -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case HTML -> "text/html";
            case CSV -> "text/csv";
            case MARKDOWN -> "text/markdown";
            case TXT, URL -> "text/plain";
        };
    }

    /**
     * 由文件名判定类型，无匹配扩展名时兜底 {@link #TXT}（本地上传语义）。
     */
    public static DocumentFileType fromFilename(String filename) {
        var type = byExtension(filename);
        return type != null ? type : TXT;
    }

    /**
     * 由 Content-Type 响应头判定类型，未知/为空返回 {@code null}。
     */
    public static DocumentFileType fromContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        var lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.contains("pdf")) {
            return PDF;
        }
        if (lower.contains("spreadsheetml") || lower.contains("excel") || lower.contains("xlsx")) {
            return XLSX;
        }
        if (lower.contains("presentationml") || lower.contains("powerpoint") || lower.contains("pptx")) {
            return PPTX;
        }
        if (lower.contains("wordprocessingml") || lower.contains("msword") || lower.contains("docx")) {
            return DOCX;
        }
        if (lower.contains("text/csv")) {
            return CSV;
        }
        if (lower.contains("text/markdown")) {
            return MARKDOWN;
        }
        if (lower.contains("text/html")) {
            return HTML;
        }
        if (lower.startsWith("text/")) {
            return TXT;
        }
        return null;
    }

    /**
     * URL 导入的类型判定：URL 路径扩展名优先 → Content-Type 兜底 → 默认 HTML（网页）。
     */
    public static DocumentFileType fromUrl(String url, String contentType) {
        var type = byExtension(stripQueryFragment(url));
        if (type != null) {
            return type;
        }
        var byContentType = fromContentType(contentType);
        return byContentType != null ? byContentType : HTML;
    }

    private static DocumentFileType byExtension(String pathOrFilename) {
        var lower = pathOrFilename == null ? "" : pathOrFilename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return PDF;
        }
        if (lower.endsWith(".docx")) {
            return DOCX;
        }
        if (lower.endsWith(".xlsx")) {
            return XLSX;
        }
        if (lower.endsWith(".pptx")) {
            return PPTX;
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return HTML;
        }
        if (lower.endsWith(".csv")) {
            return CSV;
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return MARKDOWN;
        }
        return null;
    }

    private static String stripQueryFragment(String url) {
        if (url == null) {
            return "";
        }
        var path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        return path;
    }
}