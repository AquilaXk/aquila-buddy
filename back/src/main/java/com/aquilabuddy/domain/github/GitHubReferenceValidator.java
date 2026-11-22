package com.aquilabuddy.domain.github;

/**
 * GitHub 저장소 슬러그, 브랜치 참조, 파일 경로, 트리 식별자의 경로 탈출 및 URI 메타문자를 검증하는 유틸리티입니다.
 */
public final class GitHubReferenceValidator {

    public static final int MAX_REPO_SLUG_LENGTH = 255;
    public static final int MAX_GIT_REF_LENGTH = 255;
    public static final int MAX_REPO_PATH_LENGTH = 512;
    public static final int MAX_TREE_ID_LENGTH = 64;
    private static final int MAX_FORK_OWNER_LENGTH = 100;

    private GitHubReferenceValidator() {
    }

    /**
     * GitHub 저장소 슬러그({@code owner/repo}) 형식을 검증합니다.
     */
    public static boolean isSafeRepoSlug(String repoSlug) {
        return splitSafeRepoSlug(repoSlug) != null;
    }

    /**
     * 검증된 {@code owner/repo} 슬러그를 {@code [owner, repo]} 배열로 분리하고, 부적합 시 {@code null}을 반환합니다.
     */
    public static String[] splitSafeRepoSlug(String repoSlug) {
        if (repoSlug == null || repoSlug.isBlank()) {
            return null;
        }
        String stripped = repoSlug.strip();
        if (stripped.length() > MAX_REPO_SLUG_LENGTH) {
            return null;
        }
        int slash = stripped.indexOf('/');
        if (slash <= 0 || slash >= stripped.length() - 1 || stripped.indexOf('/', slash + 1) >= 0) {
            return null;
        }
        if (!isSafeSlugSegment(stripped, 0, slash)
            || !isSafeSlugSegment(stripped, slash + 1, stripped.length())) {
            return null;
        }
        return new String[] { stripped.substring(0, slash), stripped.substring(slash + 1) };
    }

    /**
     * Git 브랜치 또는 포크 참조({@code owner:branch})의 안전성을 검증합니다.
     */
    public static boolean isSafeGitReference(String ref, boolean allowForkOwner) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        String stripped = ref.strip();
        if (stripped.length() > MAX_GIT_REF_LENGTH) {
            return false;
        }
        int colon = stripped.indexOf(':');
        if (colon >= 0) {
            if (!allowForkOwner
                || colon == 0
                || colon >= stripped.length() - 1
                || stripped.indexOf(':', colon + 1) >= 0) {
                return false;
            }
            if (colon > MAX_FORK_OWNER_LENGTH || !isSafeSlugSegment(stripped, 0, colon)) {
                return false;
            }
            return isSafeBranchName(stripped, colon + 1, stripped.length());
        }
        return isSafeBranchName(stripped, 0, stripped.length());
    }

    /**
     * 저장소 내 파일 상대 경로의 안전성을 검증합니다.
     */
    public static boolean isSafeRepositoryPath(String path) {
        if (path == null || path.isBlank() || path.length() > MAX_REPO_PATH_LENGTH) {
            return false;
        }
        String stripped = path.strip();
        if (stripped.isEmpty()
            || stripped.startsWith("/")
            || stripped.endsWith("/")
            || stripped.indexOf('\\') >= 0
            || stripped.contains("//")) {
            return false;
        }
        for (int i = 0, len = stripped.length(); i < len; i++) {
            char ch = stripped.charAt(i);
            if (ch < 0x20 || ch == 0x7F || ch == '?' || ch == '#' || ch == '{' || ch == '}' || ch == '%') {
                return false;
            }
        }
        int start = 0;
        while (start <= stripped.length()) {
            int slash = stripped.indexOf('/', start);
            int end = slash < 0 ? stripped.length() : slash;
            int segLen = end - start;
            if (segLen == 0
                || (segLen == 1 && stripped.charAt(start) == '.')
                || (segLen == 2 && stripped.charAt(start) == '.' && stripped.charAt(start + 1) == '.')) {
                return false;
            }
            if (slash < 0) {
                break;
            }
            start = slash + 1;
        }
        return true;
    }

    /**
     * Git 트리 식별자(SHA 등)의 안전성을 검증합니다.
     */
    public static boolean isSafeTreeIdentifier(String treeId) {
        if (treeId == null || treeId.isBlank()) {
            return false;
        }
        String stripped = treeId.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_TREE_ID_LENGTH) {
            return false;
        }
        for (int i = 0, len = stripped.length(); i < len; i++) {
            char ch = stripped.charAt(i);
            if (!((ch >= 'A' && ch <= 'Z')
                || (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9')
                || ch == '_'
                || ch == '-')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeSlugSegment(String text, int start, int end) {
        int len = end - start;
        if (len <= 0) {
            return false;
        }
        if (len == 1 && text.charAt(start) == '.') {
            return false;
        }
        if (len == 2 && text.charAt(start) == '.' && text.charAt(start + 1) == '.') {
            return false;
        }
        for (int i = start; i < end; i++) {
            char ch = text.charAt(i);
            if (!isSlugChar(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSlugChar(char ch) {
        return (ch >= 'A' && ch <= 'Z')
            || (ch >= 'a' && ch <= 'z')
            || (ch >= '0' && ch <= '9')
            || ch == '.'
            || ch == '_'
            || ch == '-';
    }

    private static boolean isSafeBranchName(String ref, int start, int end) {
        int len = end - start;
        if (len <= 0) {
            return false;
        }
        char first = ref.charAt(start);
        char last = ref.charAt(end - 1);
        if (first == '/' || first == '.' || last == '/' || last == '.') {
            return false;
        }
        if (endsWithLock(ref, start, end)) {
            return false;
        }
        char prev = 0;
        for (int i = start; i < end; i++) {
            char ch = ref.charAt(i);
            if (isForbiddenBranchChar(ch)) {
                return false;
            }
            if ((ch == '.' && prev == '.') || (ch == '/' && prev == '/') || (ch == '{' && prev == '@')) {
                return false;
            }
            prev = ch;
        }
        int segStart = start;
        while (segStart <= end) {
            int slash = ref.indexOf('/', segStart);
            int segEnd = (slash < 0 || slash >= end) ? end : slash;
            if (segEnd <= segStart) {
                return false;
            }
            if (ref.charAt(segStart) == '.' || endsWithLock(ref, segStart, segEnd)) {
                return false;
            }
            if (slash < 0 || slash >= end) {
                break;
            }
            segStart = slash + 1;
        }
        return true;
    }

    private static boolean isForbiddenBranchChar(char ch) {
        if (ch <= 0x20 || ch >= 0x7F) {
            return true;
        }
        return switch (ch) {
            case '\\', '?', '#', '{', '}', '%', '~', '^', ':', '*', '[' -> true;
            default -> false;
        };
    }

    private static boolean endsWithLock(String text, int start, int end) {
        return (end - start) >= 5 && text.regionMatches(end - 5, ".lock", 0, 5);
    }
}
