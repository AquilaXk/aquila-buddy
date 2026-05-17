package com.aquilabuddy.global.exception;

import com.aquilabuddy.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
