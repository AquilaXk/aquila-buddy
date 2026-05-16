package com.aquilabuddy.adapter.out.github.issue;

import com.aquilabuddy.application.port.out.issue.SearchRecommendedIssuesPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * [Outgoing Adapter] GitHub Search API에서 Good First Issue 후보를 수집합니다.
 */
@Component
public class GitHubRecommendedIssueSearchAdapter implements SearchRecommendedIssuesPort {

    private static final String STATUS_OPEN = "OPEN";

    private final RestClient restClient;
    private final String token;
    private final boolean enabled;
    private final String query;
    private final int limit;

    public GitHubRecommendedIssueSearchAdapter(
        RestClient.Builder restClientBuilder,
        @Value("${app.github.api-base-url:https://api.github.com}") String baseUrl,
        @Value("${app.github.token:}") String token,
        @Value("${app.github.issue-search.enabled:false}") boolean enabled,
        @Value("${app.github.issue-search.query:label:\"good first issue\"}") String query,
        @Value("${app.github.issue-search.limit:3}") int limit
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.token = token;
        this.enabled = enabled;
        this.query = query;
        this.limit = limit;
    }

    /**
     * test/local 기본 실행에서는 비활성화되어 외부 네트워크를 호출하지 않습니다.
     */
    @Override
    public List<RecommendedIssue> searchRecommendedIssues() {
        return searchRecommendedIssues(query);
    }

    @Override
    public List<RecommendedIssue> searchRecommendedIssues(String query) {
        if (!enabled) {
            return List.of();
        }

        GitHubIssueSearchResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/search/issues")
                .queryParam("q", normalizedQuery(query))
                .queryParam("per_page", limit)
                .build())
            .headers(headers -> {
                if (!token.isBlank()) {
                    headers.setBearerAuth(token);
                }
            })
            .retrieve()
            .body(GitHubIssueSearchResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items()
            .stream()
            .map(this::toRecommendedIssue)
            .toList();
    }

    private String normalizedQuery(String requestedQuery) {
        String baseQuery = nonBlankOrDefault(requestedQuery, query);
        return baseQuery + " is:issue is:open";
    }

    private RecommendedIssue toRecommendedIssue(GitHubIssueSearchItem item) {
        List<String> labels = item.labels() == null
            ? List.of()
            : item.labels()
                .stream()
                .map(GitHubIssueLabel::name)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        return new RecommendedIssue(
            String.valueOf(item.id()),
            parseRepoFullName(item.repository_url()),
            item.number(),
            nonBlankOrDefault(item.title(), "제목 없음"),
            summary(item.body()),
            1.0,
            labels,
            STATUS_OPEN,
            "GitHub Search에서 Good First Issue 조건으로 수집된 후보입니다."
        );
    }

    private String parseRepoFullName(String repositoryUrl) {
        if (repositoryUrl == null || !repositoryUrl.contains("/repos/")) {
            return "unknown/unknown";
        }
        return repositoryUrl.substring(repositoryUrl.indexOf("/repos/") + "/repos/".length());
    }

    private String summary(String body) {
        String fallback = "GitHub에서 수집한 Good First Issue 후보입니다.";
        String value = nonBlankOrDefault(body, fallback);
        if (value.length() <= 240) {
            return value;
        }
        return value.substring(0, 240);
    }

    private String nonBlankOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private record GitHubIssueSearchResponse(List<GitHubIssueSearchItem> items) {
    }

    private record GitHubIssueSearchItem(
        long id,
        long number,
        String title,
        String body,
        String repository_url,
        List<GitHubIssueLabel> labels
    ) {
    }

    private record GitHubIssueLabel(String name) {
    }
}
