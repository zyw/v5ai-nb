package xin.v5ai.nb.common.core.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INVALID_ARGUMENT(400),
    UNAUTHORIZED(401),
    NOT_FOUND(404),
    TOO_MANY_REQUESTS(429),
    CONFLICT(409),
    INTERNAL_ERROR(500);

    private final Integer code;

    ErrorCode(Integer code) {
        this.code = code;
    }
}
