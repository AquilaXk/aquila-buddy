package com.aquilabuddy.adapter.out.persistence.analysis;

import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftAnalysisContextPort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.prdraft.PullRequestDraftAnalysisContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * PR 초안 생성용 분석 guide context를 저장된 추천 이슈와 분석 결과에서 조합합니다.
 */
@Component
public class PullRequestDraftAnalysisContextPersistenceAdapter implements LoadPullRequestDraftAnalysisContextPort {

    private final FindRecommendedIssuePort findRecommendedIssuePort;
    private final LoadIssueGuidePort loadIssueGuidePort;

    public PullRequestDraftAnalysisContextPersistenceAdapter(
        FindRecommendedIssuePort findRecommendedIssuePort,
        LoadIssueGuidePort loadIssueGuidePort
    ) {
        this.findRecommendedIssuePort = findRecommendedIssuePort;
        this.loadIssueGuidePort = loadIssueGuidePort;
    }

    @Override
    public PullRequestDraftAnalysisContext loadAnalysisContext(PullRequestDraftRequest request) {
        return findRecommendedIssuePort
            .findByRepoFullNameAndIssueNumber(request.upstreamRepo(), request.githubIssueNumber())
            .map(this::loadAnalysisContext)
            .orElseGet(PullRequestDraftAnalysisContext::empty);
    }

    private PullRequestDraftAnalysisContext loadAnalysisContext(RecommendedIssue issue) {
        try {
            IssueGuide guide = loadIssueGuidePort.loadIssueGuide(issue.id());
            return new PullRequestDraftAnalysisContext(
                guide.guideline(),
                guide.pseudoCode(),
                guide.sideEffects()
            );
        } catch (NoSuchElementException ignored) {
            // 분석 guide가 아직 생성되지 않은 추천 이슈도 PR 초안 생성 자체는 진행할 수 있어야 합니다.
            return PullRequestDraftAnalysisContext.empty();
        }
    }
}
