package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import java.util.List;

/**
 * [Incoming Port] 저장된 PR 초안의 번역 결과 목록을 조회하는 유스케이스 계약입니다.
 */
public interface ListPullRequestDraftTranslationsUseCase {

    List<PullRequestDraftTranslation> listTranslations(String draftId);
}
