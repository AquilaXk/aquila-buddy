package com.aquilabuddy.application.port.out.analysis;

import com.aquilabuddy.domain.analysis.IssueGuide;

public interface LoadIssueGuidePort {

    IssueGuide loadIssueGuide(String issueId);
}
