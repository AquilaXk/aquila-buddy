package com.aquilabuddy.adapter.out.github.prdraft;

import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftRepositoryGuideContextPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryGuideContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.global.github.GitHubApiClient;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] GitHub repository 문서와 최근 PR context를 PR 초안용 guide context로 변환합니다.
 */
@Component
public class GitHubPullRequestDraftRepositoryGuideContextAdapter
    implements LoadPullRequestDraftRepositoryGuideContextPort {

    private final GitHubApiClient gitHubApiClient;

    public GitHubPullRequestDraftRepositoryGuideContextAdapter(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    @Override
    public PullRequestDraftRepositoryGuideContext loadRepositoryGuideContext(PullRequestDraftRequest request) {
        GitHubApiClient.GitHubRepositoryGuideContext context =
            gitHubApiClient.loadRepositoryGuideContext(request.upstreamRepo());

        return new PullRequestDraftRepositoryGuideContext(
            context.contributing(),
            context.pullRequestTemplate(),
            context.mergedPullRequestSamples()
        );
    }
}
