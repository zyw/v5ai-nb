package xin.v5ai.nb.rag.core.parser;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParserHttpSupportTest {

    @Test
    void findsMarkdownAndTaskIdInOpenJsonObjects() {
        Object value = ParserHttpSupport.json("{\"task\":{\"id\":\"t-1\"},\"document\":{\"md_content\":\"# title\"}}");

        assertThat(ParserHttpSupport.findTaskId(value)).isEqualTo("t-1");
        assertThat(ParserHttpSupport.findText(value)).isEqualTo("# title");
    }

    @Test
    void multipartContainsMineruContractFieldNames() {
        byte[] body = ParserHttpSupport.multipart("b", "a.pdf", "x".getBytes(StandardCharsets.UTF_8),
                Map.of("backend", "hybrid-engine", "return_md", "true", "start_page_id", "0"));

        String value = new String(body, StandardCharsets.UTF_8);
        assertThat(value).contains("name=\"files\"", "name=\"backend\"", "name=\"return_md\"", "name=\"start_page_id\"");
    }
}
