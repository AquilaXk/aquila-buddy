package com.aquilabuddy.adapter.out.persistence.prdraft;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 저장된 PR 초안의 JPA 전용 모델입니다.
 */
@Entity
@Table(name = "pr_drafts")
public class PullRequestDraftJpaEntity {

    @Id
    @Column(name = "draft_id", nullable = false, length = 100)
    private String id;

    @Column(name = "owner_github_id", nullable = false)
    private long ownerGithubId;

    @Column(name = "upstream_repo", nullable = false)
    private String upstreamRepo;

    @Column(name = "github_issue_number", nullable = false)
    private long githubIssueNumber;

    @Column(name = "source_issue_id", length = 100)
    private String sourceIssueId;

    @Column(name = "issue_title", length = 500)
    private String issueTitle;

    @Column(name = "base_branch", nullable = false, length = 255)
    private String baseBranch;

    @Column(name = "head_branch", nullable = false, length = 255)
    private String headBranch;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "body", nullable = false, length = 8_000)
    private String body;

    @Column(name = "diff_content", length = 12_000)
    private String diffContent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PullRequestDraftJpaEntity() {
    }

    PullRequestDraftJpaEntity(
        String id,
        String upstreamRepo,
        long githubIssueNumber,
        String baseBranch,
        String headBranch,
        String title,
        String body,
        Instant createdAt
    ) {
        this(id, 10001L, upstreamRepo, githubIssueNumber, null, null, baseBranch, headBranch, title, body, null, createdAt);
    }

    PullRequestDraftJpaEntity(
        String id,
        long ownerGithubId,
        String upstreamRepo,
        long githubIssueNumber,
        String baseBranch,
        String headBranch,
        String title,
        String body,
        Instant createdAt
    ) {
        this(id, ownerGithubId, upstreamRepo, githubIssueNumber, null, null, baseBranch, headBranch, title, body, null, createdAt);
    }

    PullRequestDraftJpaEntity(
        String id,
        String upstreamRepo,
        long githubIssueNumber,
        String sourceIssueId,
        String issueTitle,
        String baseBranch,
        String headBranch,
        String title,
        String body,
        String diffContent,
        Instant createdAt
    ) {
        this(id, 10001L, upstreamRepo, githubIssueNumber, sourceIssueId, issueTitle, baseBranch, headBranch, title, body, diffContent, createdAt);
    }

    PullRequestDraftJpaEntity(
        String id,
        long ownerGithubId,
        String upstreamRepo,
        long githubIssueNumber,
        String sourceIssueId,
        String issueTitle,
        String baseBranch,
        String headBranch,
        String title,
        String body,
        String diffContent,
        Instant createdAt
    ) {
        this.id = id;
        this.ownerGithubId = ownerGithubId;
        this.upstreamRepo = upstreamRepo;
        this.githubIssueNumber = githubIssueNumber;
        this.sourceIssueId = sourceIssueId;
        this.issueTitle = issueTitle;
        this.baseBranch = baseBranch;
        this.headBranch = headBranch;
        this.title = title;
        this.body = body;
        this.diffContent = diffContent;
        this.createdAt = createdAt;
    }

    String getId() {
        return id;
    }

    long getOwnerGithubId() {
        return ownerGithubId;
    }

    String getUpstreamRepo() {
        return upstreamRepo;
    }

    long getGithubIssueNumber() {
        return githubIssueNumber;
    }

    String getSourceIssueId() {
        return sourceIssueId;
    }

    String getIssueTitle() {
        return issueTitle;
    }

    String getBaseBranch() {
        return baseBranch;
    }

    String getHeadBranch() {
        return headBranch;
    }

    String getTitle() {
        return title;
    }

    String getBody() {
        return body;
    }

    String getDiffContent() {
        return diffContent;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void updateDraft(String title, String body) {
        this.title = title;
        this.body = body;
    }
}
