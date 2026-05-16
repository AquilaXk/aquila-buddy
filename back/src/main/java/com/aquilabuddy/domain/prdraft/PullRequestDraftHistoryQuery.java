package com.aquilabuddy.domain.prdraft;

/**
 * PR 초안 이력을 페이지 단위로 조회하기 위한 요청 값입니다.
 */
public record PullRequestDraftHistoryQuery(int page, int size) {

    public PullRequestDraftHistoryQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상이어야 합니다.");
        }
    }
}
