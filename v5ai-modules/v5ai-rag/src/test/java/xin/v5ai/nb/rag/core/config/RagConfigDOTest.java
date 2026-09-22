package xin.v5ai.nb.rag.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagConfigDOTest {

    @Test
    void roundTripsMineruParametersWithoutDroppingFields() {
        var config = RagConfigDO.builder()
                .parseParams(RagConfigDO.ParseParams.builder()
                        .engine("mineru")
                        .mineru(RagConfigDO.MineruParams.builder()
                                .langList(List.of("ch", "korean"))
                                .backend("hybrid-engine")
                                .effort("high")
                                .parseMethod("ocr")
                                .formulaEnable(true)
                                .tableEnable(true)
                                .imageAnalysis(true)
                                .returnMd(true)
                                .returnMiddleJson(true)
                                .returnImages(false)
                                .startPageId(2)
                                .endPageId(8)
                                .build())
                        .build())
                .build();

        var restored = RagConfigDO.fromJson(RagConfigDO.toJson(config));

        assertThat(restored.getParseParams().getEngine()).isEqualTo("mineru");
        assertThat(restored.getParseParams().getMineru().getLangList()).containsExactly("ch", "korean");
        assertThat(restored.getParseParams().getMineru().getBackend()).isEqualTo("hybrid-engine");
        assertThat(restored.getParseParams().getMineru().getStartPageId()).isEqualTo(2);
        assertThat(restored.getParseParams().getMineru().getEndPageId()).isEqualTo(8);
    }
}
