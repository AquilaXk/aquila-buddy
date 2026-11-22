package com.aquilabuddy.domain.analysis;

/**
 * 이슈 분석에 사용할 저장소 파일 경로와 제한된 본문입니다.
 */
public record RepositoryCodeContextFile(
    String path,
    String content,
    boolean truncated
) {

    public RepositoryCodeContextFile(String path, String content) {
        this(path, content, false);
    }

    public RepositoryCodeContextFile {
        path = normalizeRequired(path, "path");
        content = content == null ? "" : content.strip();
    }

    RepositoryCodeContextFile truncateTo(int maxContentChars) {
        if (maxContentChars <= 0 || content.length() <= maxContentChars) {
            return this;
        }
        return new RepositoryCodeContextFile(path, content.substring(0, maxContentChars) + "...", true);
    }

    private static String normalizeRequired(String value, String fieldName) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
