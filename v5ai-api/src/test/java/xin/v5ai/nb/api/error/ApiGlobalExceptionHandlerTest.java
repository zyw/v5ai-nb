package xin.v5ai.nb.api.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiGlobalExceptionHandlerTest {
    @Test
    void mapsIllegalArgumentToBadRequest() {
        var handler = new ApiGlobalExceptionHandler();

        var response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMsg()).isEqualTo("bad input");
    }

    @Test
    void mapsDataIntegrityViolationToConflict() {
        var handler = new ApiGlobalExceptionHandler();

        var response = handler.handleDataIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException(
                        "foreign key constraint violation"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMsg()).contains("referenced by other data");
    }

    @Test
    void mapsMaxUploadSizeExceededToBadRequest() {
        var handler = new ApiGlobalExceptionHandler();

        var response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(1048576));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMsg()).contains("exceeds the configured size limit");
    }
}
