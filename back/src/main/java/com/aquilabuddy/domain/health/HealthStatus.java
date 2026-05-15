package com.aquilabuddy.domain.health;

/**
 * 헬스 체크 API가 반환하는 서비스 상태 도메인 모델입니다.
 */
public record HealthStatus(
    String status,
    String service
) {
}
