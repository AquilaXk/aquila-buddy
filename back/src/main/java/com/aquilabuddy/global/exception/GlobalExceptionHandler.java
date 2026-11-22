package com.aquilabuddy.global.exception;

import com.aquilabuddy.domain.analysis.IssueGuideGenerationDailyLimitExceededException;
import com.aquilabuddy.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * web adapter에서 발생한 검증 예외를 공통 API 응답 포맷으로 변환합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * request body 바인딩 검증 실패 중 첫 번째 필드 오류를 클라이언트 메시지로 반환합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("요청 값이 올바르지 않습니다.");

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(message));
    }

    /**
     * path/query parameter 제약 조건 위반을 BAD_REQUEST 응답으로 변환합니다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(exception.getMessage()));
    }

    /**
     * controller/use case가 지정한 HTTP status를 유지하면서 실패 응답 envelope로 변환합니다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        return ResponseEntity
            .status(exception.getStatusCode())
            .body(ApiResponse.error(exception.getReason()));
    }

    /**
     * 사용자별 이슈 분석 일일 생성 제한을 안정적인 429 응답으로 변환합니다.
     */
    @ExceptionHandler(IssueGuideGenerationDailyLimitExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleIssueGuideDailyLimit(
        IssueGuideGenerationDailyLimitExceededException exception
    ) {
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ApiResponse.error(exception.getMessage()));
    }

    /**
     * 도메인 경계의 잘못된 입력을 BAD_REQUEST 응답으로 변환합니다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(exception.getMessage()));
    }

    /**
     * 리소스 미존재 예외를 NOT_FOUND 응답으로 변환합니다.
     */
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiResponse<Void>> handleNoSuchElement(NoSuchElementException exception) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(exception.getMessage()));
    }

    /**
     * 인증 정보 누락/실패 예외를 UNAUTHORIZED 응답으로 변환합니다.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(exception.getMessage()));
    }
}
