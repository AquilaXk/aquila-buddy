package com.aquilabuddy.domain.prdraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PR 템플릿의 마크다운 섹션(Description, Test Plan, Checklist 등)을 구조적으로 분석한 도메인 모델입니다.
 */
public record PullRequestTemplateSection(
    String heading,
    String rawHeading,
    int level,
    String content,
    List<String> items,
    List<String> checkboxes,
    SectionType type
) {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern CHECKBOX_PATTERN = Pattern.compile("^[-*]\\s+\\[([ xX])\\]\\s*(.*)$");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[-*]\\s+(.*)$");

    public PullRequestTemplateSection {
        heading = requireText(heading, "heading");
        rawHeading = rawHeading == null ? "#".repeat(level > 0 ? level : 2) + " " + heading : rawHeading.trim();
        level = level < 1 ? 2 : level;
        content = content == null ? "" : content.strip();
        items = items == null ? List.of() : List.copyOf(items);
        checkboxes = checkboxes == null ? List.of() : List.copyOf(checkboxes);
        type = type == null ? SectionType.fromHeading(heading) : type;
    }

    public boolean hasCheckboxes() {
        return !checkboxes.isEmpty();
    }

    public boolean hasContent() {
        return !content.isBlank();
    }

    /**
     * 마크다운 템플릿 텍스트를 구조적으로 파싱하여 섹션 목록으로 변환합니다.
     */
    public static List<PullRequestTemplateSection> parseAll(String templateMarkdown) {
        if (templateMarkdown == null || templateMarkdown.isBlank()) {
            return List.of();
        }

        List<PullRequestTemplateSection> sections = new ArrayList<>();
        List<String> currentLines = new ArrayList<>();
        List<String> currentItems = new ArrayList<>();
        List<String> currentCheckboxes = new ArrayList<>();
        String currentHeading = null;
        String currentRawHeading = null;
        int currentLevel = 2;
        boolean inCodeBlock = false;
        boolean inHtmlComment = false;

        for (String rawLine : templateMarkdown.lines().toList()) {
            String trimmed = rawLine.trim();

            // 마크다운 코드 블록(``` 또는 ~~~) 진입/탈출 상태를 추적합니다.
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock;
            }

            // 멀티라인 HTML 주석(<!-- ... -->) 상태를 추적합니다.
            if (!inCodeBlock) {
                if (trimmed.startsWith("<!--") && !trimmed.contains("-->")) {
                    inHtmlComment = true;
                } else if (inHtmlComment && trimmed.contains("-->")) {
                    inHtmlComment = false;
                }
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(trimmed);

            // 코드 블록이나 멀티라인 주석 내부가 아닌 경우에만 새로운 마크다운 헤딩으로 인식합니다.
            if (!inCodeBlock && !inHtmlComment && headingMatcher.matches()) {
                if (currentHeading != null) {
                    sections.add(new PullRequestTemplateSection(
                        currentHeading,
                        currentRawHeading,
                        currentLevel,
                        String.join("\n", currentLines),
                        currentItems,
                        currentCheckboxes,
                        SectionType.fromHeading(currentHeading)
                    ));
                }
                currentHeading = headingMatcher.group(2).trim();
                currentRawHeading = trimmed;
                currentLevel = headingMatcher.group(1).length();
                currentLines = new ArrayList<>();
                currentItems = new ArrayList<>();
                currentCheckboxes = new ArrayList<>();
                continue;
            }

            // 헤딩 하위 본문에서 체크박스([- ] 또는 [- x]) 및 일반 불릿 항목을 분리 수집합니다.
            if (currentHeading != null) {
                currentLines.add(rawLine);
                if (!inCodeBlock && !inHtmlComment) {
                    Matcher checkboxMatcher = CHECKBOX_PATTERN.matcher(trimmed);
                    if (checkboxMatcher.matches()) {
                        currentCheckboxes.add(trimmed);
                    } else {
                        Matcher bulletMatcher = BULLET_PATTERN.matcher(trimmed);
                        if (bulletMatcher.matches()) {
                            currentItems.add(bulletMatcher.group(1).trim());
                        }
                    }
                }
            }
        }

        if (currentHeading != null) {
            sections.add(new PullRequestTemplateSection(
                currentHeading,
                currentRawHeading,
                currentLevel,
                String.join("\n", currentLines),
                currentItems,
                currentCheckboxes,
                SectionType.fromHeading(currentHeading)
            ));
        }

        return List.copyOf(sections);
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    /**
     * PR 템플릿 섹션의 의미적(시맨틱) 분류입니다.
     */
    public enum SectionType {
        DESCRIPTION,
        TEST_PLAN,
        CHECKLIST,
        BREAKING_CHANGES,
        ISSUE_LINK,
        OTHER;

        public static SectionType fromHeading(String heading) {
            if (heading == null || heading.isBlank()) {
                return OTHER;
            }
            String normalized = heading.toLowerCase(Locale.ROOT).trim();
            if (normalized.contains("breaking") || normalized.contains("migration") || normalized.contains("compatibility")
                || normalized.contains("브레이킹") || normalized.contains("호환성")) {
                return BREAKING_CHANGES;
            }
            if (normalized.contains("test") || normalized.contains("testing") || normalized.contains("verification")
                || normalized.contains("테스트") || normalized.contains("검증")) {
                return TEST_PLAN;
            }
            if (normalized.contains("checklist") || normalized.contains("check list") || normalized.contains("todo")
                || normalized.contains("체크리스트")) {
                return CHECKLIST;
            }
            if (normalized.contains("bugfix") || normalized.contains("hotfix")) {
                return DESCRIPTION;
            }
            if (normalized.contains("issue") || normalized.contains("close") || normalized.contains("resolve")
                || normalized.contains("이슈") || normalized.contains("연결") || normalized.matches(".*\\b(fix|fixes|close|closes|resolve|resolves)\\b.*")) {
                return ISSUE_LINK;
            }
            if (normalized.contains("desc") || normalized.contains("summary") || normalized.contains("overview")
                || normalized.contains("motivation") || normalized.contains("context") || normalized.contains("changes")
                || normalized.contains("what") || normalized.contains("변경") || normalized.contains("개요")
                || normalized.contains("설명")) {
                return DESCRIPTION;
            }
            return OTHER;
        }
    }
}
