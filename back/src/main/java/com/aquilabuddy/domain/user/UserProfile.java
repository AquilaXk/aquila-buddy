package com.aquilabuddy.domain.user;

import java.util.List;

public record UserProfile(
    long githubId,
    String login,
    String name,
    String email,
    List<String> primaryLanguages,
    List<String> topics,
    boolean profileReady,
    String vectorUpdatedAt
) {
}
