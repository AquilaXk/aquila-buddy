package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.List;
import java.util.Optional;

/**
 * 저장된 PR 초안 목록과 상세를 읽기 위한 outbound port입니다.
 */
public interface LoadPullRequestDraftPort {

    List<StoredPullRequestDraft> loadRecentPullRequestDrafts();

    Optional<StoredPullRequestDraft> loadPullRequestDraft(String id);
}
