package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.Optional;

/**
 * [Incoming Port] GitHub 이슈 식별자로 저장된 추천 이슈를 조회하는 use case 계약입니다.
 */
public interface LookupRecommendedIssueUseCase {

    /**
     * repo full name과 issue number에 해당하는 추천 이슈를 조회합니다.
     */
    Optional<RecommendedIssue> lookup(String repoFullName, long issueNumber);
}
