package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * [Incoming Port] 저장된 수집 이슈 catalog를 조회하는 유스케이스 계약입니다.
 */
public interface ListCollectedIssuesUseCase {

    List<RecommendedIssue> listCollectedIssues();
}
