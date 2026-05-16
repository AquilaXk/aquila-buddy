package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslateResult;
import java.util.Optional;

/**
 * [Incoming Port] 저장된 PR 초안을 단일 action으로 영문 번역하는 호환 유스케이스 계약입니다.
 */
public interface TranslatePullRequestDraftUseCase {

    Optional<PullRequestDraftTranslateResult> translate(String draftId);
}
