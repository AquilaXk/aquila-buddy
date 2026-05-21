package com.aquilabuddy.adapter.out.runtime.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleBasedRecommendedIssueSummaryAdapterTest {

    @Test
    void convertsRawGithubSearchCopyIntoKoreanCardCopy() {
        RuleBasedRecommendedIssueSummaryAdapter adapter = new RuleBasedRecommendedIssueSummaryAdapter();
        RecommendedIssue raw = new RecommendedIssue(
            "issue-1",
            "suzumiyaaoba/thymeleaf-snapshot",
            6,
            "Template/inlineTemplate validation uses isEmpty() instead of isBlank()",
            "Problem Snapshot validates that whitespace input passes isEmpty() and fails later.",
            0.9,
            List.of("bug", "good first issue"),
            "OPEN",
            "GitHub Search에서 Good First Issue 조건으로 수집된 후보입니다.",
            "legacy"
        );

        RecommendedIssue result = adapter.summarizeRecommendedIssues(List.of(raw)).getFirst();

        assertThat(result.summary())
            .contains("한국어 요약")
            .contains("Template/inlineTemplate")
            .doesNotContain("Problem Snapshot");
        assertThat(result.reason())
            .contains("bug")
            .doesNotContain("GitHub Search")
            .doesNotContain("수집된 후보");
        assertThat(result.copyOutputVersion())
            .isEqualTo(RecommendedIssue.CURRENT_COPY_OUTPUT_VERSION);
    }
}
