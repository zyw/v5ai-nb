package xin.v5ai.nb.rag.core.parser;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserTest {

    private final DocumentParserRegistry registry = new DocumentParserRegistry();

    @Test
    void parsesDocxParagraphsAndTables() throws IOException {
        var text = registry.parse(docxBytes(), DocumentFileType.DOCX);
        assertThat(text).contains("Hello DOCX", "A", "B");
    }

    @Test
    void parsesXlsxCells() throws IOException {
        var text = registry.parse(xlsxBytes(), DocumentFileType.XLSX);
        assertThat(text).contains("Name", "42");
    }

    @Test
    void parsesPptxText() throws IOException {
        var text = registry.parse(pptxBytes(), DocumentFileType.PPTX);
        assertThat(text).contains("Hello PPTX");
    }

    @Test
    void parsesHtmlToPlainText() {
        var html = "<html><body><script>ignored()</script><p>Hello &amp; world</p></body></html>";
        assertThat(registry.parse(html.getBytes(StandardCharsets.UTF_8), DocumentFileType.HTML))
                .isEqualTo("Hello & world");
    }

    @Test
    void parsesCsvAsPlainText() {
        var csv = "a,b,c\n1,2,3";
        assertThat(registry.parse(csv.getBytes(StandardCharsets.UTF_8), DocumentFileType.CSV))
                .isEqualTo(csv);
    }

    private static byte[] docxBytes() throws IOException {
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Hello DOCX");
            var row = doc.createTable(1, 2).getRow(0);
            row.getCell(0).setText("A");
            row.getCell(1).setText("B");
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] xlsxBytes() throws IOException {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("Data").createRow(0);
            row.createCell(0).setCellValue("Name");
            row.createCell(1).setCellValue(42);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] pptxBytes() throws IOException {
        try (var slideshow = new XMLSlideShow(); var out = new ByteArrayOutputStream()) {
            slideshow.createSlide().createTextBox().setText("Hello PPTX");
            slideshow.write(out);
            return out.toByteArray();
        }
    }
}