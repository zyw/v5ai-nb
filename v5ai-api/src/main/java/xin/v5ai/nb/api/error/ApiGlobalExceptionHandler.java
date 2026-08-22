package xin.v5ai.nb.api.error;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.exception.ErrorCode;

/**
 * API 层专属异常处理：仅处理公共模块 {@code common.web.handler.GlobalExceptionHandler}
 * 未覆盖的场景（Sa-Token 登录/权限、数据完整性冲突、上传大小限制、非法参数），
 * 统一返回 {@link R} 包装并保留真实 HTTP 状态码，便于前端按 {@code code} 判断。
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Slf4j
@RestControllerAdvice
public class ApiGlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity
                .badRequest()
                .body(R.fail(ErrorCode.INVALID_ARGUMENT.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException exception) {
        log.warn("未登录访问: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(ErrorCode.UNAUTHORIZED.getCode(), "not logged in"));
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<R<Void>> handleNotRole(NotRoleException exception) {
        log.warn("角色权限不足: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(R.fail(ErrorCode.UNAUTHORIZED.getCode(), "insufficient role: admin role required"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("数据完整性冲突（外键/唯一约束）: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(R.fail(ErrorCode.CONFLICT.getCode(),
                        "record is referenced by other data and cannot be deleted"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        log.warn("上传文件超出大小限制: {}", exception.getMessage());
        return ResponseEntity
                .badRequest()
                .body(R.fail(ErrorCode.INVALID_ARGUMENT.getCode(),
                        "uploaded file exceeds the configured size limit (max-file-size/request-size)"));
    }
}
