package xin.v5ai.nb.api.error;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import xin.v5ai.nb.common.core.api.ApiResponse;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(V5aiException.class)
    public ResponseEntity<ApiResponse<Void>> handleV5aiException(V5aiException exception) {
        return ResponseEntity
                .status(statusFor(exception.code()))
                .body(ApiResponse.failure(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(ErrorCode.INVALID_ARGUMENT, exception.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin(NotLoginException exception) {
        log.error("登录失败，消息：{}", exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED, "not logged in"));
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotRole(NotRoleException exception) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED, "insufficient role: admin role required"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        LOG.warn("Data integrity violation (foreign key / uniqueness)", exception);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(ErrorCode.CONFLICT,
                        "record is referenced by other data and cannot be deleted"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException exception) {
        LOG.warn("Uploaded file exceeds the configured size limit", exception);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(ErrorCode.INVALID_ARGUMENT,
                        "uploaded file exceeds the configured size limit (max-file-size/request-size)"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        var message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("invalid request body");
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(ErrorCode.INVALID_ARGUMENT, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception exception) {
        LOG.error("Unhandled exception in request", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR, "internal server error"));
    }

    private HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
