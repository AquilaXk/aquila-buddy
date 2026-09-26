package com.aquilabuddy.adapter.out.github.prdraft;

import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftRepositoryGuideContextPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryGuideContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestTemplateSection;
import com.aquilabuddy.global.github.GitHubApiClient;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] GitHub repository 문서와 템플릿 마크다운을 분석하여 구조화된 PR 초안 guide context로 변환합니다.
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

        // 템플릿 마크다운 섹션(Description, Test Plan, Checklist 등)을 구조화 파싱합니다.
        List<PullRequestTemplateSection> templateSections = parseTemplateSections(context.pullRequestTemplate());

        return new PullRequestDraftRepositoryGuideContext(
            context.contributing(),
            context.pullRequestTemplate(),
            context.mergedPullRequestSamples(),
            templateSections
        );
    }

    /**
     * 마크다운 PR 템플릿 텍스트를 분석하여 Description, Test Plan, Checklist 등 섹션 구조 목록으로 파싱합니다.
     */
    public static List<PullRequestTemplateSection> parseTemplateSections(String templateMarkdown) {
        return PullRequestTemplateSection.parseAll(templateMarkdown);
    }

    /**
     * 특정 시맨틱 타입(Description, Test Plan, Checklist 등)의 섹션을 조회합니다.
     */
    public static Optional<PullRequestTemplateSection> findSection(
        List<PullRequestTemplateSection> sections,
        PullRequestTemplateSection.SectionType type
    ) {
        if (sections == null) {
            return Optional.empty();
        }
        return sections.stream().filter(section -> section.type() == type).findFirst();
    }
}
