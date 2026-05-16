package com.aquilabuddy.adapter.out.github.prdraft;

import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftDiffPort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.global.github.GitHubApiClient;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] GitHub compare API에서 PR 초안용 diff를 수집합니다.
 */
@Component
public class GitHubPullRequestDraftDiffAdapter implements LoadPullRequestDraftDiffPort {

    private final GitHubApiClient gitHubApiClient;
    private final LoadUserProfilePort loadUserProfilePort;

    public GitHubPullRequestDraftDiffAdapter(
        GitHubApiClient gitHubApiClient,
        LoadUserProfilePort loadUserProfilePort
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.loadUserProfilePort = loadUserProfilePort;
    }

    @Override
    public String loadDiff(PullRequestDraftRequest request) {
        return gitHubApiClient
            .getCompareDiff(request.upstreamRepo(), request.baseBranch(), headReference(request.headBranch()))
            .orElseThrow(() -> new IllegalStateException("GitHub compare diff를 찾을 수 없습니다."));
    }

    private String headReference(String headBranch) {
        if (headBranch.contains(":")) {
            return headBranch;
        }

        return currentUserLogin()
            .map(login -> login + ":" + headBranch)
            .orElse(headBranch);
    }

    private Optional<String> currentUserLogin() {
        try {
            UserProfile profile = loadUserProfilePort.loadCurrentUserProfile();
            if (profile.login() == null || profile.login().isBlank()) {
                return Optional.empty();
            }

            // 현재 사용자 login이 있을 때 forkOwner:branch 형태로 compare 대상 브랜치를 지정합니다.
            return Optional.of(profile.login());
        } catch (NoSuchElementException exception) {
            return Optional.empty();
        }
    }
}
