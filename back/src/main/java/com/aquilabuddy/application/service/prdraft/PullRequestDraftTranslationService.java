package com.aquilabuddy.application.service.prdraft;

import com.aquilabuddy.application.port.in.prdraft.CreatePullRequestDraftTranslationUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftTranslationsUseCase;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.TranslatePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
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
    ListPullRequestDraftTranslationsUseCase {

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

    @Override
    public List<PullRequestDraftTranslation> listTranslations(String draftId) {
        return loadPullRequestDraftTranslationPort.loadPullRequestDraftTranslations(draftId);
    }

    private String normalizeTargetLanguage(String targetLanguage) {
        if (targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("targetLanguage must not be blank.");
        }

        return targetLanguage.trim().toLowerCase(Locale.ROOT);
    }
}
