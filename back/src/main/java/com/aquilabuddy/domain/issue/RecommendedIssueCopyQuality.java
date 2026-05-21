package com.aquilabuddy.domain.issue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 추천 카드에 저장된 설명/추천 이유가 현재 화면 품질 기준을 만족하는지 판정합니다.
 */
public final class RecommendedIssueCopyQuality {

    private static final Pattern HANGUL = Pattern.compile("\\p{IsHangul}");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final int MAX_REASON_LENGTH = 240;

    private RecommendedIssueCopyQuality() {
    }

    public static boolean needsRefresh(RecommendedIssue issue) {
        return !refreshReasons(issue).isEmpty();
    }

    public static String refreshReason(RecommendedIssue issue) {
        return String.join(", ", refreshReasons(issue));
    }

    public static List<String> refreshReasons(RecommendedIssue issue) {
        if (issue == null) {
            return List.of("issue is null");
        }

        List<String> reasons = new ArrayList<>();
        if (!RecommendedIssue.CURRENT_COPY_OUTPUT_VERSION.equals(issue.copyOutputVersion())) {
            reasons.add("copy_output_version=" + issue.copyOutputVersion());
        }
        addTextQualityReasons(reasons, "summary", issue.summary());
        addTextQualityReasons(reasons, "reason", issue.reason());
        return List.copyOf(reasons);
    }

    public static boolean hasStaleText(RecommendedIssue issue) {
        if (issue == null) {
            return true;
        }
        List<String> reasons = new ArrayList<>();
        addTextQualityReasons(reasons, "summary", issue.summary());
        addTextQualityReasons(reasons, "reason", issue.reason());
        return !reasons.isEmpty();
    }

    public static String truncateReason(String value) {
        if (value == null || value.length() <= MAX_REASON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_REASON_LENGTH - 1) + "...";
    }

    private static void addTextQualityReasons(List<String> reasons, String fieldName, String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            reasons.add(fieldName + " is blank");
            return;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("github search") || normalized.contains("good first issue 조건으로 수집")) {
            reasons.add(fieldName + " has generic GitHub Search copy");
        }
        if (normalized.contains("수집된 후보")) {
            reasons.add(fieldName + " has generic candidate copy");
        }
        if (!containsKorean(text) && LATIN.matcher(text).find()) {
            reasons.add(fieldName + " is not Korean");
        }
    }

    private static boolean containsKorean(String value) {
        return HANGUL.matcher(value).find();
    }
}
