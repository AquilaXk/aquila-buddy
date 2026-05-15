package com.aquilabuddy.adapter.in.web.issue;

import com.aquilabuddy.application.port.in.issue.GetRecommendedIssueHistoryUseCase;
import com.aquilabuddy.application.port.in.issue.LookupRecommendedIssueUseCase;
import com.aquilabuddy.application.port.in.issue.RecommendIssuesUseCase;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueHistory;
import com.aquilabuddy.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * [Incoming Web Adapter] 추천 이슈 조회 API 요청을 처리하는 REST controller입니다.
 */
@RestController
@RequestMapping("/api/v1/issues")
public class IssueRecommendationController {

    private final RecommendIssuesUseCase recommendIssuesUseCase;
    private final GetRecommendedIssueHistoryUseCase getRecommendedIssueHistoryUseCase;
    private final LookupRecommendedIssueUseCase lookupRecommendedIssueUseCase;

    public IssueRecommendationController(
        RecommendIssuesUseCase recommendIssuesUseCase,
        GetRecommendedIssueHistoryUseCase getRecommendedIssueHistoryUseCase,
        LookupRecommendedIssueUseCase lookupRecommendedIssueUseCase
    ) {
        this.recommendIssuesUseCase = recommendIssuesUseCase;
        this.getRecommendedIssueHistoryUseCase = getRecommendedIssueHistoryUseCase;
        this.lookupRecommendedIssueUseCase = lookupRecommendedIssueUseCase;
    }

    /**
     * 현재 사용자에게 우선 추천할 이슈 목록을 공통 응답 포맷으로 반환합니다.
     */
    @GetMapping("/recommend")
    ApiResponse<List<RecommendedIssue>> recommend() {
        return ApiResponse.success(recommendIssuesUseCase.recommend());
    }

    /**
     * 저장된 추천 이슈를 이력 응답으로 반환합니다.
     */
    @GetMapping("/recommend/history")
    ApiResponse<List<RecommendedIssueHistory>> history() {
        return ApiResponse.success(getRecommendedIssueHistoryUseCase.history());
    }

    /**
     * GitHub repo full name과 issue number로 저장된 추천 이슈를 조회합니다.
     */
    @GetMapping("/lookup")
    ApiResponse<RecommendedIssue> lookup(
        @RequestParam("repo") String repoFullName,
        @RequestParam("number") long issueNumber
    ) {
        RecommendedIssue issue = lookupRecommendedIssueUseCase.lookup(repoFullName, issueNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "추천 이슈를 찾을 수 없습니다."));

        return ApiResponse.success(issue);
    }
}
