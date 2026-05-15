package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import java.util.List;
import java.util.Optional;

/**
 * 저장된 PR 초안 번역 결과를 조회하기 위한 outbound port입니다.
 */
public interface LoadPullRequestDraftTranslationPort {

    List<PullRequestDraftTranslation> loadPullRequestDraftTranslations(String draftId);

    Optional<PullRequestDraftTranslation> loadPullRequestDraftTranslation(String id);
}
