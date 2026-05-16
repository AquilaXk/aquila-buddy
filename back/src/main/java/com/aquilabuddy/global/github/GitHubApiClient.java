package com.aquilabuddy.global.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubUserProfileResponse.class);

            return Optional.ofNullable(response)
                .map(GitHubUserProfileResponse::toDomain);
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    /**
     * GitHub 사용자의 공개 repository 목록을 조회합니다.
     */
    public List<GitHubRepository> getUserRepos(String username) {
        return getUserRepos(username, 20);
    }

    /**
     * fork를 제외한 공개 repository를 star 내림차순으로 제한해 반환합니다.
     */
    public List<GitHubRepository> getUserRepos(String username, int limit) {
        try {
            GitHubRepositoryResponse[] response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/users/{username}/repos")
                    .queryParam("per_page", 100)
                    .queryParam("sort", "updated")
                    .build(username))
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubRepositoryResponse[].class);

            if (response == null) {
                return List.of();
            }

            return Arrays.stream(response)
                .filter(item -> !item.fork())
                .sorted(Comparator.comparingInt(GitHubRepositoryResponse::stargazersCount).reversed())
                .limit(limit)
                .map(GitHubRepositoryResponse::toDomain)
                .toList();
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    private void applyAuthorization(HttpHeaders headers) {
        if (!token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }

    /**
     * repository 목록에서 주 사용 언어를 빈도 내림차순으로 집계합니다.
     */
    public Map<String, Integer> aggregateLanguages(List<GitHubRepository> repositories) {
        return repositories.stream()
            .map(GitHubRepository::language)
            .filter(language -> language != null && !language.isBlank())
            .collect(Collectors.groupingBy(language -> language, Collectors.summingInt(language -> 1)))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    /**
     * profile vector 입력에 사용할 사용자 기여 context 텍스트를 생성합니다.
     */
    public String buildContributionContextText(
        GitHubUserProfile profile,
        List<GitHubRepository> repositories,
        Map<String, Integer> languages
    ) {
        StringBuilder builder = new StringBuilder("Developer GitHub Profile\n\n");

        if (!languages.isEmpty()) {
            builder.append("Primary Programming Languages: ")
                .append(String.join(", ", languages.keySet()))
                .append("\n");
        }

        if (profile != null && profile.bio() != null && !profile.bio().isBlank()) {
            builder.append("Bio: ")
                .append(profile.bio())
                .append("\n");
        }

        List<GitHubRepository> repositoriesWithContent = repositories.stream()
            .filter(repository -> hasText(repository.description()) || !repository.topics().isEmpty())
            .limit(10)
            .toList();

        if (!repositoriesWithContent.isEmpty()) {
            builder.append("\nRecent Projects:\n");
            repositoriesWithContent.forEach(repository -> builder
                .append("- ")
                .append(repository.name())
                .append(" (")
                .append(repositoryDetail(repository))
                .append(")")
                .append(topicText(repository))
                .append("\n"));
        }

        List<String> topics = repositories.stream()
            .flatMap(repository -> repository.topics().stream())
            .distinct()
            .limit(20)
            .toList();

        if (!topics.isEmpty()) {
            builder.append("\nTechnical Interests: ")
                .append(String.join(", ", topics))
                .append("\n");
        }

        return builder.toString().trim();
    }

    private String repositoryDetail(GitHubRepository repository) {
        return Stream.of(repository.language(), repository.description())
            .filter(this::hasText)
            .toList()
            .stream()
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    private String topicText(GitHubRepository repository) {
        if (repository.topics().isEmpty()) {
            return "";
        }

        return " [" + String.join(", ", repository.topics()) + "]";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record GitHubUserProfile(String login, String bio, int publicRepos) {
    }

    public record GitHubRepository(
        String name,
        String description,
        String language,
        List<String> topics,
        int stars
    ) {
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

    private record GitHubRepositoryResponse(
        String name,
        String description,
        String language,
        List<String> topics,
        @JsonProperty("stargazers_count") int stargazersCount,
        boolean fork
    ) {

        GitHubRepository toDomain() {
            return new GitHubRepository(
                name,
                description,
                language,
                topics == null ? List.of() : List.copyOf(topics),
                stargazersCount
            );
        }
    }
}
