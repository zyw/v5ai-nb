package xin.v5ai.nb.rag.core.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 现有 PDFBox/POI/Jsoup 解析器的结构化适配层，也是所有外部引擎的默认兜底。
 */
@Component
@RequiredArgsConstructor
public class BuiltinDocumentParseEngine implements DocumentParseEngine {

    private static final String ENGINE = "default";

    private final DocumentParserRegistry parserRegistry;

    @Override
    public ParsedDocument parse(ParseRequest request) {
        var text = parserRegistry.parse(request.content(), request.fileType());
        return ParsedDocument.builder()
                .engine(ENGINE)
                .text(text)
                .build();
    }

    @Override
    public String engine() {
        return ENGINE;
    }
}
