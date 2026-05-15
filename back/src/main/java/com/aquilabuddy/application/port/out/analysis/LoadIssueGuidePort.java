package com.aquilabuddy.application.port.out.analysis;

import com.aquilabuddy.domain.analysis.IssueGuide;

/**
 * [Outgoing Port] 이슈 작업 가이드 데이터를 외부 저장소에서 읽어오는 계약입니다.
 */
public interface LoadIssueGuidePort {

    /**
     * 이슈 식별자를 기준으로 저장된 작업 가이드를 반환합니다.
     */
    IssueGuide loadIssueGuide(String issueId);
}
