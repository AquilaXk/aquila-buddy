package com.aquilabuddy.domain.prdraft;

import java.util.Objects;

/**
 * PR 초안 생성에 필요한 저장소, 이슈, 브랜치, diff 정보를 담는 도메인 요청 모델입니다.
 */
public record PullRequestDraftRequest(
    String upstreamRepo,
    long githubIssueNumber,
    String baseBranch,
    String headBranch,
    String diffContent,
    PullRequestDraftRepositoryGuideContext repositoryGuideContext,
    PullRequestDraftIssueContext issueContext,
    PullRequestDraftAnalysisContext analysisContext
) {

    public PullRequestDraftRequest(
        String upstreamRepo,
        long githubIssueNumber,
        String baseBranch,
        String headBranch,
        String diffContent
    ) {
        this(upstreamRepo, githubIssueNumber, baseBranch, headBranch, diffContent,
            PullRequestDraftRepositoryGuideContext.empty(),
            PullRequestDraftIssueContext.empty(),
            PullRequestDraftAnalysisContext.empty());
    }

    public PullRequestDraftRequest(
        String upstreamRepo,
        long githubIssueNumber,
        String baseBranch,
        String headBranch,
        String diffContent,
        PullRequestDraftRepositoryGuideContext repositoryGuideContext
    ) {
        this(upstreamRepo, githubIssueNumber, baseBranch, headBranch, diffContent, repositoryGuideContext,
            PullRequestDraftIssueContext.empty(),
            PullRequestDraftAnalysisContext.empty());
    }

    public PullRequestDraftRequest(
        String upstreamRepo,
        long githubIssueNumber,
        String baseBranch,
        String headBranch,
        String diffContent,
        PullRequestDraftRepositoryGuideContext repositoryGuideContext,
        PullRequestDraftIssueContext issueContext
    ) {
        this(upstreamRepo, githubIssueNumber, baseBranch, headBranch, diffContent, repositoryGuideContext,
            issueContext,
            PullRequestDraftAnalysisContext.empty());
    }

    /**
     * 화면/adapter에서 넘어온 문자열 입력을 domain 경계에서 한 번 더 정규화합니다.
     */
    public PullRequestDraftRequest {
        upstreamRepo = requireText(upstreamRepo, "upstreamRepo");
        baseBranch = requireText(baseBranch, "baseBranch");
        headBranch = requireText(headBranch, "headBranch");
        diffContent = optionalText(diffContent);
        repositoryGuideContext = repositoryGuideContext == null
            ? PullRequestDraftRepositoryGuideContext.empty()
            : repositoryGuideContext;
        issueContext = issueContext == null ? PullRequestDraftIssueContext.empty() : issueContext;
        analysisContext = analysisContext == null ? PullRequestDraftAnalysisContext.empty() : analysisContext;

        if (githubIssueNumber < 1) {
            throw new IllegalArgumentException("githubIssueNumber must be positive.");
        }
    }

    public boolean hasDiffContent() {
        return diffContent != null;
    }

    public PullRequestDraftRequest withDiffContent(String resolvedDiffContent) {
        return new PullRequestDraftRequest(
            upstreamRepo,
            githubIssueNumber,
            baseBranch,
            headBranch,
            requireText(resolvedDiffContent, "diffContent"),
            repositoryGuideContext,
            issueContext,
            analysisContext
        );
    }

    public PullRequestDraftRequest withRepositoryGuideContext(
        PullRequestDraftRepositoryGuideContext resolvedRepositoryGuideContext
    ) {
        return new PullRequestDraftRequest(
            upstreamRepo,
            githubIssueNumber,
            baseBranch,
            headBranch,
            diffContent,
            resolvedRepositoryGuideContext,
            issueContext,
            analysisContext
        );
    }

    public PullRequestDraftRequest withIssueContext(PullRequestDraftIssueContext resolvedIssueContext) {
        return new PullRequestDraftRequest(
            upstreamRepo,
            githubIssueNumber,
            baseBranch,
            headBranch,
            diffContent,
            repositoryGuideContext,
            resolvedIssueContext,
            analysisContext
        );
    }

    public PullRequestDraftRequest withAnalysisContext(PullRequestDraftAnalysisContext resolvedAnalysisContext) {
        return new PullRequestDraftRequest(
            upstreamRepo,
            githubIssueNumber,
            baseBranch,
            headBranch,
            diffContent,
            repositoryGuideContext,
            issueContext,
            resolvedAnalysisContext
        );
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
