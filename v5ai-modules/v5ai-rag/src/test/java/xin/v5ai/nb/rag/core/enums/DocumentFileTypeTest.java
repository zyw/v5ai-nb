package xin.v5ai.nb.rag.core.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentFileTypeTest {

    @Test
    void fromFilenameMapsExtensionsAndDefaultsToTxt() {
        assertThat(DocumentFileType.fromFilename("a.PDF")).isEqualTo(DocumentFileType.PDF);
        assertThat(DocumentFileType.fromFilename("b.docx")).isEqualTo(DocumentFileType.DOCX);
        assertThat(DocumentFileType.fromFilename("c.xlsx")).isEqualTo(DocumentFileType.XLSX);
        assertThat(DocumentFileType.fromFilename("d.pptx")).isEqualTo(DocumentFileType.PPTX);
        assertThat(DocumentFileType.fromFilename("e.html")).isEqualTo(DocumentFileType.HTML);
        assertThat(DocumentFileType.fromFilename("f.htm")).isEqualTo(DocumentFileType.HTML);
        assertThat(DocumentFileType.fromFilename("g.csv")).isEqualTo(DocumentFileType.CSV);
        assertThat(DocumentFileType.fromFilename("h.md")).isEqualTo(DocumentFileType.MARKDOWN);
        assertThat(DocumentFileType.fromFilename("i.markdown")).isEqualTo(DocumentFileType.MARKDOWN);
        assertThat(DocumentFileType.fromFilename("j.unknown")).isEqualTo(DocumentFileType.TXT);
    }

    @Test
    void fromContentTypeMapsMimeTypes() {
        assertThat(DocumentFileType.fromContentType("application/pdf")).isEqualTo(DocumentFileType.PDF);
        assertThat(DocumentFileType.fromContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isEqualTo(DocumentFileType.XLSX);
        assertThat(DocumentFileType.fromContentType(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation")).isEqualTo(DocumentFileType.PPTX);
        assertThat(DocumentFileType.fromContentType("text/html")).isEqualTo(DocumentFileType.HTML);
        assertThat(DocumentFileType.fromContentType("text/csv")).isEqualTo(DocumentFileType.CSV);
        assertThat(DocumentFileType.fromContentType("application/octet-stream")).isNull();
        assertThat(DocumentFileType.fromContentType(null)).isNull();
    }

    @Test
    void fromUrlPrefersExtensionThenContentTypeThenHtml() {
        assertThat(DocumentFileType.fromUrl("https://x/data.xlsx?token=1", null)).isEqualTo(DocumentFileType.XLSX);
        assertThat(DocumentFileType.fromUrl("https://x/report.pdf", "text/html")).isEqualTo(DocumentFileType.PDF);
        assertThat(DocumentFileType.fromUrl("https://x/unknown", "text/csv")).isEqualTo(DocumentFileType.CSV);
        assertThat(DocumentFileType.fromUrl("https://x/page", null)).isEqualTo(DocumentFileType.HTML);
    }

    @Test
    void extensionAndDefaultContentTypeAreConsistent() {
        assertThat(DocumentFileType.XLSX.extension()).isEqualTo(".xlsx");
        assertThat(DocumentFileType.PPTX.extension()).isEqualTo(".pptx");
        assertThat(DocumentFileType.HTML.extension()).isEqualTo(".html");
        assertThat(DocumentFileType.CSV.extension()).isEqualTo(".csv");
        assertThat(DocumentFileType.URL.extension()).isEmpty();
        assertThat(DocumentFileType.HTML.defaultContentType()).isEqualTo("text/html");
        assertThat(DocumentFileType.XLSX.defaultContentType()).contains("spreadsheetml");
    }
}