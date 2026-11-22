package com.aquilabuddy.domain.issue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 추천 카드에 저장된 설명/추천 이유가 현재 화면 품질 기준을 만족하는지 판정합니다.
 */
public final class RecommendedIssueCopyQuality {

    private static final int BIT_HANGUL = 1;
    private static final int BIT_LATIN = 2;
    private static final int BOTH_SCRIPTS = BIT_HANGUL | BIT_LATIN;
    private static final Pattern SUMMARY_DISPLAY_LABEL = Pattern.compile(
        "^(?:한국어\\s*요약|요약|summary)\\s*[:：]",
        Pattern.CASE_INSENSITIVE
    );
    private static final int MAX_REASON_LENGTH = 240;

    private RecommendedIssueCopyQuality() {
    }

    public static boolean needsRefresh(RecommendedIssue issue) {
        if (issue == null) {
            return true;
        }
        return !RecommendedIssue.CURRENT_COPY_OUTPUT_VERSION.equals(issue.copyOutputVersion())
            || hasStaleText(issue);
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
        return hasTextQualityIssue(issue.summary(), true)
            || hasTextQualityIssue(issue.reason(), false);
    }

    public static String truncateReason(String value) {
        if (value == null || value.length() <= MAX_REASON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_REASON_LENGTH - 1) + "...";
    }

    static int scanScriptMask(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int mask = 0;
        for (int index = 0, length = value.length(); index < length; index++) {
            char ch = value.charAt(index);
            if ((ch >= '\uAC00' && ch <= '\uD7A3') || (ch >= '\u3131' && ch <= '\u318E')) {
                mask |= BIT_HANGUL;
            } else if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                mask |= BIT_LATIN;
            }
            if ((mask & BOTH_SCRIPTS) == BOTH_SCRIPTS) {
                return mask;
            }
        }
        return mask;
    }

    static boolean hasMixedKoreanAndEnglish(String value) {
        return (scanScriptMask(value) & BOTH_SCRIPTS) == BOTH_SCRIPTS;
    }

    private static boolean hasTextQualityIssue(String value, boolean isSummary) {
        if (value == null) {
            return true;
        }
        String text = value.trim();
        if (text.isBlank()) {
            return true;
        }
        if (isSummary && SUMMARY_DISPLAY_LABEL.matcher(text).find()) {
            return true;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("github search")
            || normalized.contains("good first issue 조건으로 수집")
            || normalized.contains("수집된 후보")) {
            return true;
        }
        return scanScriptMask(text) == BIT_LATIN;
    }

    private static void addTextQualityReasons(List<String> reasons, String fieldName, String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            reasons.add(fieldName + " is blank");
            return;
        }
        if ("summary".equals(fieldName) && SUMMARY_DISPLAY_LABEL.matcher(text).find()) {
            reasons.add(fieldName + " has display label");
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("github search") || normalized.contains("good first issue 조건으로 수집")) {
            reasons.add(fieldName + " has generic GitHub Search copy");
        }
        if (normalized.contains("수집된 후보")) {
            reasons.add(fieldName + " has generic candidate copy");
        }
        if (scanScriptMask(text) == BIT_LATIN) {
            reasons.add(fieldName + " is not Korean");
        }
    }
}
