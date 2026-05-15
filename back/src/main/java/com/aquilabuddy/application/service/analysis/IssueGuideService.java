package com.aquilabuddy.application.service.analysis;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import org.springframework.stereotype.Service;

@Service
public class IssueGuideService implements GetIssueGuideUseCase {

    private final LoadIssueGuidePort loadIssueGuidePort;

    public IssueGuideService(LoadIssueGuidePort loadIssueGuidePort) {
        this.loadIssueGuidePort = loadIssueGuidePort;
    }

    @Override
    public IssueGuide findByIssueId(String issueId) {
        return loadIssueGuidePort.loadIssueGuide(issueId);
    }
}
