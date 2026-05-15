package com.aquilabuddy.adapter.in.web.issue;

import com.aquilabuddy.application.port.in.issue.RecommendIssuesUseCase;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Incoming Web Adapter] 추천 이슈 조회 API 요청을 처리하는 REST controller입니다.
 */
@RestController
@RequestMapping("/api/v1/issues")
public class IssueRecommendationController {

    private final RecommendIssuesUseCase recommendIssuesUseCase;

    public IssueRecommendationController(RecommendIssuesUseCase recommendIssuesUseCase) {
        this.recommendIssuesUseCase = recommendIssuesUseCase;
    }

    /**
     * 현재 사용자에게 우선 추천할 이슈 목록을 공통 응답 포맷으로 반환합니다.
     */
    @GetMapping("/recommend")
    ApiResponse<List<RecommendedIssue>> recommend() {
        return ApiResponse.success(recommendIssuesUseCase.recommend());
    }
}
