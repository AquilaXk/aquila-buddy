package com.aquilabuddy.adapter.out.github.analysis;

import com.aquilabuddy.application.port.out.analysis.LoadIssueUpdatedAtPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.global.github.GitHubApiClient;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * GitHub issue metadata로 저장된 분석 캐시가 아직 유효한지 판단할 근거를 제공합니다.
 */
@Component
public class GitHubIssueUpdatedAtAdapter implements LoadIssueUpdatedAtPort {

    private final GitHubApiClient gitHubApiClient;

    public GitHubIssueUpdatedAtAdapter(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    @Override
    public Optional<Instant> loadIssueUpdatedAt(RecommendedIssue issue) {
        return gitHubApiClient.loadIssueUpdatedAt(issue.repoFullName(), issue.issueNumber());
    }
}
