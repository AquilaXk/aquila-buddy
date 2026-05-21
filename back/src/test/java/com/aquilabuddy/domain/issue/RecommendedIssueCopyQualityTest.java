package com.aquilabuddy.domain.issue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendedIssueCopyQualityTest {

    @Test
    void marksCurrentCopyWithDisplaySummaryLabelForRefresh() {
        RecommendedIssue issue = new RecommendedIssue(
            "issue-1",
            "suzumiyaaoba/thymeleaf-snapshot",
            6,
            "Template/inlineTemplate validation uses isEmpty() instead of isBlank()",
            "한국어 요약: 공백만 들어온 template 값이 isEmpty() 검증을 통과해 뒤늦게 실패하는 문제입니다.",
            0.9,
            List.of("bug", "good first issue"),
            "OPEN",
            "bug 라벨과 good first issue 범위가 맞아 검증 조건을 작게 재현하고 수정하기 좋습니다.",
            RecommendedIssue.CURRENT_COPY_OUTPUT_VERSION
        );

        assertThat(RecommendedIssueCopyQuality.needsRefresh(issue)).isTrue();
        assertThat(RecommendedIssueCopyQuality.refreshReasons(issue))
            .contains("summary has display label");
    }
}
