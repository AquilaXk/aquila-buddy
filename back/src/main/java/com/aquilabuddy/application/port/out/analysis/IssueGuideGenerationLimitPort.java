package com.aquilabuddy.application.port.out.analysis;

/**
 * 같은 이슈에 대한 guide 생성 남용을 막는 outbound port입니다.
 */
public interface IssueGuideGenerationLimitPort {

    /**
     * 이슈 guide 생성 슬롯을 획득하거나 제한 초과 시 예외를 던집니다.
     */
    void acquireGenerationSlot(String issueId);
}
