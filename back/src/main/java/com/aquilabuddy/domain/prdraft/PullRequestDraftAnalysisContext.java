package com.aquilabuddy.domain.prdraft;

import java.util.ArrayList;
import java.util.List;

/**
 * PR 초안 작성에 참고할 저장된 분석 guide context입니다.
 */
public record PullRequestDraftAnalysisContext(
    String guideline,
    String pseudoCode,
    List<String> sideEffects
) {

    public PullRequestDraftAnalysisContext {
        guideline = optionalText(guideline);
        pseudoCode = optionalText(pseudoCode);
        sideEffects = normalizeSideEffects(sideEffects);
    }

    public static PullRequestDraftAnalysisContext empty() {
        return new PullRequestDraftAnalysisContext(null, null, List.of());
    }

    public boolean hasAnalysis() {
        return guideline != null || pseudoCode != null || !sideEffects.isEmpty();
    }

    public List<String> contextSummaryLines() {
        List<String> lines = new ArrayList<>();
        if (guideline != null) {
            lines.add("Guideline: " + guideline);
        }
        if (pseudoCode != null) {
            lines.add("Pseudo code: " + pseudoCode);
        }
        sideEffects.forEach(sideEffect -> lines.add("Side effect: " + sideEffect));
        return lines;
    }

    private static List<String> normalizeSideEffects(List<String> values) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
            .map(PullRequestDraftAnalysisContext::optionalText)
            .filter(value -> value != null)
            .toList();
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
