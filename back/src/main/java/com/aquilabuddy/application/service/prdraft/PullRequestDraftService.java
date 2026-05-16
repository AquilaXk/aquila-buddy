package com.aquilabuddy.application.service.prdraft;

import com.aquilabuddy.application.port.in.prdraft.CreatePullRequestDraftUseCase;
import com.aquilabuddy.application.port.in.prdraft.DeletePullRequestDraftUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftContributionReportUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftReadinessUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftSubmissionUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftTimelineUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftHistoryUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftsUseCase;
import com.aquilabuddy.application.port.in.prdraft.UpdatePullRequestDraftUseCase;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.application.port.out.prdraft.DeletePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftAnalysisContextPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftDiffPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftHistoryPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftRepositoryGuideContextPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.UpdatePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftContributionReport;
import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryPage;
import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryQuery;
import com.aquilabuddy.domain.prdraft.PullRequestDraftIssueContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftReadiness;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftSubmission;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTimeline;
import com.aquilabuddy.domain.prdraft.PullRequestDraftUpdateRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * [Application Service] PR 초안 생성 유스케이스를 수행합니다.
 *
 * <p>초안 생성 방식은 outbound port 뒤에 두어, 이후 AI adapter로 바뀌어도 유스케이스 경계는 유지합니다.
 */
@Service
public class PullRequestDraftService implements
    CreatePullRequestDraftUseCase,
    ListPullRequestDraftsUseCase,
    ListPullRequestDraftHistoryUseCase,
    GetPullRequestDraftUseCase,
    GetPullRequestDraftContributionReportUseCase,
    GetPullRequestDraftReadinessUseCase,
    GetPullRequestDraftTimelineUseCase,
    GetPullRequestDraftSubmissionUseCase,
    UpdatePullRequestDraftUseCase,
    DeletePullRequestDraftUseCase {

    private final GeneratePullRequestDraftPort generatePullRequestDraftPort;
    private final LoadPullRequestDraftDiffPort loadPullRequestDraftDiffPort;
    private final LoadPullRequestDraftRepositoryGuideContextPort loadPullRequestDraftRepositoryGuideContextPort;
    private final FindRecommendedIssuePort findRecommendedIssuePort;
    private final LoadPullRequestDraftAnalysisContextPort loadPullRequestDraftAnalysisContextPort;
    private final SavePullRequestDraftPort savePullRequestDraftPort;
    private final LoadPullRequestDraftPort loadPullRequestDraftPort;
    private final LoadPullRequestDraftHistoryPort loadPullRequestDraftHistoryPort;
    private final UpdatePullRequestDraftPort updatePullRequestDraftPort;
    private final DeletePullRequestDraftPort deletePullRequestDraftPort;
    private final LoadPullRequestDraftTranslationPort loadPullRequestDraftTranslationPort;

    public PullRequestDraftService(
        GeneratePullRequestDraftPort generatePullRequestDraftPort,
        LoadPullRequestDraftDiffPort loadPullRequestDraftDiffPort,
        LoadPullRequestDraftRepositoryGuideContextPort loadPullRequestDraftRepositoryGuideContextPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        LoadPullRequestDraftAnalysisContextPort loadPullRequestDraftAnalysisContextPort,
        SavePullRequestDraftPort savePullRequestDraftPort,
        LoadPullRequestDraftPort loadPullRequestDraftPort,
        LoadPullRequestDraftHistoryPort loadPullRequestDraftHistoryPort,
        UpdatePullRequestDraftPort updatePullRequestDraftPort,
        DeletePullRequestDraftPort deletePullRequestDraftPort,
        LoadPullRequestDraftTranslationPort loadPullRequestDraftTranslationPort
    ) {
        this.generatePullRequestDraftPort = generatePullRequestDraftPort;
        this.loadPullRequestDraftDiffPort = loadPullRequestDraftDiffPort;
        this.loadPullRequestDraftRepositoryGuideContextPort = loadPullRequestDraftRepositoryGuideContextPort;
        this.findRecommendedIssuePort = findRecommendedIssuePort;
        this.loadPullRequestDraftAnalysisContextPort = loadPullRequestDraftAnalysisContextPort;
        this.savePullRequestDraftPort = savePullRequestDraftPort;
        this.loadPullRequestDraftPort = loadPullRequestDraftPort;
        this.loadPullRequestDraftHistoryPort = loadPullRequestDraftHistoryPort;
        this.updatePullRequestDraftPort = updatePullRequestDraftPort;
        this.deletePullRequestDraftPort = deletePullRequestDraftPort;
        this.loadPullRequestDraftTranslationPort = loadPullRequestDraftTranslationPort;
    }

    /**
     * 입력 맥락으로 PR 초안을 생성한 뒤 저장된 초안 식별자를 포함해 반환합니다.
     */
    @Override
    public StoredPullRequestDraft create(PullRequestDraftRequest request) {
        PullRequestDraftRequest resolvedRequest = resolveAnalysisContext(
            resolveIssueContext(resolveRepositoryGuideContext(resolveDiffContent(request)))
        );
        PullRequestDraft generated = generatePullRequestDraftPort.generate(resolvedRequest);
        return savePullRequestDraftPort.savePullRequestDraft(resolvedRequest, generated);
    }

    private PullRequestDraftRequest resolveDiffContent(PullRequestDraftRequest request) {
        if (request.hasDiffContent()) {
            return request;
        }

        // 서버 수집 diff가 없으면 생성/저장 모두 같은 맥락을 쓰도록 도메인 요청을 보강합니다.
        return request.withDiffContent(loadPullRequestDraftDiffPort.loadDiff(request));
    }

    private PullRequestDraftRequest resolveRepositoryGuideContext(PullRequestDraftRequest request) {
        return request.withRepositoryGuideContext(
            loadPullRequestDraftRepositoryGuideContextPort.loadRepositoryGuideContext(request)
        );
    }

    private PullRequestDraftRequest resolveIssueContext(PullRequestDraftRequest request) {
        return findRecommendedIssuePort
            .findByRepoFullNameAndIssueNumber(request.upstreamRepo(), request.githubIssueNumber())
            .map(issue -> request.withIssueContext(new PullRequestDraftIssueContext(
                issue.title(),
                issue.summary(),
                issue.reason()
            )))
            .orElse(request);
    }

    private PullRequestDraftRequest resolveAnalysisContext(PullRequestDraftRequest request) {
        return request.withAnalysisContext(loadPullRequestDraftAnalysisContextPort.loadAnalysisContext(request));
    }

    @Override
    public List<StoredPullRequestDraft> listRecentPullRequestDrafts() {
        return loadPullRequestDraftPort.loadRecentPullRequestDrafts();
    }

    @Override
    public PullRequestDraftHistoryPage listPullRequestDraftHistory(PullRequestDraftHistoryQuery query) {
        return loadPullRequestDraftHistoryPort.loadPullRequestDraftHistory(query);
    }

    @Override
    public Optional<StoredPullRequestDraft> getPullRequestDraft(String id) {
        return loadPullRequestDraftPort.loadPullRequestDraft(id);
    }

    @Override
    public Optional<PullRequestDraftReadiness> getReadiness(String id) {
        return getPullRequestDraft(id).map(PullRequestDraftReadiness::from);
    }

    @Override
    public Optional<PullRequestDraftContributionReport> getContributionReport(String id) {
        return getPullRequestDraft(id)
            .map(draft -> PullRequestDraftContributionReport.from(
                draft,
                loadPullRequestDraftTranslationPort.loadPullRequestDraftTranslations(draft.id())
            ));
    }

    @Override
    public Optional<PullRequestDraftTimeline> getTimeline(String id) {
        return getPullRequestDraft(id).map(PullRequestDraftTimeline::from);
    }

    @Override
    public Optional<PullRequestDraftSubmission> getSubmission(String id) {
        return getPullRequestDraft(id).map(PullRequestDraftSubmission::from);
    }

    @Override
    public Optional<StoredPullRequestDraft> updatePullRequestDraft(String id, PullRequestDraftUpdateRequest request) {
        return updatePullRequestDraftPort.updatePullRequestDraft(id, request);
    }

    @Override
    public boolean deletePullRequestDraft(String id) {
        return deletePullRequestDraftPort.deletePullRequestDraft(id);
    }
}
