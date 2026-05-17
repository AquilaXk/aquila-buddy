package com.aquilabuddy.application.port.out.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.Optional;

/**
 * [Outgoing Port] 저장된 추천 이슈를 GitHub 이슈 식별자로 조회하는 계약입니다.
 */
public interface FindRecommendedIssuePort {

    /**
     * 내부 이슈 ID에 해당하는 추천 이슈를 반환합니다.
     */
    Optional<RecommendedIssue> findById(String id);

    /**
     * repo full name과 issue number가 일치하는 추천 이슈를 반환합니다.
     */
    Optional<RecommendedIssue> findByRepoFullNameAndIssueNumber(String repoFullName, long issueNumber);
}
