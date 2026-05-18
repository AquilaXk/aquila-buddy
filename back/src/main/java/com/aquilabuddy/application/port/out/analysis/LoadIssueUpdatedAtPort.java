package com.aquilabuddy.application.port.out.analysis;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.time.Instant;
import java.util.Optional;

/**
 * [Outgoing Port] 원격 이슈의 마지막 수정 시각을 조회하는 계약입니다.
 */
public interface LoadIssueUpdatedAtPort {

    Optional<Instant> loadIssueUpdatedAt(RecommendedIssue issue);
}
