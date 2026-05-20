package com.aquilabuddy.domain.user;

import java.util.Objects;

/**
 * OAuth 인증 성공 시 저장소와 동기화할 GitHub 사용자 식별 정보입니다.
 */
public record AuthenticatedUserProfile(
    long githubId,
    String login,
    String name,
    String email,
    String avatarUrl
) {

    public AuthenticatedUserProfile(long githubId, String login, String name, String email) {
        this(githubId, login, name, email, null);
    }

    public AuthenticatedUserProfile {
        login = requireNonBlank(login, "login");
        name = requireNonBlank(name, "name");
        email = requireNonBlank(email, "email");
        avatarUrl = normalizeOptional(avatarUrl);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
