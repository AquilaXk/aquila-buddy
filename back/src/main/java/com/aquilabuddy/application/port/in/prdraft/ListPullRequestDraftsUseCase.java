package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.List;

/**
 * [Incoming Port] 최근 PR 초안 목록을 조회하는 유스케이스 계약입니다.
 */
public interface ListPullRequestDraftsUseCase {

    List<StoredPullRequestDraft> listRecentPullRequestDrafts();
}
