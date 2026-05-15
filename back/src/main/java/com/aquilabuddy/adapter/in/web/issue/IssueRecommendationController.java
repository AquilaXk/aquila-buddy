package com.aquilabuddy.adapter.in.web.issue;

import com.aquilabuddy.application.port.in.issue.RecommendIssuesUseCase;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues")
public class IssueRecommendationController {

    private final RecommendIssuesUseCase recommendIssuesUseCase;

    public IssueRecommendationController(RecommendIssuesUseCase recommendIssuesUseCase) {
        this.recommendIssuesUseCase = recommendIssuesUseCase;
    }

    @GetMapping("/recommend")
    ApiResponse<List<RecommendedIssue>> recommend() {
        return ApiResponse.success(recommendIssuesUseCase.recommend());
    }
}
