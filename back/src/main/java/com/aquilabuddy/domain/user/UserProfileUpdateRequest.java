package com.aquilabuddy.domain.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 현재 사용자 프로필을 추천 맥락에 맞게 갱신하기 위한 도메인 요청 모델입니다.
 */
public record UserProfileUpdateRequest(
    String name,
    String email,
    List<String> primaryLanguages,
    List<String> topics
) {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_PRIMARY_LANGUAGES = 30;
    private static final int MAX_TOPICS = 50;
    private static final int MAX_TAG_LENGTH = 100;

    public UserProfileUpdateRequest {
        name = requireBoundedText(name, "name", MAX_NAME_LENGTH);
        email = requireBoundedText(email, "email", MAX_EMAIL_LENGTH);
        primaryLanguages = normalizeBoundedTags(
            Objects.requireNonNull(primaryLanguages, "primaryLanguages must not be null"),
            "primaryLanguages",
            MAX_PRIMARY_LANGUAGES
        );
        topics = normalizeBoundedTags(
            Objects.requireNonNull(topics, "topics must not be null"),
            "topics",
            MAX_TOPICS
        );
    }

    private static String requireBoundedText(String value, String fieldName, int maxLength) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds max length of " + maxLength + ".");
        }
        return trimmed;
    }

    private static List<String> normalizeBoundedTags(List<String> values, String fieldName, int maxItems) {
        if (values.size() > maxItems) {
            throw new IllegalArgumentException(fieldName + " exceeds max count of " + maxItems + ".");
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String item : values) {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException(fieldName + " items must not be blank.");
            }
            String trimmed = item.trim();
            if (trimmed.length() > MAX_TAG_LENGTH) {
                throw new IllegalArgumentException(fieldName + " item exceeds max length of " + MAX_TAG_LENGTH + ".");
            }
            normalized.add(trimmed);
        }
        return List.copyOf(normalized);
    }
}
