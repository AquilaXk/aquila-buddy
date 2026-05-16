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

    @Column(name = "upstream_repo", nullable = false)
    private String upstreamRepo;

    @Column(name = "github_issue_number", nullable = false)
    private long githubIssueNumber;

    @Column(name = "base_branch", nullable = false, length = 255)
    private String baseBranch;

    @Column(name = "head_branch", nullable = false, length = 255)
    private String headBranch;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "body", nullable = false, length = 8_000)
    private String body;

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
        this.id = id;
        this.upstreamRepo = upstreamRepo;
        this.githubIssueNumber = githubIssueNumber;
        this.baseBranch = baseBranch;
        this.headBranch = headBranch;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
    }

    String getId() {
        return id;
    }

    String getUpstreamRepo() {
        return upstreamRepo;
    }

    long getGithubIssueNumber() {
        return githubIssueNumber;
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

    Instant getCreatedAt() {
        return createdAt;
    }

    void updateDraft(String title, String body) {
        this.title = title;
        this.body = body;
    }
}
