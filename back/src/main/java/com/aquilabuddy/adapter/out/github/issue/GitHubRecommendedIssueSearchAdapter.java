package com.aquilabuddy.adapter.out.github.issue;

import com.aquilabuddy.application.port.out.issue.SearchRecommendedIssuesPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * [Outgoing Adapter] GitHub Search API 및 GraphQL API에서 Good First Issue 후보를 수집합니다.
 */
@Component
public class GitHubRecommendedIssueSearchAdapter implements SearchRecommendedIssuesPort {

    private static final String STATUS_OPEN = "OPEN";
    private static final double SEARCH_CANDIDATE_SCORE = 0.0;

    private static final String GRAPHQL_SEARCH_QUERY = """
        query SearchGoodFirstIssues($q: String!, $first: Int!) {
          search(query: $q, type: ISSUE, first: $first) {
            issueCount
            nodes {
              ... on Issue {
                id
                number
                title
                body
                url
                state
                assignees(first: 1) {
                  totalCount
                }
                labels(first: 10) {
                  nodes {
                    name
                  }
                }
                repository {
                  nameWithOwner
                  stargazerCount
                  defaultBranchRef {
                    target {
                      ... on Commit {
                        committedDate
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """;

    private final RestClient restClient;
    private final String token;
    private final boolean enabled;
    private final String query;
    private final int limit;
    private final boolean graphqlEnabled;

    @Autowired
    public GitHubRecommendedIssueSearchAdapter(
        RestClient.Builder restClientBuilder,
        @Value("${app.github.api-base-url:https://api.github.com}") String baseUrl,
        @Value("${app.github.token:}") String token,
        @Value("${app.github.issue-search.enabled:false}") boolean enabled,
        @Value("${app.github.issue-search.query:label:\"good first issue\"}") String query,
        @Value("${app.github.issue-search.limit:3}") int limit,
        @Value("${app.github.issue-search.graphql-enabled:true}") boolean graphqlEnabled
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.token = token;
        this.enabled = enabled;
        this.query = query;
        this.limit = limit;
        this.graphqlEnabled = graphqlEnabled;
    }

    public GitHubRecommendedIssueSearchAdapter(
        RestClient.Builder restClientBuilder,
        String baseUrl,
        String token,
        boolean enabled,
        String query,
        int limit
    ) {
        this(restClientBuilder, baseUrl, token, enabled, query, limit, true);
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

        try {
            return searchRecommendedIssues(query, token);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 && !token.isBlank()) {
                return searchRecommendedIssuesWithoutToken(query);
            }
            return List.of();
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    private List<RecommendedIssue> searchRecommendedIssuesWithoutToken(String query) {
        try {
            // 운영 token이 만료/폐기된 경우 공개 Search API로 한 번 더 시도해 사용자 인증 401 전파를 막습니다.
            return searchRecommendedIssues(query, "");
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    private List<RecommendedIssue> searchRecommendedIssues(String query, String bearerToken) {
        // GraphQL 쿼리가 활성화되어 있고 인증 토큰이 있는 경우 REST 레이트 리밋을 극복하기 위해 GraphQL을 우선 시도합니다.
        if (graphqlEnabled && !bearerToken.isBlank()) {
            try {
                List<RecommendedIssue> graphQlIssues = searchViaGraphQL(query, bearerToken);
                if (!graphQlIssues.isEmpty()) {
                    return graphQlIssues;
                }
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() == 401) {
                    throw exception;
                }
                // GraphQL 엔드포인트 미구현(404) 또는 기타 HTTP 오류 시 REST Search API로 graceful fallback 합니다.
            } catch (Exception ignored) {
                // GraphQL 호출 장애 또는 응답 파싱 실패 시 REST Search API로 graceful fallback 합니다.
            }
        }
        return searchViaRest(query, bearerToken);
    }

    private List<RecommendedIssue> searchViaGraphQL(String query, String bearerToken) {
        GraphQLRequest requestBody = new GraphQLRequest(
            GRAPHQL_SEARCH_QUERY,
            Map.of(
                "q", normalizedGraphQLQuery(query),
                "first", Math.max(limit * 2, 10)
            )
        );

        GraphQLResponse response = restClient.post()
            .uri("/graphql")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(GraphQLResponse.class);

        if (response == null || response.data() == null || response.data().search() == null || response.data().search().nodes() == null) {
            return List.of();
        }

        return response.data().search().nodes().stream()
            .filter(Objects::nonNull)
            .filter(node -> node.number() != null && node.title() != null)
            .filter(this::isUnassigned)
            .map(this::toRecommendedIssueFromGraphQL)
            .limit(limit)
            .toList();
    }

    private boolean isUnassigned(GraphQLIssueNode node) {
        return node.assignees() == null
            || node.assignees().totalCount() == null
            || node.assignees().totalCount() == 0;
    }

    private List<RecommendedIssue> searchViaRest(String query, String bearerToken) {
        GitHubIssueSearchResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/search/issues")
                .queryParam("q", normalizedQuery(query))
                .queryParam("per_page", limit)
                .build())
            .headers(headers -> {
                if (!bearerToken.isBlank()) {
                    headers.setBearerAuth(bearerToken);
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

    private String normalizedGraphQLQuery(String requestedQuery) {
        String baseQuery = nonBlankOrDefault(requestedQuery, query);
        String normalized = baseQuery;
        if (!normalized.contains("is:issue")) {
            normalized += " is:issue";
        }
        if (!normalized.contains("is:open") && !normalized.contains("state:open")) {
            normalized += " is:open";
        }
        if (!normalized.contains("no:assignee")) {
            normalized += " no:assignee";
        }
        return normalized;
    }

    private RecommendedIssue toRecommendedIssue(GitHubIssueSearchItem item) {
        List<String> labels = item.labels() == null
            ? List.of()
            : item.labels()
                .stream()
                .filter(Objects::nonNull)
                .map(GitHubIssueLabel::name)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        return new RecommendedIssue(
            String.valueOf(item.id()),
            parseRepoFullName(item.repository_url()),
            item.number(),
            nonBlankOrDefault(item.title(), "제목 없음"),
            summary(item.body()),
            SEARCH_CANDIDATE_SCORE,
            labels,
            STATUS_OPEN,
            "GitHub Search에서 Good First Issue 조건으로 수집된 후보입니다."
        );
    }

    private RecommendedIssue toRecommendedIssueFromGraphQL(GraphQLIssueNode node) {
        List<String> labels = node.labels() == null || node.labels().nodes() == null
            ? List.of()
            : node.labels().nodes().stream()
                .filter(Objects::nonNull)
                .map(GraphQLLabelNode::name)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        String repoFullName = node.repository() != null && node.repository().nameWithOwner() != null
            ? node.repository().nameWithOwner()
            : parseRepoFromUrl(node.url());

        return new RecommendedIssue(
            nonBlankOrDefault(node.id(), String.valueOf(node.number() != null ? node.number() : 0L)),
            repoFullName,
            node.number() != null ? node.number() : 0L,
            nonBlankOrDefault(node.title(), "제목 없음"),
            summary(node.body()),
            SEARCH_CANDIDATE_SCORE,
            labels,
            nonBlankOrDefault(node.state(), STATUS_OPEN),
            buildGraphQLReason(node.repository())
        );
    }

    private String buildGraphQLReason(GraphQLRepository repository) {
        String base = "GitHub GraphQL에서 수집된 미할당 이슈 후보입니다.";
        if (repository == null) {
            return base;
        }
        int stars = repository.stargazerCount() != null ? repository.stargazerCount() : 0;
        String commitDate = repository.defaultBranchRef() != null && repository.defaultBranchRef().target() != null
            ? repository.defaultBranchRef().target().committedDate()
            : null;

        StringBuilder sb = new StringBuilder(base);
        sb.append(" [스타수: ").append(formatStars(stars));
        if (commitDate != null && !commitDate.isBlank()) {
            sb.append(", 최근 커밋: ").append(formatRecentCommitDate(commitDate));
        }
        sb.append("]");
        return sb.toString();
    }

    private String formatStars(int stars) {
        if (stars >= 1000) {
            return String.format(Locale.ROOT, "%.1fk", stars / 1000.0);
        }
        return String.valueOf(stars);
    }

    private String formatRecentCommitDate(String commitDate) {
        if (commitDate == null || commitDate.isBlank()) {
            return "최근";
        }
        if (commitDate.length() >= 10) {
            return commitDate.substring(0, 10);
        }
        return commitDate;
    }

    private String parseRepoFullName(String repositoryUrl) {
        if (repositoryUrl == null || !repositoryUrl.contains("/repos/")) {
            return "unknown/unknown";
        }
        return repositoryUrl.substring(repositoryUrl.indexOf("/repos/") + "/repos/".length());
    }

    private String parseRepoFromUrl(String url) {
        if (url == null || !url.contains("github.com/")) {
            return "unknown/unknown";
        }
        String path = url.substring(url.indexOf("github.com/") + "github.com/".length());
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            return parts[0] + "/" + parts[1];
        }
        return "unknown/unknown";
    }

    private String summary(String body) {
        String fallback = "GitHub에서 수집한 Good First Issue 후보입니다.";
        String value = sanitizeIssueBody(nonBlankOrDefault(body, fallback));
        if (value.isBlank()) {
            value = fallback;
        }
        if (value.length() <= 240) {
            return value;
        }
        return value.substring(0, 240);
    }

    private String sanitizeIssueBody(String body) {
        return decodeCommonHtmlEntities(body)
            .replaceAll("(?s)```.*?```", " ")
            .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
            .replaceAll("(?i)<img\\b[^>]*>", " ")
            .replaceAll("(?i)<[^>]+>", " ")
            .replaceAll("https://github\\.com/user-attachments/\\S+", " ")
            .replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "$1")
            .replaceAll("`([^`]*)`", "$1")
            .replaceAll("(?m)^#{1,6}\\s*", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String decodeCommonHtmlEntities(String value) {
        return value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'");
    }

    private String nonBlankOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private record GraphQLRequest(String query, Map<String, Object> variables) {
    }

    private record GraphQLResponse(GraphQLData data, List<GraphQLError> errors) {
    }

    private record GraphQLError(String message) {
    }

    private record GraphQLData(GraphQLSearch search) {
    }

    private record GraphQLSearch(Integer issueCount, List<GraphQLIssueNode> nodes) {
    }

    private record GraphQLIssueNode(
        String id,
        Long number,
        String title,
        String body,
        String url,
        String state,
        GraphQLAssignees assignees,
        GraphQLLabels labels,
        GraphQLRepository repository
    ) {
    }

    private record GraphQLAssignees(Integer totalCount) {
    }

    private record GraphQLLabels(List<GraphQLLabelNode> nodes) {
    }

    private record GraphQLLabelNode(String name) {
    }

    private record GraphQLRepository(
        String nameWithOwner,
        Integer stargazerCount,
        GraphQLDefaultBranchRef defaultBranchRef
    ) {
    }

    private record GraphQLDefaultBranchRef(GraphQLCommitTarget target) {
    }

    private record GraphQLCommitTarget(String committedDate) {
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

