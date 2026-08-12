package xin.v5ai.nb.common;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.page.PageQuery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {
    @Test
    void pageSizeIsBounded() {
        assertThatThrownBy(() -> new PageQuery(1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
