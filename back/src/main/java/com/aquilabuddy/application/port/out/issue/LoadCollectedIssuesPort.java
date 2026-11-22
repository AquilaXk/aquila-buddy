package com.aquilabuddy.application.port.out.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * [Outgoing Port] 저장된 수집 이슈 catalog를 읽어오는 계약입니다.
 */
public interface LoadCollectedIssuesPort {

    List<RecommendedIssue> loadCollectedIssues();
}
