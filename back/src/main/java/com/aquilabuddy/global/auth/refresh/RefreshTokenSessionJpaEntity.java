package com.aquilabuddy.global.auth.refresh;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_token_sessions")
class RefreshTokenSessionJpaEntity {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 64)
    private String familyId;

    @Column(name = "github_id", nullable = false)
    private Long githubId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_hash", length = 64)
    private String replacedByTokenHash;

    protected RefreshTokenSessionJpaEntity() {
    }

    RefreshTokenSessionJpaEntity(
        String tokenHash,
        String familyId,
        Long githubId,
        Instant issuedAt,
        Instant expiresAt
    ) {
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.githubId = githubId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    String getTokenHash() {
        return tokenHash;
    }

    String getFamilyId() {
        return familyId;
    }

    Long getGithubId() {
        return githubId;
    }

    boolean isUsable(Instant now) {
        return rotatedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    boolean isReusedOrRevoked() {
        return rotatedAt != null || revokedAt != null;
    }

    void rotate(String replacementTokenHash, Instant now) {
        this.rotatedAt = now;
        this.replacedByTokenHash = replacementTokenHash;
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
