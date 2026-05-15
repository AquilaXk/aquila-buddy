package com.aquilabuddy.adapter.in.web.analysis;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Incoming Web Adapter] 특정 이슈의 작업 가이드 조회 API를 처리하는 REST controller입니다.
 */
@RestController
@RequestMapping("/api/v1/issues")
public class IssueGuideController {

    private final GetIssueGuideUseCase getIssueGuideUseCase;

    public IssueGuideController(GetIssueGuideUseCase getIssueGuideUseCase) {
        this.getIssueGuideUseCase = getIssueGuideUseCase;
    }

    /**
     * 이슈 식별자에 해당하는 작업 가이드를 공통 응답 포맷으로 반환합니다.
     */
    @GetMapping("/{issueId}/guide")
    ApiResponse<IssueGuide> guide(@PathVariable String issueId) {
        return ApiResponse.success(getIssueGuideUseCase.findByIssueId(issueId));
    }
}
