package com.aquilabuddy.application.service.prdraft;

import com.aquilabuddy.application.port.in.prdraft.CreatePullRequestDraftTranslationUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftTranslationReadinessUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftTranslationSubmissionUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftTranslationTimelineUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftTranslationActivitiesUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftTranslationsUseCase;
import com.aquilabuddy.application.port.in.prdraft.TranslatePullRequestDraftUseCase;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.TranslatePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslateResult;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationActivity;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationReadiness;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationSubmission;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationTimeline;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 저장된 PR 초안의 번역 생성과 조회 유스케이스를 수행합니다.
 */
@Service
public class PullRequestDraftTranslationService implements
    CreatePullRequestDraftTranslationUseCase,
    ListPullRequestDraftTranslationsUseCase,
    ListPullRequestDraftTranslationActivitiesUseCase,
    GetPullRequestDraftTranslationReadinessUseCase,
    GetPullRequestDraftTranslationSubmissionUseCase,
    GetPullRequestDraftTranslationTimelineUseCase,
    TranslatePullRequestDraftUseCase {

    private final LoadPullRequestDraftPort loadPullRequestDraftPort;
    private final TranslatePullRequestDraftPort translatePullRequestDraftPort;
    private final SavePullRequestDraftTranslationPort savePullRequestDraftTranslationPort;
    private final LoadPullRequestDraftTranslationPort loadPullRequestDraftTranslationPort;

    public PullRequestDraftTranslationService(
        LoadPullRequestDraftPort loadPullRequestDraftPort,
        TranslatePullRequestDraftPort translatePullRequestDraftPort,
        SavePullRequestDraftTranslationPort savePullRequestDraftTranslationPort,
        LoadPullRequestDraftTranslationPort loadPullRequestDraftTranslationPort
    ) {
        this.loadPullRequestDraftPort = loadPullRequestDraftPort;
        this.translatePullRequestDraftPort = translatePullRequestDraftPort;
        this.savePullRequestDraftTranslationPort = savePullRequestDraftTranslationPort;
        this.loadPullRequestDraftTranslationPort = loadPullRequestDraftTranslationPort;
    }

    /**
     * 원본 초안이 존재할 때만 번역 adapter를 호출하고 결과를 저장합니다.
     */
    @Override
    public Optional<PullRequestDraftTranslation> createTranslation(String draftId, String targetLanguage) {
        String normalizedLanguage = normalizeTargetLanguage(targetLanguage);
        Optional<StoredPullRequestDraft> draft = loadPullRequestDraftPort.loadPullRequestDraft(draftId);

        if (draft.isEmpty()) {
            return Optional.empty();
        }

        PullRequestDraft translatedDraft = translatePullRequestDraftPort.translate(draft.get(), normalizedLanguage);
        return Optional.of(savePullRequestDraftTranslationPort.savePullRequestDraftTranslation(
            draftId,
            normalizedLanguage,
            translatedDraft
        ));
    }

    /**
     * 기존 단일 action 계약은 항상 영문 번역 결과와 GitHub PR 작성 URL을 함께 반환합니다.
     */
    @Override
    public Optional<PullRequestDraftTranslateResult> translate(String draftId) {
        Optional<StoredPullRequestDraft> draft = loadPullRequestDraftPort.loadPullRequestDraft(draftId);

        if (draft.isEmpty()) {
            return Optional.empty();
        }

        String targetLanguage = "en";
        PullRequestDraft translatedDraft = translatePullRequestDraftPort.translate(draft.get(), targetLanguage);
        PullRequestDraftTranslation translation = savePullRequestDraftTranslationPort.savePullRequestDraftTranslation(
            draftId,
            targetLanguage,
            translatedDraft
        );

        return Optional.of(PullRequestDraftTranslateResult.from(draft.get(), translation));
    }

    @Override
    public List<PullRequestDraftTranslation> listTranslations(String draftId) {
        return loadPullRequestDraftTranslationPort.loadPullRequestDraftTranslations(draftId);
    }

    @Override
    public List<PullRequestDraftTranslationActivity> listRecentTranslationActivities(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive.");
        }

        return loadPullRequestDraftTranslationPort.loadRecentPullRequestDraftTranslations(limit)
            .stream()
            .flatMap(translation -> loadPullRequestDraftPort.loadPullRequestDraft(translation.draftId())
                .map(draft -> PullRequestDraftTranslationActivity.from(draft, translation))
                .stream())
            .toList();
    }

    @Override
    public Optional<PullRequestDraftTranslationSubmission> getTranslationSubmission(
        String draftId,
        String translationId
    ) {
        return loadPullRequestDraftPort.loadPullRequestDraft(draftId)
            .flatMap(draft -> loadPullRequestDraftTranslationPort.loadPullRequestDraftTranslation(translationId)
                .filter(translation -> draft.id().equals(translation.draftId()))
                .map(translation -> PullRequestDraftTranslationSubmission.from(draft, translation)));
    }

    @Override
    public Optional<PullRequestDraftTranslationReadiness> getTranslationReadiness(
        String draftId,
        String translationId
    ) {
        return loadPullRequestDraftPort.loadPullRequestDraft(draftId)
            .flatMap(draft -> loadPullRequestDraftTranslationPort.loadPullRequestDraftTranslation(translationId)
                .filter(translation -> draft.id().equals(translation.draftId()))
                .map(translation -> PullRequestDraftTranslationReadiness.from(draft, translation)));
    }

    @Override
    public Optional<PullRequestDraftTranslationTimeline> getTranslationTimeline(
        String draftId,
        String translationId
    ) {
        return loadPullRequestDraftPort.loadPullRequestDraft(draftId)
            .flatMap(draft -> loadPullRequestDraftTranslationPort.loadPullRequestDraftTranslation(translationId)
                .filter(translation -> draft.id().equals(translation.draftId()))
                .map(translation -> PullRequestDraftTranslationTimeline.from(draft, translation)));
    }

    private String normalizeTargetLanguage(String targetLanguage) {
        if (targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("targetLanguage must not be blank.");
        }

        return targetLanguage.trim().toLowerCase(Locale.ROOT);
    }
}
