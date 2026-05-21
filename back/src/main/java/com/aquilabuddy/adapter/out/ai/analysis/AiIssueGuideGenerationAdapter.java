package com.aquilabuddy.adapter.out.ai.analysis;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * AI completion 결과를 issue guide 생성 outbound port로 연결합니다.
 */
@Component
@ConditionalOnExpression("'${app.ai.provider:fixture}' == 'openai-compatible' || '${app.ai.provider:fixture}' == 'vertex-ai'")
public class AiIssueGuideGenerationAdapter implements GenerateIssueGuidePort {

    private static final String SYSTEM_PROMPT = """
        You are an issue guide generator for an open-source contribution assistant.
        Write GUIDELINE, PSEUDO_CODE, and SIDE_EFFECTS in Korean.
        Use only the provided issue metadata and repository code context.
        Base GUIDELINE, PSEUDO_CODE, and SIDE_EFFECTS on repository code context when it is present.
        When repository code context is present, mention at least one exact file name, class, method, function, or config key from that context.
        Do not invent validation, error handling, database, UI, or test behavior that is not visible in the issue metadata or code context.
        If the provided code context is too thin to determine the exact implementation point, say what must be inspected next instead of pretending it is known.
        GUIDELINE must include the target file or symbol, the issue-specific failure condition, the intended edit direction, and the first verification.
        Do not write generic GUIDELINE text such as "follow the validation flow" unless it names the concrete failing condition and edit.
        PSEUDO_CODE must be language-neutral pseudocode, not a numbered explanation checklist.
        PSEUDO_CODE must use code-like lines such as GIVEN, WHEN, IF, CALL, RETURN, ASSERT, or VERIFY.
        Return exactly this marker format.
        GUIDELINE: <2-4 concrete Korean sentences with target, failure condition, edit direction, and first verification>
        PSEUDO_CODE:
        <4-8 lines of code-like pseudocode grounded in the target file or symbol; do not number the lines>
        SIDE_EFFECTS:
        - <2-4 concrete side effect or verification concern>
        """;

    private final AiClient aiClient;
    private final GenerateIssueGuidePort fallbackGenerator;

    @Autowired
    public AiIssueGuideGenerationAdapter(AiClient aiClient) {
        this(aiClient, new RuleBasedIssueGuideGenerationAdapter());
    }

    AiIssueGuideGenerationAdapter(AiClient aiClient, GenerateIssueGuidePort fallbackGenerator) {
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null.");
        this.fallbackGenerator = Objects.requireNonNull(fallbackGenerator, "fallbackGenerator must not be null.");
    }

    @Override
    public IssueGuide generateIssueGuide(RecommendedIssue issue) {
        return generateIssueGuide(issue, RepositoryCodeContext.empty());
    }

    @Override
    public IssueGuide generateIssueGuide(RecommendedIssue issue, RepositoryCodeContext codeContext) {
        RepositoryCodeContext safeContext = codeContext == null ? RepositoryCodeContext.empty() : codeContext;
        try {
            return parseGuide(
                issue,
                safeContext,
                aiClient.complete(AiCompletionUseCase.ISSUE_GUIDE, SYSTEM_PROMPT, buildPrompt(issue, safeContext))
            );
        } catch (RuntimeException exception) {
            // 운영 AI 응답 형식이 흔들려도 사용자는 제출 가능한 기본 가이드를 받아야 합니다.
            return fallbackGenerator.generateIssueGuide(issue, safeContext);
        }
    }

    private static String buildPrompt(RecommendedIssue issue, RepositoryCodeContext codeContext) {
        List<String> lines = new ArrayList<>(List.of(
            "Issue ID: " + issue.id(),
            "Repository: " + issue.repoFullName(),
            "Issue number: #" + issue.issueNumber(),
            "Title: " + issue.title(),
            "Summary: " + issue.summary(),
            "Labels: " + String.join(", ", issue.labels()),
            "Status: " + issue.status(),
            "Recommendation reason: " + issue.reason()
        ));
        RepositoryCodeContext safeContext = codeContext == null ? RepositoryCodeContext.empty() : codeContext;
        lines.addAll(safeContext.promptSummaryLines());
        return String.join("\n", lines);
    }

    private static IssueGuide parseGuide(RecommendedIssue issue, RepositoryCodeContext codeContext, String response) {
        String normalized = Objects.requireNonNull(response, "AI response must not be null.").trim();
        int guidelineIndex = normalized.indexOf("GUIDELINE:");
        int pseudoCodeIndex = normalized.indexOf("PSEUDO_CODE:");
        int sideEffectsIndex = normalized.indexOf("SIDE_EFFECTS:");
        if (guidelineIndex < 0 || pseudoCodeIndex < 0 || sideEffectsIndex < 0
            || pseudoCodeIndex <= guidelineIndex || sideEffectsIndex <= pseudoCodeIndex) {
            throw new IllegalStateException("AI issue guide response must contain GUIDELINE, PSEUDO_CODE, and SIDE_EFFECTS markers.");
        }

        String guideline = normalized.substring(guidelineIndex + "GUIDELINE:".length(), pseudoCodeIndex).trim();
        String pseudoCode = normalized.substring(pseudoCodeIndex + "PSEUDO_CODE:".length(), sideEffectsIndex).trim();
        List<String> sideEffects = parseSideEffects(normalized.substring(sideEffectsIndex + "SIDE_EFFECTS:".length()));
        if (guideline.isBlank() || pseudoCode.isBlank() || sideEffects.isEmpty()) {
            throw new IllegalStateException("AI issue guide response sections must not be blank.");
        }
        requireCodeLikePseudoCode(pseudoCode);

        IssueGuide guide = new IssueGuide(issue.id(), selectedFilePaths(codeContext), guideline, pseudoCode, sideEffects);
        requireGroundedInCodeContext(guide, codeContext);
        requireActionableGuideline(guide, issue, codeContext);
        return guide;
    }

    private static void requireGroundedInCodeContext(IssueGuide guide, RepositoryCodeContext codeContext) {
        List<String> anchors = IssueGuideCodeContextAnchors.groundingAnchors(codeContext);
        if (anchors.isEmpty()) {
            return;
        }

        String guideText = String.join("\n",
            guide.guideline(),
            guide.pseudoCode(),
            String.join("\n", guide.sideEffects())
        ).toLowerCase(Locale.ROOT);
        boolean grounded = anchors.stream()
            .map(anchor -> anchor.toLowerCase(Locale.ROOT))
            .anyMatch(guideText::contains);
        if (!grounded) {
            throw new IllegalStateException("AI issue guide response must reference repository code context.");
        }
    }

    private static void requireActionableGuideline(IssueGuide guide, RecommendedIssue issue, RepositoryCodeContext codeContext) {
        if (codeContext == null || !codeContext.hasFiles()) {
            return;
        }

        String guideline = guide.guideline().toLowerCase(Locale.ROOT);
        if (!containsAny(guideline, issueTerms(issue))) {
            throw new IllegalStateException("AI issue guide GUIDELINE must mention an issue-specific failure or requirement.");
        }
        if (!containsAny(guideline, editSignals())) {
            throw new IllegalStateException("AI issue guide GUIDELINE must include an edit direction.");
        }
        if (!containsAny(guideline, verificationSignals())) {
            throw new IllegalStateException("AI issue guide GUIDELINE must include first verification.");
        }
    }

    private static void requireCodeLikePseudoCode(String pseudoCode) {
        List<String> lines = Arrays.stream(pseudoCode.split("\\R"))
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .toList();
        String upperPseudoCode = pseudoCode.toUpperCase(Locale.ROOT);
        long numberedLines = lines.stream()
            .filter(line -> line.matches("^\\d+[.)]\\s+.*"))
            .count();
        if (!containsAny(upperPseudoCode, codeLikeSignals())) {
            throw new IllegalStateException("AI issue guide PSEUDO_CODE must contain code-like control or assertion lines.");
        }
        if (numberedLines >= Math.max(2, lines.size() / 2)) {
            throw new IllegalStateException("AI issue guide PSEUDO_CODE must not be a numbered checklist.");
        }
    }

    private static List<String> codeLikeSignals() {
        return List.of("GIVEN", "WHEN", "IF", "ELSE", "CALL", "RETURN", "ASSERT", "VERIFY", "THEN", "->", "=");
    }

    private static List<String> issueTerms(RecommendedIssue issue) {
        return Arrays.stream((issue.title() + " " + issue.summary()).split("[^\\p{L}\\p{N}_#()./-]+"))
            .map(term -> term.toLowerCase(Locale.ROOT).trim())
            .filter(term -> term.length() >= 4)
            .distinct()
            .toList();
    }

    private static List<String> editSignals() {
        return List.of("수정", "변경", "교체", "적용", "개선", "fix", "update", "change", "replace", "implement", "adjust");
    }

    private static List<String> verificationSignals() {
        return List.of("검증", "테스트", "재현", "확인", "test", "verify", "regression", "assert");
    }

    private static boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private static List<String> selectedFilePaths(RepositoryCodeContext codeContext) {
        if (codeContext == null || !codeContext.hasFiles()) {
            return List.of();
        }

        return codeContext.files().stream()
            .map(file -> file.path())
            .filter(path -> path != null && !path.isBlank())
            .distinct()
            .toList();
    }

    private static List<String> parseSideEffects(String sideEffectBlock) {
        return Arrays.stream(sideEffectBlock.split("\\R"))
            .map(String::trim)
            .map(line -> line.startsWith("-") ? line.substring(1).trim() : line)
            .filter(line -> !line.isBlank())
            .toList();
    }
}
