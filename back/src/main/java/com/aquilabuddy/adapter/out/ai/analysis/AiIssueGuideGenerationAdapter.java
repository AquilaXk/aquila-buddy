package com.aquilabuddy.adapter.out.ai.analysis;

import com.aquilabuddy.application.port.out.ai.AiClient;
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
        Return exactly this marker format.
        GUIDELINE: <2-4 concrete sentences with target file or symbol and first verification>
        PSEUDO_CODE:
        <4-6 numbered implementation steps grounded in the target file or symbol>
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
                issue.id(),
                safeContext,
                aiClient.complete(SYSTEM_PROMPT, buildPrompt(issue, safeContext))
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

    private static IssueGuide parseGuide(String issueId, RepositoryCodeContext codeContext, String response) {
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

        IssueGuide guide = new IssueGuide(issueId, selectedFilePaths(codeContext), guideline, pseudoCode, sideEffects);
        requireGroundedInCodeContext(guide, codeContext);
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
