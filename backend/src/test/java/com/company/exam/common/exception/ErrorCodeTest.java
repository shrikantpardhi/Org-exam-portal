package com.company.exam.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    void defaultStatusMappingMatchesTheApiContract() {
        assertThat(ErrorCode.VALIDATION_ERROR.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.AUTHENTICATION_ERROR.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTHORIZATION_ERROR.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.CONFLICT.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.BUSINESS_ERROR.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.INVALID_STATE.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.PRECONDITION_FAILED.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(ErrorCode.RATE_LIMITED.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.SYSTEM_ERROR.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void fromStatusMapsDocumentedCategoriesAndDefaultsToSystemError() {
        assertThat(ErrorCode.fromStatus(400)).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(ErrorCode.fromStatus(401)).isEqualTo(ErrorCode.AUTHENTICATION_ERROR);
        assertThat(ErrorCode.fromStatus(403)).isEqualTo(ErrorCode.AUTHORIZATION_ERROR);
        assertThat(ErrorCode.fromStatus(404)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.fromStatus(409)).isEqualTo(ErrorCode.CONFLICT);
        assertThat(ErrorCode.fromStatus(412)).isEqualTo(ErrorCode.PRECONDITION_FAILED);
        assertThat(ErrorCode.fromStatus(422)).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(ErrorCode.fromStatus(429)).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(ErrorCode.fromStatus(500)).isEqualTo(ErrorCode.SYSTEM_ERROR);
        assertThat(ErrorCode.fromStatus(418)).isEqualTo(ErrorCode.SYSTEM_ERROR);
    }
}
