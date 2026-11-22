package com.aquilabuddy.application.port.out.analysis;

/**
 * 같은 이슈에 대한 guide 생성 남용을 막는 outbound port입니다.
 */
public interface IssueGuideGenerationLimitPort {

    /**
     * 이슈 guide 생성 슬롯을 획득하거나 제한 초과 시 예외를 던집니다.
     */
    void acquireGenerationSlot(String issueId);

    /**
     * 저장된 guide를 재사용한 요청 이력을 기록합니다.
     */
    default void recordCacheHit(String issueId) {
    }

    /**
     * 생성과 저장이 완료된 요청 이력을 성공으로 표시합니다.
     */
    default void recordGenerationSucceeded(String issueId) {
    }

    /**
     * 생성 또는 저장 실패 요청 이력을 실패로 표시합니다.
     */
    default void recordGenerationFailed(String issueId, String reason) {
    }
}
