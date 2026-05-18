package com.aquilabuddy.adapter.out.ai.analysis;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI completion 결과를 issue guide 생성 outbound port로 연결합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai-compatible")
public class AiIssueGuideGenerationAdapter implements GenerateIssueGuidePort {

    private static final String SYSTEM_PROMPT = """
        You are an issue guide generator for an open-source contribution assistant.
        Use only the provided issue metadata and repository code context.
        Return exactly this marker format.
        GUIDELINE: <one focused implementation guideline>
        PSEUDO_CODE:
        <numbered implementation steps>
        SIDE_EFFECTS:
        - <side effect or verification concern>
        """;

    private final AiClient aiClient;

    public AiIssueGuideGenerationAdapter(AiClient aiClient) {
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null.");
    }

    @Override
    public IssueGuide generateIssueGuide(RecommendedIssue issue) {
        return generateIssueGuide(issue, RepositoryCodeContext.empty());
    }

    @Override
    public IssueGuide generateIssueGuide(RecommendedIssue issue, RepositoryCodeContext codeContext) {
        return parseGuide(issue.id(), aiClient.complete(SYSTEM_PROMPT, buildPrompt(issue, codeContext)));
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

    private static IssueGuide parseGuide(String issueId, String response) {
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

        return new IssueGuide(issueId, guideline, pseudoCode, sideEffects);
    }

    private static List<String> parseSideEffects(String sideEffectBlock) {
        return Arrays.stream(sideEffectBlock.split("\\R"))
            .map(String::trim)
            .map(line -> line.startsWith("-") ? line.substring(1).trim() : line)
            .filter(line -> !line.isBlank())
            .toList();
    }
}
