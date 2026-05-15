package com.aquilabuddy.adapter.in.web.prdraft;

import com.aquilabuddy.application.port.in.prdraft.CreatePullRequestDraftUseCase;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Incoming Web Adapter] PR 초안 생성 API 요청을 처리하는 REST controller입니다.
 */
@RestController
@RequestMapping("/api/v1/pr")
public class PullRequestDraftController {

    private final CreatePullRequestDraftUseCase createPullRequestDraftUseCase;

    public PullRequestDraftController(CreatePullRequestDraftUseCase createPullRequestDraftUseCase) {
        this.createPullRequestDraftUseCase = createPullRequestDraftUseCase;
    }

    /**
     * 저장소/이슈/브랜치/diff 입력값을 받아 PR 제목과 본문 초안을 반환합니다.
     */
    @PostMapping
    ApiResponse<PullRequestDraft> create(@Valid @RequestBody CreatePullRequestDraftRequest request) {
        return ApiResponse.success(createPullRequestDraftUseCase.create(request.toDomain()));
    }

    /**
     * web 요청 body를 domain 요청 모델로 변환하기 전 검증하는 DTO입니다.
     */
    record CreatePullRequestDraftRequest(
        @NotBlank(message = "upstreamRepo는 비어 있을 수 없습니다.")
        String upstreamRepo,
        @Positive(message = "githubIssueNumber는 1 이상이어야 합니다.")
        long githubIssueNumber,
        @NotBlank(message = "baseBranch는 비어 있을 수 없습니다.")
        String baseBranch,
        @NotBlank(message = "headBranch는 비어 있을 수 없습니다.")
        String headBranch,
        @NotBlank(message = "diffContent는 비어 있을 수 없습니다.")
        String diffContent
    ) {

        PullRequestDraftRequest toDomain() {
            return new PullRequestDraftRequest(
                upstreamRepo,
                githubIssueNumber,
                baseBranch,
                headBranch,
                diffContent
            );
        }
    }
}
