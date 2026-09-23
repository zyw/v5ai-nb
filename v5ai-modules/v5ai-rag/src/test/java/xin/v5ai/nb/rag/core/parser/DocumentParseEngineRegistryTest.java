package xin.v5ai.nb.rag.core.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentParseEngineRegistryTest {

    @Test
    void resolvesEngineNamesCaseInsensitivelyAndDefaultsBlankName() {
        var defaultEngine = engine("default");
        var docling = engine("docling");
        var registry = new DocumentParseEngineRegistry(List.of(defaultEngine, docling));

        assertThat(registry.resolve("DOCLING")).isSameAs(docling);
        assertThat(registry.resolve(" ")).isSameAs(defaultEngine);
        assertThat(registry.defaultEngine()).isSameAs(defaultEngine);
    }

    @Test
    void rejectsUnknownEngine() {
        var registry = new DocumentParseEngineRegistry(List.of(engine("default")));

        assertThatThrownBy(() -> registry.resolve("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    private static DocumentParseEngine engine(String name) {
        return new DocumentParseEngine() {
            @Override
            public ParsedDocument parse(ParseRequest request) {
                return ParsedDocument.builder().engine(name).text(name).build();
            }

            @Override
            public String engine() {
                return name;
            }
        };
    }
}
