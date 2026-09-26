package com.aquilabuddy.global.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final List<String> CONTRIBUTING_PATHS = List.of(
        "CONTRIBUTING.md",
        ".github/CONTRIBUTING.md",
        "CONTRIBUTING.adoc",
        ".github/CONTRIBUTING.adoc"
    );
    private static final List<String> PULL_REQUEST_TEMPLATE_PATHS = List.of(
        ".github/pull_request_template.md",
        ".github/PULL_REQUEST_TEMPLATE.md",
        "pull_request_template.md",
        "docs/pull_request_template.md",
        ".github/PULL_REQUEST_TEMPLATE/pull_request_template.md",
        "PULL_REQUEST_TEMPLATE/pull_request_template.md",
        "docs/PULL_REQUEST_TEMPLATE/pull_request_template.md"
    );
    private static final List<String> TREE_EXCLUDED_PATH_SEGMENTS = List.of(
        "/.git/",
        "/.gradle/",
        "/.idea/",
        "/build/",
        "/dist/",
        "/node_modules/",
        "/target/",
        "/vendor/"
    );
    private static final List<String> TREE_ASSET_EXTENSIONS = List.of(
        ".avif",
        ".gif",
        ".ico",
        ".jpeg",
        ".jpg",
        ".pdf",
        ".png",
        ".svg",
        ".webp",
        ".zip"
    );
    private static final List<String> TREE_QUERY_STOP_WORDS = List.of(
        "and",
        "first",
        "good",
        "issue",
        "the"
    );
    private static final List<String> CANDIDATE_DIR_PREFIXES = List.of(
        "src",
        "lib",
        "app",
        "packages",
        "back",
        "front",
        "backend",
        "frontend",
        "client",
        "server",
        "core"
    );

    private static final Duration DEFAULT_TREE_CACHE_TTL = Duration.ofHours(2);

    private final RestClient restClient;
    private final String token;
    private final Clock clock;
    private final Duration treeCacheTtl;
    private final Map<String, CachedTree> treeCache = new ConcurrentHashMap<>();

    @Autowired
    public GitHubApiClient(
        RestClient.Builder restClientBuilder,
        @Value("${app.github.api-base-url:https://api.github.com}") String baseUrl,
        @Value("${app.github.token:}") String token
    ) {
        this(restClientBuilder, baseUrl, token, Clock.systemUTC(), DEFAULT_TREE_CACHE_TTL);
    }

    GitHubApiClient(
        RestClient.Builder restClientBuilder,
        String baseUrl,
        String token,
        Clock clock,
        Duration treeCacheTtl
    ) {
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
        this.token = token;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.treeCacheTtl = treeCacheTtl == null ? DEFAULT_TREE_CACHE_TTL : treeCacheTtl;
    }

    void clearTreeCache() {
        treeCache.clear();
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

    /**
     * 사용자 vector 갱신에 필요한 GitHub 공개 contribution context를 한 번에 구성합니다.
     */
    public GitHubContributionContext loadContributionContext(String username) {
        GitHubUserProfile profile = getUserProfile(username).orElse(null);
        List<GitHubRepository> repositories = getUserRepos(username);
        Map<String, Integer> languages = aggregateLanguages(repositories);
        List<String> primaryLanguages = languages.keySet()
            .stream()
            .limit(5)
            .toList();
        List<String> topics = aggregateTopics(repositories);
        String text = buildContributionContextText(profile, repositories, languages);

        return new GitHubContributionContext(
            profile,
            repositories,
            primaryLanguages,
            topics,
            text
        );
    }

    /**
     * 이슈 분석 캐시 만료 후 원격 이슈가 수정되었는지 확인할 때 사용합니다.
     */
    public Optional<Instant> loadIssueUpdatedAt(String repoFullName, long issueNumber) {
        if (!hasText(repoFullName) || issueNumber <= 0 || !repoFullName.contains("/")) {
            return Optional.empty();
        }

        String[] parts = repoFullName.split("/", 2);
        try {
            GitHubIssueMetadataResponse response = restClient.get()
                .uri("/repos/{owner}/{repo}/issues/{issueNumber}", parts[0], parts[1], issueNumber)
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubIssueMetadataResponse.class);

            return Optional.ofNullable(response)
                .map(GitHubIssueMetadataResponse::updatedAt);
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    /**
     * GitHub compare API의 파일 patch들을 PR 초안 입력 diff 문자열로 변환합니다.
     */
    public Optional<String> getCompareDiff(String upstreamRepo, String baseBranch, String headReference) {
        try {
            GitHubCompareResponse response = restClient.get()
                .uri("/repos/" + upstreamRepo + "/compare/" + baseBranch + "..." + headReference)
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubCompareResponse.class);

            return Optional.ofNullable(response)
                .map(GitHubCompareResponse::files)
                .map(this::formatCompareDiff)
                .filter(this::hasText);
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    /**
     * PR 초안 생성에 참고할 저장소별 기여 가이드 context를 수집합니다.
     */
    public GitHubRepositoryGuideContext loadRepositoryGuideContext(String upstreamRepo) {
        String contributing = firstExistingContent(upstreamRepo, CONTRIBUTING_PATHS).orElse(null);
        String pullRequestTemplate = firstExistingContent(upstreamRepo, PULL_REQUEST_TEMPLATE_PATHS).orElse(null);
        List<String> mergedPullRequestSamples = contributing == null && pullRequestTemplate == null
            ? getMergedPullRequestSamples(upstreamRepo)
            : List.of();

        return new GitHubRepositoryGuideContext(
            contributing,
            pullRequestTemplate,
            mergedPullRequestSamples
        );
    }

    /**
     * GitHub code search 결과의 파일 경로를 contents API로 읽어 issue guide용 코드 맥락을 구성합니다.
     */
    public GitHubRepositoryCodeContext loadRepositoryCodeContext(
        String upstreamRepo,
        String query,
        int maxFiles,
        int maxContentChars
    ) {
        if (!hasText(upstreamRepo) || maxFiles <= 0) {
            return GitHubRepositoryCodeContext.empty(upstreamRepo);
        }

        List<GitHubRepositoryCodeContextFile> files =
            loadCodeSearchCodeContextFiles(upstreamRepo, query, maxFiles, maxContentChars);
        if (files.isEmpty()) {
            files = loadRepositoryTreeCodeContextFiles(upstreamRepo, query, maxFiles, maxContentChars);
        }
        return new GitHubRepositoryCodeContext(upstreamRepo, "default", files);
    }

    private List<GitHubRepositoryCodeContextFile> loadCodeSearchCodeContextFiles(
        String upstreamRepo,
        String query,
        int maxFiles,
        int maxContentChars
    ) {
        try {
            GitHubCodeSearchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/search/code")
                    .queryParam("q", codeSearchQuery(upstreamRepo, query))
                    .queryParam("per_page", maxFiles)
                    .build())
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubCodeSearchResponse.class);
            List<GitHubCodeSearchItemResponse> items = response == null || response.items() == null
                ? List.of()
                : response.items();
            List<GitHubRepositoryCodeContextFile> files = new ArrayList<>();
            Set<String> seenPaths = new LinkedHashSet<>();
            for (GitHubCodeSearchItemResponse item : items) {
                if (files.size() >= maxFiles) {
                    break;
                }
                if (item == null || !hasText(item.path()) || !isRepositoryTreeCodeCandidate(item.path())) {
                    continue;
                }
                if (!seenPaths.add(item.path())) {
                    continue;
                }
                getContentFile(upstreamRepo, item.path())
                    .map(content -> toRepositoryCodeContextFile(item.path(), content, maxContentChars))
                    .ifPresent(files::add);
            }
            return files;
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    private List<GitHubRepositoryCodeContextFile> loadRepositoryTreeCodeContextFiles(
        String upstreamRepo,
        String query,
        int maxFiles,
        int maxContentChars
    ) {
        Optional<GitHubRepositoryTreeResponse> treeResponseOpt = fetchRepositoryTree(upstreamRepo);
        if (treeResponseOpt.isEmpty()) {
            return List.of();
        }

        GitHubRepositoryTreeResponse response = treeResponseOpt.get();
        List<GitHubRepositoryTreeItemResponse> tree = response.tree() == null
            ? List.of()
            : response.tree();
        List<String> queryTokens = codeContextQueryTokens(query);

        List<GitHubRepositoryTreeCandidate> candidates = extractTreeCandidates(tree, queryTokens);

        // recursive tree 응답에 truncated: true가 반환된 경우, 상위 디렉토리 하위 탐색으로 보완합니다.
        if (response.isTruncated() && candidates.size() < maxFiles) {
            candidates = fallbackTruncatedTreeCandidates(upstreamRepo, tree, queryTokens, maxFiles, candidates);
        }

        List<GitHubRepositoryCodeContextFile> files = new ArrayList<>();
        for (GitHubRepositoryTreeCandidate candidate : candidates) {
            if (files.size() >= maxFiles) {
                break;
            }
            getContentFile(upstreamRepo, candidate.path())
                .map(content -> toRepositoryCodeContextFile(candidate.path(), content, maxContentChars))
                .ifPresent(files::add);
        }
        return files;
    }

    // Git Recursive Tree 응답을 2시간 TTL 동안 캐시하여 GitHub API rate limit을 방어합니다.
    private Optional<GitHubRepositoryTreeResponse> fetchRepositoryTree(String upstreamRepo) {
        Instant now = clock.instant();
        String cacheKey = upstreamRepo.strip().toLowerCase(Locale.ROOT);
        CachedTree cached = treeCache.get(cacheKey);
        if (cached != null && !cached.isExpired(now)) {
            return Optional.of(cached.response());
        }

        try {
            GitHubRepositoryTreeResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/" + upstreamRepo.strip() + "/git/trees/HEAD")
                    .queryParam("recursive", "1")
                    .build())
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubRepositoryTreeResponse.class);

            if (response != null) {
                treeCache.put(cacheKey, new CachedTree(now.plus(treeCacheTtl), response));
                return Optional.of(response);
            }
            return Optional.empty();
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    private List<GitHubRepositoryTreeCandidate> extractTreeCandidates(
        List<GitHubRepositoryTreeItemResponse> tree,
        List<String> queryTokens
    ) {
        return tree.stream()
            .filter(item -> item != null && "blob".equals(item.type()))
            .filter(item -> isRepositoryTreeCodeCandidate(item.path()))
            .map(item -> new GitHubRepositoryTreeCandidate(
                item.path(),
                treeCandidateScore(item.path(), queryTokens)
            ))
            .filter(candidate -> queryTokens.isEmpty() || candidate.score() > 0)
            .sorted(Comparator
                .comparingInt(GitHubRepositoryTreeCandidate::score)
                .reversed()
                .thenComparing(GitHubRepositoryTreeCandidate::path))
            .toList();
    }

    // truncated 플래그 대응: 10만 개 한도로 잘린 트리에서 주요 소스 디렉토리 하위를 비재귀적으로 조회해 보완합니다.
    private List<GitHubRepositoryTreeCandidate> fallbackTruncatedTreeCandidates(
        String upstreamRepo,
        List<GitHubRepositoryTreeItemResponse> tree,
        List<String> queryTokens,
        int maxFiles,
        List<GitHubRepositoryTreeCandidate> existingCandidates
    ) {
        Set<String> seenPaths = existingCandidates.stream()
            .map(GitHubRepositoryTreeCandidate::path)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<GitHubRepositoryTreeCandidate> combined = new ArrayList<>(existingCandidates);

        List<GitHubRepositoryTreeItemResponse> dirNodes = tree.stream()
            .filter(item -> item != null && "tree".equals(item.type()) && hasText(item.path()) && hasText(item.sha()))
            .filter(item -> isCandidateDirectory(item.path(), queryTokens))
            .limit(5)
            .toList();

        for (GitHubRepositoryTreeItemResponse dirNode : dirNodes) {
            if (combined.size() >= maxFiles) {
                break;
            }
            List<GitHubRepositoryTreeItemResponse> subTree = fetchSubtree(upstreamRepo, dirNode.sha(), dirNode.path());
            for (GitHubRepositoryTreeCandidate subCandidate : extractTreeCandidates(subTree, queryTokens)) {
                if (seenPaths.add(subCandidate.path())) {
                    combined.add(subCandidate);
                    if (combined.size() >= maxFiles) {
                        break;
                    }
                }
            }
        }

        combined.sort(Comparator
            .comparingInt(GitHubRepositoryTreeCandidate::score)
            .reversed()
            .thenComparing(GitHubRepositoryTreeCandidate::path));
        return combined;
    }

    private boolean isCandidateDirectory(String dirPath, List<String> queryTokens) {
        String lower = dirPath.toLowerCase(Locale.ROOT);
        if (TREE_EXCLUDED_PATH_SEGMENTS.stream().anyMatch(lower::contains)) {
            return false;
        }
        if (queryTokens.stream().anyMatch(lower::contains)) {
            return true;
        }
        return CANDIDATE_DIR_PREFIXES.stream().anyMatch(prefix ->
            lower.equals(prefix) || lower.startsWith(prefix + "/")
        );
    }

    private List<GitHubRepositoryTreeItemResponse> fetchSubtree(String upstreamRepo, String treeSha, String basePath) {
        try {
            GitHubRepositoryTreeResponse subtreeResponse = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/" + upstreamRepo.strip() + "/git/trees/" + treeSha.strip())
                    .queryParam("recursive", "1")
                    .build())
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubRepositoryTreeResponse.class);
            if (subtreeResponse == null || subtreeResponse.tree() == null) {
                return List.of();
            }
            return subtreeResponse.tree().stream()
                .filter(Objects::nonNull)
                .map(subItem -> new GitHubRepositoryTreeItemResponse(
                    basePath.isEmpty() ? subItem.path() : basePath + "/" + subItem.path(),
                    subItem.type(),
                    subItem.sha()
                ))
                .toList();
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    private List<String> codeContextQueryTokens(String query) {
        if (!hasText(query)) {
            return List.of();
        }

        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            .map(String::strip)
            .filter(token -> token.length() >= 3)
            .filter(token -> !TREE_QUERY_STOP_WORDS.contains(token))
            .distinct()
            .toList();
    }

    private int treeCandidateScore(String path, List<String> queryTokens) {
        if (!hasText(path) || queryTokens.isEmpty()) {
            return 0;
        }

        String normalized = path.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : queryTokens) {
            if (normalized.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private boolean isRepositoryTreeCodeCandidate(String path) {
        if (!hasText(path)) {
            return false;
        }

        String normalized = "/" + path.strip().toLowerCase(Locale.ROOT);
        if (TREE_EXCLUDED_PATH_SEGMENTS.stream().anyMatch(normalized::contains)) {
            return false;
        }
        return TREE_ASSET_EXTENSIONS.stream().noneMatch(normalized::endsWith);
    }

    private Optional<String> firstExistingContent(String upstreamRepo, List<String> paths) {
        return paths.stream()
            .map(path -> getContentFile(upstreamRepo, path))
            .flatMap(Optional::stream)
            .findFirst();
    }

    private Optional<String> getContentFile(String upstreamRepo, String path) {
        try {
            GitHubContentResponse response = restClient.get()
                .uri("/repos/" + upstreamRepo + "/contents/" + path)
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubContentResponse.class);

            return Optional.ofNullable(response)
                .map(GitHubContentResponse::decodedContent)
                .filter(this::hasText);
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    private GitHubRepositoryCodeContextFile toRepositoryCodeContextFile(String path, String content, int maxContentChars) {
        String sanitizedContent = sanitizeCodeContextContent(content);
        if (maxContentChars <= 0 || sanitizedContent.length() <= maxContentChars) {
            return new GitHubRepositoryCodeContextFile(path, sanitizedContent, false);
        }

        return new GitHubRepositoryCodeContextFile(path, sanitizedContent.substring(0, maxContentChars) + "...", true);
    }

    private String sanitizeCodeContextContent(String content) {
        if (!hasText(content)) {
            return "";
        }

        return Arrays.stream(content.split("\\R"))
            .map(String::stripTrailing)
            .filter(line -> {
                String stripped = line.strip();
                return !stripped.isEmpty()
                    && !stripped.startsWith("//")
                    && !stripped.startsWith("#")
                    && !stripped.startsWith("/*")
                    && !stripped.startsWith("*")
                    && !stripped.startsWith("*/");
            })
            .collect(Collectors.joining("\n"));
    }

    private String codeSearchQuery(String upstreamRepo, String query) {
        String normalizedQuery = hasText(query) ? query.strip() : "TODO";
        return normalizedQuery + " repo:" + upstreamRepo;
    }

    private List<String> getMergedPullRequestSamples(String upstreamRepo) {
        try {
            GitHubPullRequestResponse[] response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/" + upstreamRepo + "/pulls")
                    .queryParam("state", "closed")
                    .queryParam("per_page", 20)
                    .build())
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubPullRequestResponse[].class);

            if (response == null) {
                return List.of();
            }

            return Arrays.stream(response)
                .filter(item -> hasText(item.mergedAt()) && hasText(item.body()))
                .map(GitHubPullRequestResponse::body)
                .limit(3)
                .toList();
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    private String formatCompareDiff(List<GitHubCompareFileResponse> files) {
        if (files == null) {
            return "";
        }

        return files.stream()
            .filter(file -> hasText(file.patch()))
            .map(file -> "--- " + file.filename() + "\n" + file.patch())
            .collect(Collectors.joining("\n"));
    }

    private List<String> aggregateTopics(List<GitHubRepository> repositories) {
        return repositories.stream()
            .flatMap(repository -> repository.topics().stream())
            .filter(topic -> topic != null && !topic.isBlank())
            .distinct()
            .limit(20)
            .toList();
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

    public record GitHubContributionContext(
        GitHubUserProfile profile,
        List<GitHubRepository> repositories,
        List<String> primaryLanguages,
        List<String> topics,
        String text
    ) {
    }

    public record GitHubRepository(
        String name,
        String description,
        String language,
        List<String> topics,
        int stars
    ) {
    }

    public record GitHubRepositoryGuideContext(
        String contributing,
        String pullRequestTemplate,
        List<String> mergedPullRequestSamples
    ) {

        public GitHubRepositoryGuideContext {
            mergedPullRequestSamples = mergedPullRequestSamples == null
                ? List.of()
                : List.copyOf(mergedPullRequestSamples);
        }
    }

    public record GitHubRepositoryCodeContext(
        String repository,
        String branch,
        List<GitHubRepositoryCodeContextFile> files
    ) {

        public GitHubRepositoryCodeContext {
            repository = repository == null ? "" : repository.strip();
            branch = branch == null ? "" : branch.strip();
            files = files == null ? List.of() : List.copyOf(files);
        }

        static GitHubRepositoryCodeContext empty(String repository) {
            return new GitHubRepositoryCodeContext(repository, "default", List.of());
        }
    }

    public record GitHubRepositoryCodeContextFile(
        String path,
        String content,
        boolean truncated
    ) {

        public GitHubRepositoryCodeContextFile {
            path = path == null ? "" : path.strip();
            content = content == null ? "" : content.strip();
        }
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

    private record GitHubCompareResponse(List<GitHubCompareFileResponse> files) {
    }

    private record GitHubCompareFileResponse(String filename, String patch) {
    }

    private record GitHubCodeSearchResponse(List<GitHubCodeSearchItemResponse> items) {
    }

    private record GitHubCodeSearchItemResponse(String path) {
    }

    private record GitHubRepositoryTreeResponse(
        List<GitHubRepositoryTreeItemResponse> tree,
        Boolean truncated
    ) {
        boolean isTruncated() {
            return Boolean.TRUE.equals(truncated);
        }
    }

    private record GitHubRepositoryTreeItemResponse(
        String path,
        String type,
        String sha
    ) {
        GitHubRepositoryTreeItemResponse(String path, String type) {
            this(path, type, null);
        }
    }

    private record GitHubRepositoryTreeCandidate(
        String path,
        int score
    ) {
    }

    private record CachedTree(
        Instant expiresAt,
        GitHubRepositoryTreeResponse response
    ) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private record GitHubContentResponse(String content) {

        String decodedContent() {
            if (content == null || content.isBlank()) {
                return null;
            }

            String normalized = content.replace("\n", "");
            return new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
        }
    }

    private record GitHubPullRequestResponse(
        String body,
        @JsonProperty("merged_at") String mergedAt
    ) {
    }

    private record GitHubIssueMetadataResponse(
        @JsonProperty("updated_at") Instant updatedAt
    ) {
    }
}
