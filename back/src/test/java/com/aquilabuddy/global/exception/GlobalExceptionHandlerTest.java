package com.aquilabuddy.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.analysis.IssueGuideGenerationDailyLimitExceededException;
import com.aquilabuddy.global.response.ApiResponse;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void convertsResponseStatusExceptionToFailureEnvelope() {
        ResponseStatusException exception = new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "PR 초안을 찾을 수 없습니다."
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatus(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().status()).isEqualTo("fail");
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().message()).isEqualTo("PR 초안을 찾을 수 없습니다.");
    }

    @Test
    void convertsIssueGuideDailyLimitToTooManyRequestsEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIssueGuideDailyLimit(
            new IssueGuideGenerationDailyLimitExceededException(10001L)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().status()).isEqualTo("fail");
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().message())
            .isEqualTo("이슈 분석 일일 생성 제한을 초과했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void convertsIllegalArgumentAndNoSuchElementToConsistentFailureEnvelopes() {
        ResponseEntity<ApiResponse<Void>> badRequest = handler.handleIllegalArgument(
            new IllegalArgumentException("입력 길이가 허용 범위를 초과했습니다.")
        );
        ResponseEntity<ApiResponse<Void>> notFound = handler.handleNoSuchElement(
            new NoSuchElementException("Issue guide not found: missing-id")
        );

        assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badRequest.getBody()).isNotNull();
        assertThat(badRequest.getBody().success()).isFalse();
        assertThat(badRequest.getBody().message()).contains("입력 길이");

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).isNotNull();
        assertThat(notFound.getBody().success()).isFalse();
        assertThat(notFound.getBody().message()).contains("missing-id");
    }
}
