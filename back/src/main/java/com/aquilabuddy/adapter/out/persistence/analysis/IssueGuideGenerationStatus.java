package com.aquilabuddy.adapter.out.persistence.analysis;

/**
 * 이슈 guide 생성 요청 이력의 처리 상태입니다.
 */
public enum IssueGuideGenerationStatus {
    CACHE_HIT,
    ACQUIRED,
    SUCCEEDED,
    FAILED
}
