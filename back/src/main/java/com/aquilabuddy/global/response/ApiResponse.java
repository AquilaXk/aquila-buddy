package com.aquilabuddy.global.response;

/**
 * web adapter에서 공통으로 사용하는 API 응답 envelope입니다.
 */
public record ApiResponse<T>(
    boolean success,
    T data,
    String message
) {
    /**
     * 정상 처리된 요청의 응답 payload를 감싸서 반환합니다.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 실패한 요청의 사용자 노출 메시지를 감싸서 반환합니다.
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
