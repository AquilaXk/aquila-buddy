package com.aquilabuddy.domain.analysis;

import java.util.Objects;

/**
 * 이슈 가이드에서 의사코드만 독립 조회할 때 사용하는 projection 모델입니다.
 */
public record IssuePseudoCode(
    String issueKey,
    String pseudoCode
) {

    public static IssuePseudoCode from(IssueGuide guide) {
        Objects.requireNonNull(guide, "guide must not be null");
        return new IssuePseudoCode(guide.issueKey(), guide.pseudoCode());
    }
}
