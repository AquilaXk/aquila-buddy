package com.aquilabuddy.global.issue;

import com.aquilabuddy.application.issue.RecommendedIssueService;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues")
public class IssueRecommendationController {

    private final RecommendedIssueService recommendedIssueService;

    public IssueRecommendationController(RecommendedIssueService recommendedIssueService) {
        this.recommendedIssueService = recommendedIssueService;
    }

    @GetMapping("/recommend")
    ApiResponse<List<RecommendedIssue>> recommend() {
        return ApiResponse.success(recommendedIssueService.recommend());
    }
}
