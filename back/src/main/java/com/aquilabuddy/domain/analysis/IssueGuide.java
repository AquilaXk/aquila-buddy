package com.aquilabuddy.domain.analysis;

import java.util.List;

/**
 * 선택한 이슈를 구현하기 위해 필요한 작업 가이드와 영향 범위를 담는 도메인 모델입니다.
 */
public record IssueGuide(
    String issueKey,
    String guideline,
    String pseudoCode,
    List<String> sideEffects
) {
}
