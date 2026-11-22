package com.aquilabuddy.adapter.out.ai.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiRecommendedIssueSummaryAdapterTest {

    @Test
    void summarizesRecommendedIssueWithIssueSummaryUseCase() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            SUMMARY: 템플릿 입력값이 공백만 있어도 검증을 통과해 나중에 Thymeleaf 처리 단계에서 실패하는 문제입니다.
            REASON: bug와 good first issue 라벨이 있고 검증 조건이 명확해 작은 테스트부터 시작하기 좋은 후보입니다.
            """);
        AiRecommendedIssueSummaryAdapter adapter = new AiRecommendedIssueSummaryAdapter(aiClient);

        List<RecommendedIssue> result = adapter.summarizeRecommendedIssues(List.of(issue()));

        assertThat(result.getFirst().summary())
            .isEqualTo("템플릿 입력값이 공백만 있어도 검증을 통과해 나중에 Thymeleaf 처리 단계에서 실패하는 문제입니다.");
        assertThat(result.getFirst().reason())
            .isEqualTo("bug와 good first issue 라벨이 있고 검증 조건이 명확해 작은 테스트부터 시작하기 좋은 후보입니다.");
        assertThat(aiClient.useCase).isEqualTo(AiCompletionUseCase.ISSUE_SUMMARY);
        assertThat(aiClient.systemPrompt)
            .contains("한국어")
            .contains("SUMMARY:")
            .contains("REASON:");
        assertThat(aiClient.userPrompt)
            .contains("Repository: suzumiyaaoba/thymeleaf-snapshot")
            .contains("Title: Template/inlineTemplate validation uses isEmpty() instead of isBlank()")
            .contains("Source summary: Problem Snapshot validates by checking isEmpty()")
            .contains("Source recommendation reason: GitHub Search에서 Good First Issue 조건으로 수집된 후보입니다.");
    }

    @Test
    void removesDisplayLabelFromAiSummary() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            SUMMARY: 한국어 요약: 템플릿 값이 공백만 있어도 검증을 통과해 나중에 Thymeleaf 렌더링 단계에서 실패하는 문제입니다.
            REASON: bug와 good first issue 라벨이 있어 실패 조건을 테스트로 확인하며 작게 시작하기 좋은 후보입니다.
            """);
        AiRecommendedIssueSummaryAdapter adapter = new AiRecommendedIssueSummaryAdapter(aiClient);

        List<RecommendedIssue> result = adapter.summarizeRecommendedIssues(List.of(issue()));

        assertThat(result.getFirst().summary())
            .isEqualTo("템플릿 값이 공백만 있어도 검증을 통과해 나중에 Thymeleaf 렌더링 단계에서 실패하는 문제입니다.");
    }

    @Test
    void usesRuleBasedKoreanSummaryAndReasonWhenAiResponseIsMalformed() {
        AiRecommendedIssueSummaryAdapter adapter = new AiRecommendedIssueSummaryAdapter(new CapturingAiClient(" "));

        List<RecommendedIssue> result = adapter.summarizeRecommendedIssues(List.of(issue()));

        assertThat(result.getFirst().summary())
            .doesNotStartWith("한국어 요약:")
            .contains("문제")
            .contains("isEmpty()")
            .contains("isBlank()")
            .doesNotContain("Problem Snapshot");
        assertThat(result.getFirst().reason())
            .contains("good first issue")
            .doesNotContain("GitHub Search");
    }

    @Test
    void usesRuleBasedKoreanSummaryAndReasonWhenAiClientFails() {
        AiRecommendedIssueSummaryAdapter adapter = new AiRecommendedIssueSummaryAdapter(new ThrowingAiClient());

        List<RecommendedIssue> result = adapter.summarizeRecommendedIssues(List.of(issue()));

        assertThat(result.getFirst().summary())
            .doesNotStartWith("한국어 요약:")
            .contains("문제")
            .contains("isEmpty()")
            .doesNotContain("Problem Snapshot");
        assertThat(result.getFirst().reason())
            .contains("검증")
            .doesNotContain("GitHub Search");
    }

    private static RecommendedIssue issue() {
        return new RecommendedIssue(
            "987654321",
            "suzumiyaaoba/thymeleaf-snapshot",
            6,
            "Template/inlineTemplate validation uses isEmpty() instead of isBlank()",
            "Problem Snapshot validates by checking isEmpty(), so whitespace input passes and fails later.",
            0.0,
            List.of("bug", "good first issue"),
            "OPEN",
            "GitHub Search에서 Good First Issue 조건으로 수집된 후보입니다."
        );
    }

    private static class CapturingAiClient implements AiClient {

        private final String response;
        private AiCompletionUseCase useCase;
        private String systemPrompt;
        private String userPrompt;

        private CapturingAiClient(String response) {
            this.response = response;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return response;
        }

        @Override
        public String complete(AiCompletionUseCase useCase, String systemPrompt, String userPrompt) {
            this.useCase = useCase;
            return complete(systemPrompt, userPrompt);
        }
    }

    private static class ThrowingAiClient implements AiClient {

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            throw new IllegalStateException("AI unavailable");
        }

        @Override
        public String complete(AiCompletionUseCase useCase, String systemPrompt, String userPrompt) {
            throw new IllegalStateException("AI unavailable");
        }
    }
}
