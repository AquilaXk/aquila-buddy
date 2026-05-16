package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftContributionReport;
import java.util.Optional;

/**
 * PR 상세 화면의 제출 전 report를 조회하는 inbound use case입니다.
 */
public interface GetPullRequestDraftContributionReportUseCase {

    Optional<PullRequestDraftContributionReport> getContributionReport(String id);
}
