package com.aquilabuddy.domain.repo;

public record GitHubRepository(
    String owner,
    String name,
    String defaultBranch
) {
}
