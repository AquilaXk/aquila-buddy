package com.aquilabuddy.global.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * GitHub REST API에서 사용자 공개 context를 수집하는 client입니다.
 */
@Component
public class GitHubApiClient {

    private final RestClient restClient;
    private final String token;

    public GitHubApiClient(
        RestClient.Builder restClientBuilder,
        @Value("${app.github.api-base-url:https://api.github.com}") String baseUrl,
        @Value("${app.github.token:}") String token
    ) {
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
        this.token = token;
    }

    /**
     * GitHub 사용자의 공개 프로필 정보를 조회합니다.
     */
    public Optional<GitHubUserProfile> getUserProfile(String username) {
        try {
            GitHubUserProfileResponse response = restClient.get()
                .uri("/users/{username}", username)
                .headers(headers -> {
                    if (!token.isBlank()) {
                        headers.setBearerAuth(token);
                    }
                })
                .retrieve()
                .body(GitHubUserProfileResponse.class);

            return Optional.ofNullable(response)
                .map(GitHubUserProfileResponse::toDomain);
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    public record GitHubUserProfile(String login, String bio, int publicRepos) {
    }

    private record GitHubUserProfileResponse(
        String login,
        String bio,
        @JsonProperty("public_repos") int publicRepos
    ) {

        GitHubUserProfile toDomain() {
            return new GitHubUserProfile(login, bio, publicRepos);
        }
    }
}
