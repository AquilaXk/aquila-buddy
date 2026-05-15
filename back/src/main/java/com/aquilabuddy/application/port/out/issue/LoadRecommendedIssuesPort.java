package com.aquilabuddy.application.port.out.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

public interface LoadRecommendedIssuesPort {

    List<RecommendedIssue> loadRecommendedIssues();
}
