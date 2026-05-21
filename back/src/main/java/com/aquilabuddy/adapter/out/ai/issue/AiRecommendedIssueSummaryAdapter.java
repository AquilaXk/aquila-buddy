package com.aquilabuddy.adapter.out.ai.issue;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
import com.aquilabuddy.application.port.out.issue.SummarizeRecommendedIssuePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * AI completion으로 추천 카드에 표시할 한국어 의미 요약을 생성합니다.
 */
@Component
@ConditionalOnExpression("'${app.ai.provider:fixture}' == 'openai-compatible' || '${app.ai.provider:fixture}' == 'vertex-ai'")
public class AiRecommendedIssueSummaryAdapter implements SummarizeRecommendedIssuePort {

    private static final int SUMMARY_MAX_LENGTH = 220;
    private static final String SUMMARY_MARKER = "SUMMARY:";
    private static final String SYSTEM_PROMPT = """
        너는 한국어 사용자를 위한 오픈소스 이슈 추천 카드 요약 작성자다.
        입력된 GitHub 이슈 제목, 저장소, 라벨, 원문 요약만 사용한다.
        원문이 영어이거나 다른 언어여도 한국어로 의미를 풀어서 쓴다.
        문제 상황과 기여자가 해야 할 일을 1-2문장으로 설명한다.
        함수명, 클래스명, 파일명, API 이름 같은 기술 식별자는 번역하지 말고 보존한다.
        마크다운, 목록, 따옴표, 과장 표현을 쓰지 않는다.
        반드시 다음 형식만 반환한다.
        SUMMARY: <한국어 1-2문장, 220자 이내>
        """;

    private final AiClient aiClient;

    public AiRecommendedIssueSummaryAdapter(AiClient aiClient) {
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null.");
    }

    @Override
    public List<RecommendedIssue> summarizeRecommendedIssues(List<RecommendedIssue> issues) {
        return List.copyOf(issues).stream()
            .map(this::summarizeSafely)
            .toList();
    }

    private RecommendedIssue summarizeSafely(RecommendedIssue issue) {
        try {
            String response = aiClient.complete(AiCompletionUseCase.ISSUE_SUMMARY, SYSTEM_PROMPT, buildPrompt(issue));
            return withSummary(issue, parseSummary(response));
        } catch (RuntimeException exception) {
            return issue;
        }
    }

    private static String buildPrompt(RecommendedIssue issue) {
        List<String> lines = new ArrayList<>(List.of(
            "Repository: " + issue.repoFullName(),
            "Issue number: #" + issue.issueNumber(),
            "Title: " + issue.title(),
            "Labels: " + String.join(", ", issue.labels()),
            "Source summary: " + issue.summary()
        ));
        return String.join("\n", lines);
    }

    private static String parseSummary(String response) {
        String normalized = Objects.requireNonNull(response, "AI response must not be null.").trim();
        if (normalized.startsWith(SUMMARY_MARKER)) {
            normalized = normalized.substring(SUMMARY_MARKER.length()).trim();
        }
        String summary = normalized
            .replaceAll("(?m)^[-*]\\s+", "")
            .replaceAll("\\s+", " ")
            .trim();
        if (summary.isBlank()) {
            throw new IllegalStateException("AI issue summary response must not be blank.");
        }
        return summary.length() <= SUMMARY_MAX_LENGTH
            ? summary
            : summary.substring(0, SUMMARY_MAX_LENGTH);
    }

    private static RecommendedIssue withSummary(RecommendedIssue issue, String summary) {
        return new RecommendedIssue(
            issue.id(),
            issue.repoFullName(),
            issue.issueNumber(),
            issue.title(),
            summary,
            issue.score(),
            issue.labels(),
            issue.status(),
            issue.reason()
        );
    }
}
