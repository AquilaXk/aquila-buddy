package com.aquilabuddy.global.analysis;

import com.aquilabuddy.application.analysis.IssueGuideService;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues")
public class IssueGuideController {

    private final IssueGuideService issueGuideService;

    public IssueGuideController(IssueGuideService issueGuideService) {
        this.issueGuideService = issueGuideService;
    }

    @GetMapping("/{issueId}/guide")
    ApiResponse<IssueGuide> guide(@PathVariable String issueId) {
        return ApiResponse.success(issueGuideService.findByIssueId(issueId));
    }
}
