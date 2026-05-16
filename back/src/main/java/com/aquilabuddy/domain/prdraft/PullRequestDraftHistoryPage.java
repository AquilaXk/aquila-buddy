package com.aquilabuddy.domain.prdraft;

import java.util.List;

/**
 * PR 초안 이력 목록과 페이지 메타데이터를 함께 전달하는 응답 모델입니다.
 */
public record PullRequestDraftHistoryPage(
    List<StoredPullRequestDraft> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {

    public PullRequestDraftHistoryPage {
        content = List.copyOf(content);
    }
}
