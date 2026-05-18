package com.aquilabuddy.application.port.out.analysis;

import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;

/**
 * 저장된 guide가 없을 때 추천 이슈 맥락으로 작업 가이드를 생성하는 outbound port입니다.
 */
public interface GenerateIssueGuidePort {

    /**
     * 추천 이슈 정보를 기반으로 guide를 생성합니다.
     */
    IssueGuide generateIssueGuide(RecommendedIssue issue);

    /**
     * 추천 이슈와 관련 저장소 코드 맥락을 함께 사용해 guide를 생성합니다.
     */
    default IssueGuide generateIssueGuide(RecommendedIssue issue, RepositoryCodeContext codeContext) {
        return generateIssueGuide(issue);
    }
}
