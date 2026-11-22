package com.aquilabuddy.global.github;

import com.aquilabuddy.domain.github.GitHubReferenceValidator;
import com.aquilabuddy.global.github.GitHubCodeContextSupport.ComparePatch;
import com.aquilabuddy.global.github.GitHubCodeContextSupport.TreeCandidate;
import com.aquilabuddy.global.github.GitHubCodeContextSupport.TreeItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    private static final int MAX_TREE_CACHE_ENTRIES = 64;
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
    private static final Duration DEFAULT_TREE_CACHE_TTL = Duration.ofHours(2);

    private final RestClient restClient;
    private final String token;
    private final Clock clock;
    private final Duration treeCacheTtl;
    private final Map<String, CachedTree> treeCache = Collections.synchronizedMap(
        new LinkedHashMap<>(MAX_TREE_CACHE_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedTree> eldest) {
                return size() > MAX_TREE_CACHE_ENTRIES;
            }
        }
    );

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
        if (!hasText(username)) {
            return Optional.empty();
        }
        try {
            GitHubUserProfileResponse response = restClient.get()
                .uri("/users/{username}", username.strip())
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubUserProfileResponse.class);

            return Optional.ofNullable(response)
                .map(GitHubUserProfileResponse::toDomain);
        } catch (RestClientException | IllegalArgumentException exception) {
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
        if (!hasText(username) || limit <= 0) {
            return List.of();
        }
        try {
            GitHubRepositoryResponse[] response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/users/{username}/repos")
                    .queryParam("per_page", 100)
                    .queryParam("sort", "updated")
                    .build(username.strip()))
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
        } catch (RestClientException | IllegalArgumentException exception) {
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
        String[] slug = GitHubReferenceValidator.splitSafeRepoSlug(repoFullName);
        if (slug == null || issueNumber <= 0) {
            return Optional.empty();
        }

        try {
            GitHubIssueMetadataResponse response = restClient.get()
                .uri("/repos/{owner}/{repo}/issues/{issueNumber}", slug[0], slug[1], issueNumber)
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubIssueMetadataResponse.class);

            return Optional.ofNullable(response)
                .map(GitHubIssueMetadataResponse::updatedAt);
        } catch (RestClientException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * GitHub compare API의 파일 patch들을 PR 초안 입력 diff 문자열로 변환합니다.
     */
    public Optional<String> getCompareDiff(String upstreamRepo, String baseBranch, String headReference) {
        String[] slug = GitHubReferenceValidator.splitSafeRepoSlug(upstreamRepo);
        if (slug == null
            || !GitHubReferenceValidator.isSafeGitReference(baseBranch, false)
            || !GitHubReferenceValidator.isSafeGitReference(headReference, true)) {
            return Optional.empty();
        }
        try {
            GitHubCompareResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{owner}/{repo}/compare/" + baseBranch.strip() + "..." + headReference.strip())
                    .build(slug[0], slug[1]))
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubCompareResponse.class);

            if (response == null || response.files() == null) {
                return Optional.empty();
            }
            List<ComparePatch> patches = response.files().stream()
                .map(file -> new ComparePatch(file.filename(), file.patch()))
                .toList();
            String formatted = GitHubCodeContextSupport.formatCompareDiff(patches);
            return hasText(formatted) ? Optional.of(formatted) : Optional.empty();
        } catch (RestClientException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * PR 초안 생성에 참고할 저장소별 기여 가이드 context를 수집합니다.
     */
    public GitHubRepositoryGuideContext loadRepositoryGuideContext(String upstreamRepo) {
        if (!GitHubReferenceValidator.isSafeRepoSlug(upstreamRepo)) {
            return new GitHubRepositoryGuideContext(null, null, List.of());
        }
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
        if (!GitHubReferenceValidator.isSafeRepoSlug(upstreamRepo) || maxFiles <= 0) {
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
                    .queryParam("q", codeSearchQuery(upstreamRepo.strip(), query))
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
                if (item == null || !hasText(item.path()) || !GitHubCodeContextSupport.isRepositoryTreeCodeCandidate(item.path())) {
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
        } catch (RestClientException | IllegalArgumentException exception) {
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
        List<TreeItem> tree = toTreeItems(response.tree());
        List<String> queryTokens = GitHubCodeContextSupport.codeContextQueryTokens(query);
        List<TreeCandidate> candidates = GitHubCodeContextSupport.extractTreeCandidates(tree, queryTokens);

        // recursive tree 응답에 truncated: true가 반환된 경우, 상위 디렉토리 하위 탐색으로 보완합니다.
        if (response.isTruncated() && candidates.size() < maxFiles) {
            candidates = fallbackTruncatedTreeCandidates(upstreamRepo, tree, queryTokens, maxFiles, candidates);
        }

        List<GitHubRepositoryCodeContextFile> files = new ArrayList<>();
        for (TreeCandidate candidate : candidates) {
            if (files.size() >= maxFiles) {
                break;
            }
            getContentFile(upstreamRepo, candidate.path())
                .map(content -> toRepositoryCodeContextFile(candidate.path(), content, maxContentChars))
                .ifPresent(files::add);
        }
        return files;
    }

    private static List<TreeItem> toTreeItems(List<GitHubRepositoryTreeItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<TreeItem> converted = new ArrayList<>(items.size());
        for (GitHubRepositoryTreeItemResponse item : items) {
            if (item != null) {
                converted.add(new TreeItem(item.path(), item.type(), item.sha()));
            }
        }
        return converted;
    }

    // Git Recursive Tree 응답을 2시간 TTL + LRU 64개 상한으로 캐시하여 rate limit과 메모리 누수를 방어합니다.
    private Optional<GitHubRepositoryTreeResponse> fetchRepositoryTree(String upstreamRepo) {
        String[] slug = GitHubReferenceValidator.splitSafeRepoSlug(upstreamRepo);
        if (slug == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        String cacheKey = upstreamRepo.strip().toLowerCase(Locale.ROOT);
        CachedTree cached = treeCache.get(cacheKey);
        if (cached != null) {
            if (!cached.isExpired(now)) {
                return Optional.of(cached.response());
            }
            treeCache.remove(cacheKey, cached);
        }

        try {
            GitHubRepositoryTreeResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{owner}/{repo}/git/trees/HEAD")
                    .queryParam("recursive", "1")
                    .build(slug[0], slug[1]))
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubRepositoryTreeResponse.class);

            if (response != null) {
                treeCache.put(cacheKey, new CachedTree(now.plus(treeCacheTtl), response));
                return Optional.of(response);
            }
            return Optional.empty();
        } catch (RestClientException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private List<TreeCandidate> fallbackTruncatedTreeCandidates(
        String upstreamRepo,
        List<TreeItem> tree,
        List<String> queryTokens,
        int maxFiles,
        List<TreeCandidate> existingCandidates
    ) {
        Set<String> seenPaths = existingCandidates.stream()
            .map(TreeCandidate::path)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TreeCandidate> combined = new ArrayList<>(existingCandidates);

        List<TreeItem> dirNodes = tree.stream()
            .filter(item -> "tree".equals(item.type()) && hasText(item.path()) && hasText(item.sha()))
            .filter(item -> GitHubCodeContextSupport.isCandidateDirectory(item.path(), queryTokens))
            .limit(5)
            .toList();

        for (TreeItem dirNode : dirNodes) {
            if (combined.size() >= maxFiles) {
                break;
            }
            List<TreeItem> subTree = fetchSubtree(upstreamRepo, dirNode.sha(), dirNode.path());
            for (TreeCandidate subCandidate : GitHubCodeContextSupport.extractTreeCandidates(subTree, queryTokens)) {
                if (seenPaths.add(subCandidate.path())) {
                    combined.add(subCandidate);
                    if (combined.size() >= maxFiles) {
                        break;
                    }
                }
            }
        }

        combined.sort(Comparator
            .comparingInt(TreeCandidate::score)
            .reversed()
            .thenComparing(TreeCandidate::path));
        return combined;
    }

    private List<TreeItem> fetchSubtree(String upstreamRepo, String treeSha, String basePath) {
        String[] slug = GitHubReferenceValidator.splitSafeRepoSlug(upstreamRepo);
        if (slug == null || !GitHubReferenceValidator.isSafeTreeIdentifier(treeSha)) {
            return List.of();
        }
        try {
            GitHubRepositoryTreeResponse subtreeResponse = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{owner}/{repo}/git/trees/{treeSha}")
                    .queryParam("recursive", "1")
                    .build(slug[0], slug[1], treeSha.strip()))
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubRepositoryTreeResponse.class);
            if (subtreeResponse == null || subtreeResponse.tree() == null) {
                return List.of();
            }
            return subtreeResponse.tree().stream()
                .filter(Objects::nonNull)
                .map(subItem -> new TreeItem(
                    basePath.isEmpty() ? subItem.path() : basePath + "/" + subItem.path(),
                    subItem.type(),
                    subItem.sha()
                ))
                .toList();
        } catch (RestClientException | IllegalArgumentException exception) {
            return List.of();
        }
    }

    private Optional<String> firstExistingContent(String upstreamRepo, List<String> paths) {
        return paths.stream()
            .map(path -> getContentFile(upstreamRepo, path))
            .flatMap(Optional::stream)
            .findFirst();
    }

    private Optional<String> getContentFile(String upstreamRepo, String path) {
        String[] slug = GitHubReferenceValidator.splitSafeRepoSlug(upstreamRepo);
        if (slug == null || !GitHubCodeContextSupport.isSafeRepositoryPath(path)) {
            return Optional.empty();
        }
        try {
            GitHubContentResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{owner}/{repo}/contents/" + path.strip())
                    .build(slug[0], slug[1]))
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubContentResponse.class);

            return Optional.ofNullable(response)
                .map(GitHubContentResponse::decodedContent)
                .filter(this::hasText);
        } catch (RestClientException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private GitHubRepositoryCodeContextFile toRepositoryCodeContextFile(String path, String content, int maxContentChars) {
        String sanitizedContent = GitHubCodeContextSupport.sanitizeCodeContextContent(content);
        if (maxContentChars <= 0 || sanitizedContent.length() <= maxContentChars) {
            return new GitHubRepositoryCodeContextFile(path, sanitizedContent, false);
        }

        return new GitHubRepositoryCodeContextFile(path, sanitizedContent.substring(0, maxContentChars) + "...", true);
    }

    private String codeSearchQuery(String upstreamRepo, String query) {
        String normalizedQuery = hasText(query) ? query.strip() : "TODO";
        return normalizedQuery + " repo:" + upstreamRepo;
    }

    private List<String> getMergedPullRequestSamples(String upstreamRepo) {
        String[] slug = GitHubReferenceValidator.splitSafeRepoSlug(upstreamRepo);
        if (slug == null) {
            return List.of();
        }
        try {
            GitHubPullRequestResponse[] response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{owner}/{repo}/pulls")
                    .queryParam("state", "closed")
                    .queryParam("per_page", 20)
                    .build(slug[0], slug[1]))
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
        } catch (RestClientException | IllegalArgumentException exception) {
            return List.of();
        }
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

            return new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
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
