package com.aquilabuddy.application.port.in.analysis;

import com.aquilabuddy.domain.analysis.IssueGuide;

/**
 * [Incoming Port] 이슈 작업 가이드를 조회하는 유스케이스 계약입니다.
 */
public interface GetIssueGuideUseCase {

    /**
     * 이슈 식별자를 기준으로 구현 가이드와 영향 범위를 조회합니다.
     */
    IssueGuide findByIssueId(String issueId);
}
