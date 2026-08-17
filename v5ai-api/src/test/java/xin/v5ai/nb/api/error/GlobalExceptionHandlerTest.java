package xin.v5ai.nb.api.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void mapsV5aiExceptionToDeclaredErrorCode() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleV5aiException(new V5aiException(ErrorCode.NOT_FOUND, "missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("missing");
    }

    @Test
    void mapsIllegalArgumentToInvalidArgument() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(response.getBody().message()).isEqualTo("bad input");
    }

    @Test
    void mapsUnknownExceptionToInternalErrorWithoutLeakingMessage() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleUnknown(new RuntimeException("database-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message()).isEqualTo("internal server error");
    }

    @Test
    void mapsDataIntegrityViolationToConflict() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleDataIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException(
                        "foreign key constraint violation"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(response.getBody().message()).contains("referenced by other data");
    }

    @Test
    void mapsMaxUploadSizeExceededToInvalidArgument() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleMaxUploadSize(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(1048576));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(response.getBody().message()).contains("exceeds the configured size limit");
    }
}
