package xin.v5ai.nb.common.core.page;

public record PageQuery(int page, int size) {
    public PageQuery {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 1 and size must be between 1 and 100");
        }
    }
}
