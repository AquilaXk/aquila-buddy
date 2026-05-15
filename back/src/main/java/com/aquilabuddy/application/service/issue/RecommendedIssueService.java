package com.aquilabuddy.application.service.issue;

import com.aquilabuddy.application.port.in.issue.RecommendIssuesUseCase;
import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecommendedIssueService implements RecommendIssuesUseCase {

    private final LoadRecommendedIssuesPort loadRecommendedIssuesPort;

    public RecommendedIssueService(LoadRecommendedIssuesPort loadRecommendedIssuesPort) {
        this.loadRecommendedIssuesPort = loadRecommendedIssuesPort;
    }

    @Override
    public List<RecommendedIssue> recommend() {
        return loadRecommendedIssuesPort.loadRecommendedIssues();
    }
}
