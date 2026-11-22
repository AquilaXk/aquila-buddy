package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;

/**
 * 생성된 PR 초안을 요청 맥락과 함께 저장하기 위한 outbound port입니다.
 */
public interface SavePullRequestDraftPort {

    StoredPullRequestDraft savePullRequestDraft(PullRequestDraftRequest request, PullRequestDraft draft);
}
