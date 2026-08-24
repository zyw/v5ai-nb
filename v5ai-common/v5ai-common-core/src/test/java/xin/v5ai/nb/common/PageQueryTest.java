package xin.v5ai.nb.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {
    @Test
    void pageSizeIsBounded() {
        // 构造器参数顺序为 (pageSize, pageNum)，超大 pageSize 应被拒绝
        assertThatThrownBy(() -> new PageQuery(1001, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullPageSizeUsesDefaultAll() {
        var query = new PageQuery();
        assertThat(query.build().getSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
    }
}
