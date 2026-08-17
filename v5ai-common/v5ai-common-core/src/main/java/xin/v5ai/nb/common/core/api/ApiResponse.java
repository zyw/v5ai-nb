package xin.v5ai.nb.common.core.api;

import xin.v5ai.nb.common.core.exception.ErrorCode;

public record ApiResponse<T>(boolean success, T data, ErrorCode code, String message) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, code, message);
    }
}
