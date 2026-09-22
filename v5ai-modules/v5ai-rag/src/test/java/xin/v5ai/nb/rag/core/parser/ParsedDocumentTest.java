package xin.v5ai.nb.rag.core.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParsedDocumentTest {

    @Test
    void prefersTextThenMarkdownAndReportsBlankWhenBothAreEmpty() {
        var text = ParsedDocument.builder().engine("default").text("plain").markdown("# markdown").build();
        var markdown = ParsedDocument.builder().engine("docling").text(" ").markdown("# markdown").build();
        var blank = ParsedDocument.builder().engine("mineru").build();

        assertThat(text.content()).isEqualTo("plain");
        assertThat(markdown.content()).isEqualTo("# markdown");
        assertThat(blank.content()).isBlank();
    }
}
