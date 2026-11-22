package com.aquilabuddy.application.port.in.analysis;

import com.aquilabuddy.domain.analysis.IssuePseudoCode;

/**
 * [Incoming Port] 이슈 의사코드를 독립 조회하는 유스케이스 계약입니다.
 */
public interface GetIssuePseudoCodeUseCase {

    /**
     * 이슈 식별자를 기준으로 의사코드 projection을 조회합니다.
     */
    IssuePseudoCode findPseudoCodeByIssueId(String issueId);
}
