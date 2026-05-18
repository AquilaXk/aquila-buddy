package com.aquilabuddy.domain.embedding;

import java.time.Instant;
import java.util.Objects;

/**
 * 운영 backfill 호출의 실행자, 제한값, 결과를 secret 없이 남기는 감사 이벤트입니다.
 */
public record EmbeddingBackfillAuditEvent(
    EmbeddingBackfillAuditOutcome outcome,
    String operator,
    int requestedLimit,
    int effectiveLimit,
    int profileUpdated,
    int issueUpdated,
    int failed,
    String reason,
    Instant occurredAt
) {

    public EmbeddingBackfillAuditEvent {
        Objects.requireNonNull(outcome, "outcome must not be null.");
        operator = normalize(operator, "unknown");
        requireNonNegative(requestedLimit, "requestedLimit");
        requireNonNegative(effectiveLimit, "effectiveLimit");
        requireNonNegative(profileUpdated, "profileUpdated");
        requireNonNegative(issueUpdated, "issueUpdated");
        requireNonNegative(failed, "failed");
        reason = normalize(reason, "");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null.");
    }

    public static EmbeddingBackfillAuditEvent succeeded(
        String operator,
        int requestedLimit,
        int effectiveLimit,
        EmbeddingBackfillSummary summary,
        Instant occurredAt
    ) {
        return new EmbeddingBackfillAuditEvent(
            EmbeddingBackfillAuditOutcome.SUCCEEDED,
            operator,
            requestedLimit,
            effectiveLimit,
            summary.profiles().updated(),
            summary.issues().updated(),
            summary.profiles().failed() + summary.issues().failed(),
            "",
            occurredAt
        );
    }

    public static EmbeddingBackfillAuditEvent denied(
        String operator,
        int requestedLimit,
        String reason,
        Instant occurredAt
    ) {
        return new EmbeddingBackfillAuditEvent(
            EmbeddingBackfillAuditOutcome.DENIED,
            operator,
            requestedLimit,
            0,
            0,
            0,
            0,
            reason,
            occurredAt
        );
    }

    public static EmbeddingBackfillAuditEvent failed(
        String operator,
        int requestedLimit,
        int effectiveLimit,
        Throwable error,
        Instant occurredAt
    ) {
        return new EmbeddingBackfillAuditEvent(
            EmbeddingBackfillAuditOutcome.FAILED,
            operator,
            requestedLimit,
            effectiveLimit,
            0,
            0,
            1,
            failureReason(error),
            occurredAt
        );
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.trim().isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static String failureReason(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + message.trim();
    }
}
