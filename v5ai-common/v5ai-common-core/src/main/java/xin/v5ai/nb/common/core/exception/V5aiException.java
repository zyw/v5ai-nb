package xin.v5ai.nb.common.core.exception;

public class V5aiException extends RuntimeException {
    private final ErrorCode code;

    public V5aiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
