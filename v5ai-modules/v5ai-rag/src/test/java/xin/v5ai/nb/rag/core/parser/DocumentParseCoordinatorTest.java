package xin.v5ai.nb.rag.core.parser;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentParseCoordinatorTest {

    private final ParseRequest request = new ParseRequest(
            "source".getBytes(), "source.txt", DocumentFileType.TXT, 12L, 34L, null);

    @Test
    void fallsBackToDefaultWhenExternalEngineThrows() {
        var defaultEngine = engine("default", ParsedDocument.builder().engine("default").text("fallback").build());
        var docling = throwingEngine("docling");
        var coordinator = new DocumentParseCoordinator(
                new DocumentParseEngineRegistry(List.of(defaultEngine, docling)));

        var result = coordinator.parse(requestWithEngine("docling"));

        assertThat(result.getEngine()).isEqualTo("default");
        assertThat(result.content()).isEqualTo("fallback");
    }

    @Test
    void fallsBackToDefaultWhenExternalResultIsBlank() {
        var defaultEngine = engine("default", ParsedDocument.builder().engine("default").text("fallback").build());
        var mineru = engine("mineru", ParsedDocument.builder().engine("mineru").build());
        var coordinator = new DocumentParseCoordinator(
                new DocumentParseEngineRegistry(List.of(defaultEngine, mineru)));

        var result = coordinator.parse(requestWithEngine("mineru"));

        assertThat(result.getEngine()).isEqualTo("default");
    }

    @Test
    void propagatesDefaultFailureAfterExternalFailure() {
        var defaultEngine = new DocumentParseEngine() {
            @Override
            public ParsedDocument parse(ParseRequest request) {
                throw new IllegalStateException("default failed");
            }

            @Override
            public String engine() {
                return "default";
            }
        };
        var docling = throwingEngine("docling");
        var coordinator = new DocumentParseCoordinator(
                new DocumentParseEngineRegistry(List.of(defaultEngine, docling)));

        assertThatThrownBy(() -> coordinator.parse(requestWithEngine("docling")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("default failed");
    }

    private ParseRequest requestWithEngine(String engine) {
        return new ParseRequest(request.content(), request.filename(), request.fileType(), request.documentId(),
                request.knowledgeBaseId(), new xin.v5ai.nb.rag.core.config.RagConfigDO.ParseParams(engine, null, null));
    }

    private static DocumentParseEngine throwingEngine(String name) {
        return new DocumentParseEngine() {
            @Override
            public ParsedDocument parse(ParseRequest request) {
                throw new IllegalStateException(name + " unavailable");
            }

            @Override
            public String engine() {
                return name;
            }
        };
    }

    private static DocumentParseEngine engine(String name, ParsedDocument result) {
        return new DocumentParseEngine() {
            @Override
            public ParsedDocument parse(ParseRequest request) {
                return result;
            }

            @Override
            public String engine() {
                return name;
            }
        };
    }
}
