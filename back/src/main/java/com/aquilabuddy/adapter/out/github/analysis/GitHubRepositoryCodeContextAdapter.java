package com.aquilabuddy.adapter.out.github.analysis;

import com.aquilabuddy.application.port.out.analysis.LoadRepositoryCodeContextPort;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.analysis.RepositoryCodeContextFile;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.global.github.GitHubApiClient;
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

    private final GitHubApiClient gitHubApiClient;
    private final int maxFiles;
    private final int maxContentChars;

    public GitHubRepositoryCodeContextAdapter(
        GitHubApiClient gitHubApiClient,
        @Value("${app.analysis.code-context.max-files:3}") int maxFiles,
        @Value("${app.analysis.code-context.max-content-chars:2000}") int maxContentChars
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.maxFiles = normalizeLimit(maxFiles, 3);
        this.maxContentChars = normalizeLimit(maxContentChars, 2000);
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
        return Stream.of(
                issue.title(),
                issue.summary(),
                String.join(" ", issue.labels()),
                issue.reason()
            )
            .filter(GitHubRepositoryCodeContextAdapter::hasText)
            .collect(Collectors.joining(" "));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int normalizeLimit(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
