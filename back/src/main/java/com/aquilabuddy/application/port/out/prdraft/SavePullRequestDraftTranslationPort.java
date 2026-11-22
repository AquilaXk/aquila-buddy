package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;

/**
 * PR 초안 번역 결과를 저장하기 위한 outbound port입니다.
 */
public interface SavePullRequestDraftTranslationPort {

    PullRequestDraftTranslation savePullRequestDraftTranslation(
        String draftId,
        String targetLanguage,
        PullRequestDraft translatedDraft
    );
}
