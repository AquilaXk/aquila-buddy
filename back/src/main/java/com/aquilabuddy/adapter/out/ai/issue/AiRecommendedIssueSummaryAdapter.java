package com.aquilabuddy.adapter.out.ai.issue;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
import com.aquilabuddy.application.port.out.issue.SummarizeRecommendedIssuePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * AI completion으로 추천 카드에 표시할 한국어 의미 요약을 생성합니다.
 */
@Component
@ConditionalOnExpression("'${app.ai.provider:fixture}' == 'openai-compatible' || '${app.ai.provider:fixture}' == 'vertex-ai'")
public class AiRecommendedIssueSummaryAdapter implements SummarizeRecommendedIssuePort {

    private static final int SUMMARY_MAX_LENGTH = 220;
    private static final int REASON_MAX_LENGTH = 220;
    private static final String SUMMARY_MARKER = "SUMMARY:";
    private static final String REASON_MARKER = "REASON:";
    private static final Pattern SUMMARY_DISPLAY_LABEL = Pattern.compile(
        "^(?:한국어\\s*요약|요약|summary)\\s*[:：]\\s*",
        Pattern.CASE_INSENSITIVE
    );
    private static final String SYSTEM_PROMPT = """
        너는 한국어 사용자를 위한 오픈소스 이슈 추천 카드 요약 작성자다.
        입력된 GitHub 이슈 제목, 저장소, 라벨, 원문 요약, 기존 추천 이유만 사용한다.
        SUMMARY는 원문이 영어이거나 다른 언어여도 한국어로 의미를 풀어서 쓴다.
        SUMMARY는 문제 상황과 기여자가 해야 할 일을 1-2문장으로 설명한다.
        REASON은 수집 경로가 아니라 라벨, 난이도, 수정 범위, 검증 가능성 같은 추천 근거를 설명한다.
        함수명, 클래스명, 파일명, API 이름 같은 기술 식별자는 번역하지 말고 보존한다.
        GitHub Search에서 수집했다는 말, 마크다운, 목록, 따옴표, 과장 표현을 쓰지 않는다.
        반드시 다음 형식만 반환한다.
        SUMMARY: <한국어 1-2문장, 220자 이내>
        REASON: <한국어 추천 근거 1문장, 220자 이내>
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
            CardCopy cardCopy = parseCardCopy(response);
            return withCardCopy(issue, cardCopy);
        } catch (RuntimeException exception) {
            return withCardCopy(issue, fallbackCardCopy(issue));
        }
    }

    private static String buildPrompt(RecommendedIssue issue) {
        List<String> lines = new ArrayList<>(List.of(
            "Repository: " + issue.repoFullName(),
            "Issue number: #" + issue.issueNumber(),
            "Title: " + issue.title(),
            "Labels: " + String.join(", ", issue.labels()),
            "Source summary: " + issue.summary(),
            "Source recommendation reason: " + issue.reason()
        ));
        return String.join("\n", lines);
    }

    private static CardCopy parseCardCopy(String response) {
        String normalized = Objects.requireNonNull(response, "AI response must not be null.").trim();
        int summaryIndex = normalized.indexOf(SUMMARY_MARKER);
        int reasonIndex = normalized.indexOf(REASON_MARKER);
        if (summaryIndex < 0 || reasonIndex < 0 || reasonIndex <= summaryIndex) {
            throw new IllegalStateException("AI issue summary response must contain SUMMARY and REASON markers.");
        }
        String summary = sanitizeCopy(
            normalized.substring(summaryIndex + SUMMARY_MARKER.length(), reasonIndex),
            SUMMARY_MAX_LENGTH,
            "summary"
        );
        String reason = sanitizeCopy(
            normalized.substring(reasonIndex + REASON_MARKER.length()),
            REASON_MAX_LENGTH,
            "reason"
        );
        return new CardCopy(summary, reason);
    }

    private static String sanitizeCopy(String value, int maxLength, String fieldName) {
        String sanitized = value
            .replaceAll("(?m)^[-*]\\s+", "")
            .replaceAll("\\*\\*", "")
            .replaceAll("\\s+", " ")
            .trim();
        if ("summary".equals(fieldName)) {
            sanitized = SUMMARY_DISPLAY_LABEL.matcher(sanitized).replaceFirst("").trim();
        }
        if (sanitized.isBlank()) {
            throw new IllegalStateException("AI issue " + fieldName + " response must not be blank.");
        }
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }

    private static CardCopy fallbackCardCopy(RecommendedIssue issue) {
        String title = issue.title();
        String summary = title
            + " 문제를 다루며, 원문 설명을 바탕으로 실패 조건을 재현하고 작은 수정 범위에서 검증해야 합니다.";
        String reason = fallbackReason(issue);
        return new CardCopy(
            truncate(summary, SUMMARY_MAX_LENGTH),
            truncate(reason, REASON_MAX_LENGTH)
        );
    }

    private static String fallbackReason(RecommendedIssue issue) {
        boolean goodFirstIssue = issue.labels().stream()
            .anyMatch(label -> "good first issue".equalsIgnoreCase(label));
        boolean bug = issue.labels().stream()
            .anyMatch(label -> "bug".equalsIgnoreCase(label));
        if (goodFirstIssue && bug) {
            return "bug와 good first issue 라벨이 있어 실패 조건을 테스트로 확인하며 작게 시작하기 좋은 검증 후보입니다.";
        }
        if (goodFirstIssue) {
            return "good first issue 라벨이 있어 변경 범위를 작게 잡고 기여 절차를 익히기 좋은 후보입니다.";
        }
        if (bug) {
            return "bug 라벨이 있어 재현 조건과 수정 결과를 비교하며 검증하기 좋은 후보입니다.";
        }
        return "라벨과 이슈 설명 기준으로 수정 범위를 확인하며 단계적으로 기여를 준비하기 좋은 후보입니다.";
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static RecommendedIssue withCardCopy(RecommendedIssue issue, CardCopy cardCopy) {
        return new RecommendedIssue(
            issue.id(),
            issue.repoFullName(),
            issue.issueNumber(),
            issue.title(),
            cardCopy.summary(),
            issue.score(),
            issue.labels(),
            issue.status(),
            cardCopy.reason()
        );
    }

    private record CardCopy(String summary, String reason) {
    }
}
