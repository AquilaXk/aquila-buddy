package com.aquilabuddy.domain.issue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 추천 이슈의 라벨·저장소·제목·요약 텍스트와 사용자 주 사용 언어의 단어 경계 일치를 판정합니다.
 */
public final class RecommendedIssueLanguageMatcher {

    private static final Map<String, Pattern> LANGUAGE_PATTERNS = Map.ofEntries(
        Map.entry("c++", Pattern.compile("(?i)(?:^|[^a-zA-Z0-9])(?:c\\+\\+|cpp)(?:$|[^a-zA-Z0-9])")),
        Map.entry("cpp", Pattern.compile("(?i)(?:^|[^a-zA-Z0-9])(?:c\\+\\+|cpp)(?:$|[^a-zA-Z0-9])")),
        Map.entry("c#", Pattern.compile("(?i)(?:^|[^a-zA-Z0-9])(?:c#|csharp)(?:$|[^a-zA-Z0-9])")),
        Map.entry("csharp", Pattern.compile("(?i)(?:^|[^a-zA-Z0-9])(?:c#|csharp)(?:$|[^a-zA-Z0-9])")),
        Map.entry("go", Pattern.compile("(?i)\\b(?:go|golang)\\b")),
        Map.entry("golang", Pattern.compile("(?i)\\b(?:go|golang)\\b")),
        Map.entry("java", Pattern.compile("(?i)\\bjava\\b")),
        Map.entry("javascript", Pattern.compile("(?i)\\b(?:javascript|js)\\b")),
        Map.entry("js", Pattern.compile("(?i)\\b(?:javascript|js)\\b")),
        Map.entry("typescript", Pattern.compile("(?i)\\b(?:typescript|ts)\\b")),
        Map.entry("ts", Pattern.compile("(?i)\\b(?:typescript|ts)\\b")),
        Map.entry("python", Pattern.compile("(?i)\\b(?:python|py)\\b")),
        Map.entry("py", Pattern.compile("(?i)\\b(?:python|py)\\b")),
        Map.entry("rust", Pattern.compile("(?i)\\brust\\b")),
        Map.entry("kotlin", Pattern.compile("(?i)\\bkotlin\\b"))
    );

    private RecommendedIssueLanguageMatcher() {
    }

    /**
     * 짧은 언어명(Go, C 등)이 일반 단어(good first issue, ongoing 등)에 오매칭되지 않도록 단어 경계 정규식을 반환합니다.
     */
    public static Pattern compileLanguageBoundaryPattern(String lang) {
        String normalized = lang.trim().toLowerCase(Locale.ROOT);
        Pattern pattern = LANGUAGE_PATTERNS.get(normalized);
        if (pattern != null) {
            return pattern;
        }
        return Pattern.compile("(?i)\\b" + Pattern.quote(normalized) + "\\b");
    }

    public static boolean matchesLanguage(String text, String lang) {
        if (text == null || text.isBlank() || lang == null || lang.isBlank()) {
            return false;
        }
        return compileLanguageBoundaryPattern(lang).matcher(text).find();
    }

    public static boolean matchesAnyLanguage(RecommendedIssue issue, List<String> languages) {
        return findMatchingLanguage(issue, languages) != null;
    }

    public static String findMatchingLanguage(RecommendedIssue issue, List<String> languages) {
        if (issue == null || languages == null || languages.isEmpty()) {
            return null;
        }

        List<String> labels = issue.labels() == null ? List.of() : issue.labels();
        for (String lang : languages) {
            if (lang == null || lang.isBlank()) {
                continue;
            }
            for (String label : labels) {
                if (matchesLanguage(label, lang)) {
                    return canonicalLanguageName(lang);
                }
            }
            if (matchesLanguage(issue.repoFullName(), lang)
                || matchesLanguage(issue.title(), lang)
                || matchesLanguage(issue.summary(), lang)) {
                return canonicalLanguageName(lang);
            }
        }
        return null;
    }

    public static String canonicalLanguageName(String lang) {
        if (lang == null || lang.isBlank()) {
            return lang;
        }
        String trimmed = lang.trim();
        return switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "c++", "cpp" -> "C++";
            case "c#", "csharp" -> "C#";
            case "go", "golang" -> "Go";
            case "java" -> "Java";
            case "javascript", "js" -> "JavaScript";
            case "typescript", "ts" -> "TypeScript";
            case "python", "py" -> "Python";
            case "rust" -> "Rust";
            case "kotlin" -> "Kotlin";
            default -> Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
        };
    }
}
