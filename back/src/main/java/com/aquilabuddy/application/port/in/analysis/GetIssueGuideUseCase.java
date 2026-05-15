package com.aquilabuddy.application.port.in.analysis;

import com.aquilabuddy.domain.analysis.IssueGuide;

public interface GetIssueGuideUseCase {

    IssueGuide findByIssueId(String issueId);
}
