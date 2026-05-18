package com.aquilabuddy.domain.prdraft;

import java.util.List;
import java.util.stream.Stream;

/**
 * PR 초안 작성에 참고할 저장소별 기여 가이드 context입니다.
 */
public record PullRequestDraftRepositoryGuideContext(
    String contributing,
    String pullRequestTemplate,
    List<String> mergedPullRequestSamples
) {

    private static final int MAX_MERGED_PULL_REQUEST_SAMPLES = 3;

    public PullRequestDraftRepositoryGuideContext {
        contributing = optionalText(contributing);
        pullRequestTemplate = optionalText(pullRequestTemplate);
        mergedPullRequestSamples = mergedPullRequestSamples == null
            ? List.of()
            : mergedPullRequestSamples.stream()
                .map(PullRequestDraftRepositoryGuideContext::optionalText)
                .filter(item -> item != null)
                .limit(MAX_MERGED_PULL_REQUEST_SAMPLES)
                .toList();
    }

    public static PullRequestDraftRepositoryGuideContext empty() {
        return new PullRequestDraftRepositoryGuideContext(null, null, List.of());
    }

    public boolean hasAnyGuide() {
        return contributing != null || pullRequestTemplate != null || !mergedPullRequestSamples.isEmpty();
    }

    public List<String> guideSummaryLines() {
        return Stream.of(
                contributing == null ? null : "CONTRIBUTING: " + firstLine(contributing),
                pullRequestTemplate == null ? null : "PR template: " + firstLine(pullRequestTemplate),
                mergedPullRequestSamples.isEmpty() ? null : "Merged PR samples: " + mergedPullRequestSamples.size()
            )
            .filter(item -> item != null)
            .toList();
    }

    public List<String> guidePromptLines() {
        List<String> lines = new java.util.ArrayList<>();
        if (contributing != null) {
            lines.add("CONTRIBUTING:");
            lines.add(contributing);
        }
        if (pullRequestTemplate != null) {
            lines.add("PR template:");
            lines.add(pullRequestTemplate);
        }
        for (int index = 0; index < mergedPullRequestSamples.size(); index++) {
            lines.add("Merged PR sample #" + (index + 1) + ":");
            lines.add(mergedPullRequestSamples.get(index));
        }
        return List.copyOf(lines);
    }

    private static String firstLine(String value) {
        return value.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .findFirst()
            .orElse(value);
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
