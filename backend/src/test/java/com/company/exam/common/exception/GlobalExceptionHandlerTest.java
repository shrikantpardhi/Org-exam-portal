package com.company.exam.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.api.ApiErrorResponse;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void controlledExceptionKeepsItsStatusMessageAndPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/questions/1");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleApiException(new NotFoundException("Question not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("Question not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/questions/1");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void controlledExceptionCarriesFieldErrors() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/questions");
        ValidationException exception = new ValidationException("Validation failed",
                List.of(ApiError.of("marks", "INVALID_VALUE", "Marks must be positive")));

        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("marks");
    }

    @Test
    void beanValidationFailureIsReportedAsBadRequestWithFieldErrors() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/questions");
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "title", "must not be blank"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter(), binding);

        ResponseEntity<ApiErrorResponse> response = handler.handleMethodArgumentNotValid(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("title");
        assertThat(response.getBody().errors().get(0).message()).isEqualTo("must not be blank");
    }

    @Test
    void unknownRouteIsReportedAsNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/does-not-exist");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleNoResource(new NoResourceFoundException(org.springframework.http.HttpMethod.GET,
                        "/api/v1/does-not-exist", "No static resource"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Resource not found");
    }

    @Test
    void frameworkClientErrorKeepsItsHttpStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/system/info");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(new HttpRequestMethodNotSupportedException("PATCH"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void unexpectedExceptionBecomesGenericServerErrorWithoutLeakingInternals() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/info");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:postgresql://db-internal:5432/exam?password=leak"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().errors()).isNull();
        assertThat(response.getBody().message()).doesNotContain("password");
    }

    private MethodParameter methodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
        // Exists only to provide a MethodParameter for the validation test.
    }
}
