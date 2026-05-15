package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

public interface RecommendIssuesUseCase {

    List<RecommendedIssue> recommend();
}
