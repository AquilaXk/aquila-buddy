package com.aquilabuddy.adapter.out.github.analysis;

import com.aquilabuddy.application.port.out.analysis.LoadRepositoryCodeContextPort;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.analysis.RepositoryCodeContextFile;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.global.github.GitHubApiClient;
import com.aquilabuddy.global.github.GitHubCodeContextSupport;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] GitHub code search와 contents API로 이슈 관련 코드 맥락을 수집합니다.
 */
@Component
public class GitHubRepositoryCodeContextAdapter implements LoadRepositoryCodeContextPort {

    static final int MAX_FILE_SAMPLES = 5;
    static final int MAX_FILE_CONTENT_CHARS = 3_000;
    private static final int HARD_MAX_FILES = 10;
    private static final int HARD_MAX_CONTENT_CHARS = 16_384;

    private final GitHubApiClient gitHubApiClient;
    private final int maxFiles;
    private final int maxContentChars;

    public GitHubRepositoryCodeContextAdapter(
        GitHubApiClient gitHubApiClient,
        @Value("${app.analysis.code-context.max-files:" + MAX_FILE_SAMPLES + "}") int maxFiles,
        @Value("${app.analysis.code-context.max-content-chars:" + MAX_FILE_CONTENT_CHARS + "}") int maxContentChars
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.maxFiles = normalizeLimit(maxFiles, MAX_FILE_SAMPLES, HARD_MAX_FILES);
        this.maxContentChars = normalizeLimit(maxContentChars, MAX_FILE_CONTENT_CHARS, HARD_MAX_CONTENT_CHARS);
    }

    @Override
    public RepositoryCodeContext loadRepositoryCodeContext(RecommendedIssue issue) {
        try {
            GitHubApiClient.GitHubRepositoryCodeContext context = gitHubApiClient.loadRepositoryCodeContext(
                issue.repoFullName(),
                buildSearchQuery(issue),
                maxFiles,
                maxContentChars
            );
            List<RepositoryCodeContextFile> files = context.files()
                .stream()
                .map(file -> new RepositoryCodeContextFile(file.path(), file.content(), file.truncated()))
                .toList();
            return RepositoryCodeContext.fromFiles(
                context.repository(),
                context.branch(),
                files,
                maxFiles,
                maxContentChars
            );
        } catch (RuntimeException exception) {
            return RepositoryCodeContext.empty();
        }
    }

    private static String buildSearchQuery(RecommendedIssue issue) {
        String rawContext = Stream.of(
                issue.title(),
                issue.summary(),
                String.join(" ", issue.labels()),
                issue.reason()
            )
            .filter(GitHubRepositoryCodeContextAdapter::hasText)
            .collect(Collectors.joining(" "));
        List<String> anchorTokens = GitHubCodeContextSupport.codeContextQueryTokens(rawContext);
        if (anchorTokens.isEmpty()) {
            return rawContext;
        }
        return rawContext + " " + String.join(" ", anchorTokens);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int normalizeLimit(int value, int defaultValue, int hardCeiling) {
        int resolved = value > 0 ? value : defaultValue;
        return Math.min(resolved, hardCeiling);
    }
}
