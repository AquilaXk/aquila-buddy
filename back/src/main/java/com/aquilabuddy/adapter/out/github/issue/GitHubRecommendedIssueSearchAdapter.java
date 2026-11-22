package com.aquilabuddy.adapter.out.github.issue;

import com.aquilabuddy.application.port.out.issue.SearchRecommendedIssuesPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * [Outgoing Adapter] GitHub Search API 및 GraphQL API에서 Good First Issue 후보를 수집합니다.
 */
@Component
public class GitHubRecommendedIssueSearchAdapter implements SearchRecommendedIssuesPort {

    private static final int MAX_RAW_ISSUE_BODY_CHARS = 16_384;
    private static final int FRESHNESS_WINDOW_DAYS = 90;
    private static final int MAX_LIMIT = 50;
    private static final long DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS = 60_000L;
    private static final long MAX_RATE_LIMIT_COOLDOWN_MILLIS = 900_000L;
    private static final String STATUS_OPEN = "OPEN";
    private static final double SEARCH_CANDIDATE_SCORE = 0.0;
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("(?s)```.*?```");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern HTML_IMG_PATTERN = Pattern.compile("(?i)<img\\b[^>]*>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?i)<[^>]+>");
    private static final Pattern GITHUB_ATTACHMENT_URL_PATTERN = Pattern.compile("https://github\\.com/user-attachments/\\S+");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]*)`");
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("(?m)^#{1,6}\\s*");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

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
                timelineItems(itemTypes: [CONNECTED_EVENT, CROSS_REFERENCED_EVENT], first: 5) {
                  nodes {
                    ... on ConnectedEvent {
                      subject {
                        ... on PullRequest {
                          state
                          merged
                        }
                      }
                    }
                    ... on CrossReferencedEvent {
                      willCloseTarget
                      source {
                        ... on PullRequest {
                          state
                          merged
                        }
                      }
                    }
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
    private final Clock clock;
    private final AtomicLong cooldownUntilEpochMillis = new AtomicLong(0L);

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
        this(restClientBuilder, baseUrl, token, enabled, query, limit, graphqlEnabled, Clock.systemUTC());
    }

    public GitHubRecommendedIssueSearchAdapter(
        RestClient.Builder restClientBuilder,
        String baseUrl,
        String token,
        boolean enabled,
        String query,
        int limit
    ) {
        this(restClientBuilder, baseUrl, token, enabled, query, limit, true, Clock.systemUTC());
    }

    GitHubRecommendedIssueSearchAdapter(
        RestClient.Builder restClientBuilder,
        String baseUrl,
        String token,
        boolean enabled,
        String query,
        int limit,
        boolean graphqlEnabled,
        Clock clock
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.token = token == null ? "" : token;
        this.enabled = enabled;
        this.query = query;
        this.limit = Math.max(1, Math.min(limit, MAX_LIMIT));
        this.graphqlEnabled = graphqlEnabled;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public List<RecommendedIssue> searchRecommendedIssues() {
        return searchRecommendedIssues(query);
    }

    @Override
    public List<RecommendedIssue> searchRecommendedIssues(String query) {
        if (!enabled || isRateLimitCoolingDown()) {
            return List.of();
        }

        try {
            return searchRecommendedIssues(query, token);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 && !token.isBlank()) {
                return searchRecommendedIssuesWithoutToken(query);
            }
            recordRateLimitIfApplicable(exception.getStatusCode().value(), exception.getResponseHeaders());
            return List.of();
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    boolean isRateLimitCoolingDown() {
        return clock.millis() < cooldownUntilEpochMillis.get();
    }

    private List<RecommendedIssue> searchRecommendedIssuesWithoutToken(String query) {
        if (isRateLimitCoolingDown()) {
            return List.of();
        }
        try {
            return searchRecommendedIssues(query, "");
        } catch (RestClientResponseException exception) {
            recordRateLimitIfApplicable(exception.getStatusCode().value(), exception.getResponseHeaders());
            return List.of();
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    private List<RecommendedIssue> searchRecommendedIssues(String query, String bearerToken) {
        if (isRateLimitCoolingDown()) {
            return List.of();
        }
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
                recordRateLimitIfApplicable(exception.getStatusCode().value(), exception.getResponseHeaders());
                if (isRateLimitCoolingDown()) {
                    return List.of();
                }
            } catch (Exception ignored) {
                // GraphQL 호출 장애 또는 응답 파싱 실패 시 REST Search API로 fallback 합니다.
            }
        }
        if (isRateLimitCoolingDown()) {
            return List.of();
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

        ResponseEntity<GraphQLResponse> entity = restClient.post()
            .uri("/graphql")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .toEntity(GraphQLResponse.class);
        inspectSuccessRateLimitHeaders(entity.getHeaders());

        GraphQLResponse response = entity.getBody();
        if (response == null || response.data() == null || response.data().search() == null || response.data().search().nodes() == null) {
            return List.of();
        }

        return response.data().search().nodes().stream()
            .filter(Objects::nonNull)
            .filter(node -> node.number() != null && node.title() != null)
            .filter(this::isUnassigned)
            .filter(node -> !hasOpenOrMergedPullRequest(node))
            .map(this::toRecommendedIssueFromGraphQL)
            .limit(limit)
            .toList();
    }

    private boolean isUnassigned(GraphQLIssueNode node) {
        return node.assignees() == null
            || node.assignees().totalCount() == null
            || node.assignees().totalCount() == 0;
    }

    private boolean hasOpenOrMergedPullRequest(GraphQLIssueNode node) {
        if (node.timelineItems() == null || node.timelineItems().nodes() == null) {
            return false;
        }
        for (GraphQLTimelineItem item : node.timelineItems().nodes()) {
            if (item == null) {
                continue;
            }
            if (isOpenOrMergedPullRequest(item.subject())) {
                return true;
            }
            if (Boolean.TRUE.equals(item.willCloseTarget())
                && (item.source() == null || isOpenOrMergedPullRequest(item.source()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOpenOrMergedPullRequest(GraphQLPullRequestRef pr) {
        if (pr == null) {
            return false;
        }
        if (Boolean.TRUE.equals(pr.merged())) {
            return true;
        }
        return "OPEN".equalsIgnoreCase(pr.state()) || "MERGED".equalsIgnoreCase(pr.state());
    }

    private List<RecommendedIssue> searchViaRest(String query, String bearerToken) {
        ResponseEntity<GitHubIssueSearchResponse> entity = restClient.get()
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
            .toEntity(GitHubIssueSearchResponse.class);
        inspectSuccessRateLimitHeaders(entity.getHeaders());

        GitHubIssueSearchResponse response = entity.getBody();
        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items()
            .stream()
            .limit(limit)
            .map(this::toRecommendedIssue)
            .toList();
    }

    private void inspectSuccessRateLimitHeaders(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        if ("0".equals(remaining != null ? remaining.trim() : null)) {
            long target = resolveResetCooldownMillis(headers, clock.millis() + DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS);
            cooldownUntilEpochMillis.accumulateAndGet(target, Math::max);
        }
    }

    private void recordRateLimitIfApplicable(int statusCode, HttpHeaders headers) {
        if (statusCode != 403 && statusCode != 429) {
            return;
        }
        long now = clock.millis();
        long retryAfterMillis = parseRetryAfterMillis(headers, now);
        if (retryAfterMillis > now) {
            cooldownUntilEpochMillis.accumulateAndGet(retryAfterMillis, Math::max);
            return;
        }
        String remaining = headers == null ? null : headers.getFirst("X-RateLimit-Remaining");
        if (statusCode == 429 || "0".equals(remaining != null ? remaining.trim() : null)) {
            long target = resolveResetCooldownMillis(headers, now + DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS);
            cooldownUntilEpochMillis.accumulateAndGet(target, Math::max);
        }
    }

    private long parseRetryAfterMillis(HttpHeaders headers, long nowMillis) {
        if (headers == null) {
            return 0L;
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return 0L;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            if (seconds <= 0) {
                return 0L;
            }
            long deltaMillis = Math.min(Duration.ofSeconds(seconds).toMillis(), MAX_RATE_LIMIT_COOLDOWN_MILLIS);
            return nowMillis + deltaMillis;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private long resolveResetCooldownMillis(HttpHeaders headers, long fallbackMillis) {
        long now = clock.millis();
        if (headers != null) {
            String resetHeader = headers.getFirst("X-RateLimit-Reset");
            if (resetHeader != null && !resetHeader.isBlank()) {
                try {
                    long resetMillis = Long.parseLong(resetHeader.trim()) * 1000L;
                    if (resetMillis > now) {
                        return Math.min(resetMillis, now + MAX_RATE_LIMIT_COOLDOWN_MILLIS);
                    }
                } catch (NumberFormatException ignored) {
                    // Fallback to bounded default cooldown
                }
            }
        }
        return Math.min(fallbackMillis, now + MAX_RATE_LIMIT_COOLDOWN_MILLIS);
    }

    private String normalizedQuery(String requestedQuery) {
        String baseQuery = nonBlankOrDefault(requestedQuery, query);
        StringBuilder normalized = new StringBuilder(baseQuery);
        if (!baseQuery.contains("is:issue")) {
            normalized.append(" is:issue");
        }
        if (!baseQuery.contains("is:open") && !baseQuery.contains("state:open")) {
            normalized.append(" is:open");
        }
        appendFreshnessQualifiers(normalized, baseQuery);
        return normalized.toString();
    }

    private String normalizedGraphQLQuery(String requestedQuery) {
        String baseQuery = nonBlankOrDefault(requestedQuery, query);
        StringBuilder normalized = new StringBuilder(baseQuery);
        if (!baseQuery.contains("is:issue")) {
            normalized.append(" is:issue");
        }
        if (!baseQuery.contains("is:open") && !baseQuery.contains("state:open")) {
            normalized.append(" is:open");
        }
        if (!baseQuery.contains("no:assignee")) {
            normalized.append(" no:assignee");
        }
        appendFreshnessQualifiers(normalized, baseQuery);
        return normalized.toString();
    }

    private void appendFreshnessQualifiers(StringBuilder builder, String baseQuery) {
        if (!baseQuery.contains("archived:")) {
            builder.append(" archived:false");
        }
        if (!baseQuery.contains("created:")) {
            LocalDate cutoff = LocalDate.now(clock).minusDays(FRESHNESS_WINDOW_DAYS);
            builder.append(" created:>=").append(cutoff);
        }
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
        String bounded = body.length() > MAX_RAW_ISSUE_BODY_CHARS ? body.substring(0, MAX_RAW_ISSUE_BODY_CHARS) : body;
        String sanitized = decodeCommonHtmlEntities(bounded);
        sanitized = CODE_FENCE_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = MARKDOWN_IMAGE_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = HTML_IMG_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = GITHUB_ATTACHMENT_URL_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = MARKDOWN_LINK_PATTERN.matcher(sanitized).replaceAll("$1");
        sanitized = INLINE_CODE_PATTERN.matcher(sanitized).replaceAll("$1");
        sanitized = MARKDOWN_HEADING_PATTERN.matcher(sanitized).replaceAll("");
        return WHITESPACE_PATTERN.matcher(sanitized).replaceAll(" ").trim();
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
        GraphQLTimelineItems timelineItems,
        GraphQLRepository repository
    ) {
    }

    private record GraphQLAssignees(Integer totalCount) {
    }

    private record GraphQLLabels(List<GraphQLLabelNode> nodes) {
    }

    private record GraphQLLabelNode(String name) {
    }

    private record GraphQLTimelineItems(List<GraphQLTimelineItem> nodes) {
    }

    private record GraphQLTimelineItem(
        Boolean willCloseTarget,
        GraphQLPullRequestRef subject,
        GraphQLPullRequestRef source
    ) {
    }

    private record GraphQLPullRequestRef(String state, Boolean merged) {
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
