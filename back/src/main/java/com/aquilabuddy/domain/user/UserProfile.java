package com.aquilabuddy.domain.user;

import java.util.List;

public record UserProfile(
    long githubId,
    String login,
    String name,
    List<String> languages
) {
}
