package com.aquilabuddy.domain.prdraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 대상 저장소가 기대하는 PR 본문 구조를 template 또는 기존 merged PR에서 도출한 값입니다.
 */
public record PullRequestDraftRepositoryFormat(
    Source source,
    List<String> requiredHeadings,
    List<String> requiredCheckboxes,
    String templateBody,
    String fallbackReason
) {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+\\S.*$");
    private static final Pattern CHECKBOX_PATTERN = Pattern.compile("^[-*]\\s+\\[[ xX]\\]\\s+.+$");

    public PullRequestDraftRepositoryFormat {
        source = source == null ? Source.GENERIC : source;
        requiredHeadings = requiredHeadings == null ? List.of() : List.copyOf(requiredHeadings);
        requiredCheckboxes = requiredCheckboxes == null ? List.of() : List.copyOf(requiredCheckboxes);
        templateBody = optionalText(templateBody);
        fallbackReason = optionalText(fallbackReason);
    }

    public static PullRequestDraftRepositoryFormat from(String pullRequestTemplate, List<String> mergedSamples) {
        if (hasText(pullRequestTemplate)) {
            return fromTemplate(pullRequestTemplate);
        }
        if (mergedSamples == null || mergedSamples.size() < 2) {
            return generic("not enough merged PR samples to infer common headings");
        }
        return fromMergedSamples(mergedSamples);
    }

    public static PullRequestDraftRepositoryFormat generic(String fallbackReason) {
        return new PullRequestDraftRepositoryFormat(Source.GENERIC, List.of(), List.of(), null, fallbackReason);
    }

    public boolean hasRepositoryFormat() {
        return source != Source.GENERIC;
    }

    public boolean isSatisfiedBy(String body) {
        if (!hasRepositoryFormat()) {
            return true;
        }
        List<String> normalizedLines = normalizedLines(body);
        return containsAll(normalizedLines, requiredHeadings) && containsAll(normalizedLines, requiredCheckboxes);
    }

    public List<String> promptLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Repository draft format source: " + source.promptLabel());
        if (source == Source.GENERIC) {
            lines.add("Repository draft format fallback reason: " + fallbackReason);
            return List.copyOf(lines);
        }

        if (!requiredHeadings.isEmpty()) {
            lines.add("Required PR headings:");
            requiredHeadings.forEach(heading -> lines.add("- " + heading));
        }
        if (!requiredCheckboxes.isEmpty()) {
            lines.add("Required PR checkboxes:");
            requiredCheckboxes.forEach(checkbox -> lines.add("- " + checkbox));
        }
        lines.add("- If the generated BODY misses required repository format lines, deterministic fallback is used.");
        return List.copyOf(lines);
    }

    private static PullRequestDraftRepositoryFormat fromTemplate(String pullRequestTemplate) {
        String normalizedTemplate = pullRequestTemplate.strip();
        return new PullRequestDraftRepositoryFormat(
            Source.TEMPLATE,
            extractHeadings(normalizedTemplate),
            extractCheckboxes(normalizedTemplate),
            normalizedTemplate,
            null
        );
    }

    private static PullRequestDraftRepositoryFormat fromMergedSamples(List<String> mergedSamples) {
        List<List<String>> headingGroups = mergedSamples.stream()
            .map(PullRequestDraftRepositoryFormat::extractHeadings)
            .filter(headings -> !headings.isEmpty())
            .toList();
        if (headingGroups.size() < 2) {
            return generic("merged PR samples did not contain enough headings");
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        headingGroups.forEach(headings ->
            headings.stream()
                .distinct()
                .forEach(heading -> counts.merge(heading, 1, Integer::sum))
        );

        List<String> commonHeadings = headingGroups.getFirst().stream()
            .filter(heading -> counts.getOrDefault(heading, 0) >= 2)
            .toList();
        if (commonHeadings.isEmpty()) {
            return generic("merged PR samples did not share common headings");
        }

        return new PullRequestDraftRepositoryFormat(
            Source.MERGED_PULL_REQUEST_SAMPLES,
            commonHeadings,
            List.of(),
            null,
            null
        );
    }

    private static List<String> extractHeadings(String body) {
        return normalizedLines(body).stream()
            .filter(line -> HEADING_PATTERN.matcher(line).matches())
            .distinct()
            .toList();
    }

    private static List<String> extractCheckboxes(String body) {
        return normalizedLines(body).stream()
            .filter(line -> CHECKBOX_PATTERN.matcher(line).matches())
            .distinct()
            .toList();
    }

    private static List<String> normalizedLines(String body) {
        if (!hasText(body)) {
            return List.of();
        }
        return body.lines()
            .map(String::strip)
            .filter(line -> !line.isBlank())
            .toList();
    }

    private static boolean containsAll(List<String> bodyLines, List<String> requiredLines) {
        return requiredLines.stream().allMatch(bodyLines::contains);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    public enum Source {
        TEMPLATE("PR template"),
        MERGED_PULL_REQUEST_SAMPLES("merged PR samples"),
        GENERIC("generic fallback");

        private final String promptLabel;

        Source(String promptLabel) {
            this.promptLabel = promptLabel;
        }

        private String promptLabel() {
            return promptLabel;
        }
    }
}
