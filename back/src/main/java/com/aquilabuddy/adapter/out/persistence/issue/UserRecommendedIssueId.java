package com.aquilabuddy.adapter.out.persistence.issue;

import java.io.Serializable;
import java.util.Objects;

/**
 * `user_recommended_issues` 복합 기본키 식별자입니다.
 */
public class UserRecommendedIssueId implements Serializable {

    private Long userId;
    private String issueId;

    protected UserRecommendedIssueId() {
    }

    public UserRecommendedIssueId(Long userId, String issueId) {
        this.userId = userId;
        this.issueId = issueId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserRecommendedIssueId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(issueId, that.issueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, issueId);
    }
}
