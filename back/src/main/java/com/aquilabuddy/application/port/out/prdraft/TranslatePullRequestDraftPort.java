package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;

/**
 * 저장된 PR 초안 내용을 대상 언어의 PR 초안으로 변환하기 위한 outbound port입니다.
 */
public interface TranslatePullRequestDraftPort {

    PullRequestDraft translate(StoredPullRequestDraft draft, String targetLanguage);
}
