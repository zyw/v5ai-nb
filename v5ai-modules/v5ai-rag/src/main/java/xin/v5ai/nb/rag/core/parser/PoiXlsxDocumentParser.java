package xin.v5ai.nb.rag.core.parser;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * XLSX 解析器：基于 Apache POI {@link XSSFWorkbook}，逐 sheet/row/cell 抽取格式化文本。
 * 单元格之间以 {@code \t} 分隔、行以 {@code \n} 分隔，保留表格可读结构。
 */
public class PoiXlsxDocumentParser implements DocumentParser {
    @Override
    public String parse(byte[] content, DocumentFileType type) {
        if (type != DocumentFileType.XLSX) {
            throw new IllegalArgumentException("unsupported file type for XLSX parser: " + type);
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var formatter = new DataFormatter();
            var text = new StringBuilder();
            for (var sheet : workbook) {
                for (var row : sheet) {
                    boolean hasValue = false;
                    for (var cell : row) {
                        var value = formatter.formatCellValue(cell);
                        if (value != null && !value.isBlank()) {
                            text.append(value).append('\t');
                            hasValue = true;
                        }
                    }
                    if (hasValue) {
                        text.append('\n');
                    }
                }
            }
            return text.toString().trim();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to parse XLSX document", exception);
        }
    }
}