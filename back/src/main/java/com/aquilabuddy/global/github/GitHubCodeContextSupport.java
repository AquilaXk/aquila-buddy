package com.aquilabuddy.global.github;

import com.aquilabuddy.domain.github.GitHubReferenceValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GitHub 저장소 트리 후보 필터링·점수화 및 diff/코드 컨텍스트 정제를 수행하는 유틸리티입니다.
 */
public final class GitHubCodeContextSupport {

    static final int MAX_COMPARE_DIFF_CHARS = 11_500;
    private static final int MAX_SINGLE_FILE_CONTENT_CHARS = 16_384;
    private static final int MAX_QUERY_SCAN_CHARS = 4_096;
    private static final int MAX_QUERY_TOKENS = 32;

    private static final List<String> TREE_EXCLUDED_PATH_SEGMENTS = List.of(
        "/.git/",
        "/.gradle/",
        "/.idea/",
        "/build/",
        "/dist/",
        "/node_modules/",
        "/target/",
        "/vendor/"
    );
    private static final List<String> TREE_ASSET_EXTENSIONS = List.of(
        ".avif",
        ".gif",
        ".ico",
        ".jpeg",
        ".jpg",
        ".pdf",
        ".png",
        ".svg",
        ".webp",
        ".zip",
        ".lock",
        ".min.js",
        ".min.css",
        ".map"
    );
    private static final Set<String> LOCKFILE_BASENAMES = Set.of(
        "package-lock.json",
        "yarn.lock",
        "pnpm-lock.yaml",
        "cargo.lock",
        "poetry.lock",
        "composer.lock",
        "gemfile.lock",
        "go.sum"
    );
    private static final List<String> IMPLEMENTATION_EXTENSIONS = List.of(
        ".java", ".kt", ".ts", ".tsx", ".js", ".jsx", ".py", ".go", ".rs",
        ".c", ".cc", ".cpp", ".h", ".cs", ".rb", ".php", ".scala", ".swift"
    );
    private static final List<String> DOCUMENTATION_EXTENSIONS = List.of(
        ".md", ".mdx", ".txt", ".rst", ".adoc"
    );
    private static final Set<String> TREE_QUERY_STOP_WORDS = Set.of(
        "and",
        "first",
        "good",
        "issue",
        "the",
        "for",
        "with",
        "from",
        "that",
        "this"
    );
    private static final List<String> CANDIDATE_DIR_PREFIXES = List.of(
        "src",
        "lib",
        "app",
        "packages",
        "back",
        "front",
        "backend",
        "frontend",
        "client",
        "server",
        "core"
    );

    private GitHubCodeContextSupport() {
    }

    static boolean isSafeRepositoryPath(String path) {
        return GitHubReferenceValidator.isSafeRepositoryPath(path);
    }

    /**
     * 이슈 제목/본문에서 camelCase, snake_case, 파일 경로 앵커 심볼을 단일 패스로 추출합니다.
     */
    public static List<String> codeContextQueryTokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        int length = Math.min(query.length(), MAX_QUERY_SCAN_CHARS);
        StringBuilder compound = new StringBuilder(48);
        StringBuilder subToken = new StringBuilder(24);
        boolean previousWasLowerOrDigit = false;

        for (int index = 0; index < length && tokens.size() < MAX_QUERY_TOKENS; index++) {
            char ch = query.charAt(index);
            if (isAsciiAlphaNum(ch)) {
                char lower = foldLower(ch);
                boolean isUpper = ch >= 'A' && ch <= 'Z';
                if (isUpper && previousWasLowerOrDigit && !subToken.isEmpty()) {
                    addTokenIfEligible(subToken.toString(), tokens);
                    subToken.setLength(0);
                }
                compound.append(lower);
                subToken.append(lower);
                previousWasLowerOrDigit = !isUpper;
            } else if (ch == '_' || ch == '-' || ch == '/' || ch == '.') {
                if (!subToken.isEmpty()) {
                    addTokenIfEligible(subToken.toString(), tokens);
                    subToken.setLength(0);
                }
                if (ch == '_' || ch == '-' || ch == '/') {
                    compound.append(ch);
                } else {
                    flushCompound(compound, tokens);
                }
                previousWasLowerOrDigit = false;
            } else {
                if (!subToken.isEmpty()) {
                    addTokenIfEligible(subToken.toString(), tokens);
                    subToken.setLength(0);
                }
                flushCompound(compound, tokens);
                previousWasLowerOrDigit = false;
            }
        }
        if (!subToken.isEmpty()) {
            addTokenIfEligible(subToken.toString(), tokens);
        }
        flushCompound(compound, tokens);
        return List.copyOf(tokens);
    }

    private static void flushCompound(StringBuilder compound, Set<String> tokens) {
        if (!compound.isEmpty()) {
            String value = stripEdgeSeparators(compound.toString());
            addTokenIfEligible(value, tokens);
            compound.setLength(0);
        }
    }

    private static String stripEdgeSeparators(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSeparator(value.charAt(start))) {
            start++;
        }
        while (end > start && isSeparator(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isSeparator(char ch) {
        return ch == '_' || ch == '-' || ch == '/';
    }

    private static boolean isAsciiAlphaNum(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9');
    }

    private static char foldLower(char ch) {
        return (ch >= 'A' && ch <= 'Z') ? (char) (ch + ('a' - 'A')) : ch;
    }

    private static void addTokenIfEligible(String token, Set<String> tokens) {
        if (token.length() >= 3
            && tokens.size() < MAX_QUERY_TOKENS
            && !TREE_QUERY_STOP_WORDS.contains(token)) {
            tokens.add(token);
        }
    }

    static int treeCandidateScore(String path, List<String> queryTokens) {
        if (path == null || path.isBlank()) {
            return 0;
        }

        String normalized = path.toLowerCase(Locale.ROOT);
        int matchedTokens = 0;
        for (String token : queryTokens) {
            if (normalized.contains(token)) {
                matchedTokens++;
            }
        }
        if (!queryTokens.isEmpty() && matchedTokens == 0) {
            return 0;
        }

        int score = matchedTokens * 10;
        String fileName = baseName(normalized);
        String stem = fileStem(fileName);
        for (String token : queryTokens) {
            if (stem.equals(token)) {
                score += 15;
                break;
            }
        }

        if (hasAnyExtension(normalized, IMPLEMENTATION_EXTENSIONS)) {
            score += 6;
        } else if (hasAnyExtension(normalized, DOCUMENTATION_EXTENSIONS)) {
            score -= 5;
        }

        String slashPrefixed = "/" + normalized;
        if (slashPrefixed.contains("/src/main/") || slashPrefixed.contains("/src/")) {
            score += 3;
        }
        if (slashPrefixed.contains("/test/")
            || slashPrefixed.contains("/tests/")
            || slashPrefixed.contains("/__tests__/")
            || fileName.endsWith("test.java")
            || fileName.contains(".spec.")
            || fileName.contains(".test.")) {
            score -= 2;
        }
        if (slashPrefixed.contains("/docs/")
            || slashPrefixed.contains("/doc/")
            || fileName.startsWith("readme")
            || fileName.startsWith("changelog")) {
            score -= 4;
        }
        return queryTokens.isEmpty() ? Math.max(0, score) : Math.max(1, score);
    }

    static boolean isRepositoryTreeCodeCandidate(String path) {
        if (!isSafeRepositoryPath(path)) {
            return false;
        }

        String normalized = "/" + path.strip().toLowerCase(Locale.ROOT);
        for (String excluded : TREE_EXCLUDED_PATH_SEGMENTS) {
            if (normalized.contains(excluded)) {
                return false;
            }
        }
        for (String assetExt : TREE_ASSET_EXTENSIONS) {
            if (normalized.endsWith(assetExt)) {
                return false;
            }
        }
        return !LOCKFILE_BASENAMES.contains(baseName(normalized));
    }

    private static String baseName(String normalizedPath) {
        int slash = normalizedPath.lastIndexOf('/');
        return slash < 0 ? normalizedPath : normalizedPath.substring(slash + 1);
    }

    private static String fileStem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static boolean hasAnyExtension(String path, List<String> extensions) {
        for (String ext : extensions) {
            if (path.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    static boolean isCandidateDirectory(String dirPath, List<String> queryTokens) {
        String lower = dirPath.toLowerCase(Locale.ROOT);
        for (String excluded : TREE_EXCLUDED_PATH_SEGMENTS) {
            if (lower.contains(excluded)) {
                return false;
            }
        }
        for (String token : queryTokens) {
            if (lower.contains(token)) {
                return true;
            }
        }
        for (String prefix : CANDIDATE_DIR_PREFIXES) {
            if (lower.equals(prefix) || lower.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    static String sanitizeCodeContextContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        int scanLimit = Math.min(content.length(), MAX_SINGLE_FILE_CONTENT_CHARS);
        StringBuilder builder = new StringBuilder(Math.min(scanLimit, 4096));
        int lineStart = 0;
        while (lineStart < scanLimit) {
            int lineEnd = lineStart;
            while (lineEnd < scanLimit) {
                char c = content.charAt(lineEnd);
                if (c == '\n' || c == '\r') {
                    break;
                }
                lineEnd++;
            }

            int trimmedStart = lineStart;
            while (trimmedStart < lineEnd && Character.isWhitespace(content.charAt(trimmedStart))) {
                trimmedStart++;
            }
            int trimmedEnd = lineEnd;
            while (trimmedEnd > lineStart && Character.isWhitespace(content.charAt(trimmedEnd - 1))) {
                trimmedEnd--;
            }

            if (trimmedStart < trimmedEnd && !isCommentStart(content, trimmedStart, trimmedEnd)) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(content, lineStart, trimmedEnd);
            }

            lineStart = lineEnd;
            if (lineStart < scanLimit && content.charAt(lineStart) == '\r') {
                lineStart++;
            }
            if (lineStart < scanLimit && content.charAt(lineStart) == '\n') {
                lineStart++;
            }
        }
        return builder.toString();
    }

    private static boolean isCommentStart(String content, int start, int end) {
        char first = content.charAt(start);
        if (first == '#' || first == '*') {
            return true;
        }
        if (start + 1 < end) {
            char second = content.charAt(start + 1);
            return (first == '/' && (second == '/' || second == '*'))
                || (first == '*' && second == '/');
        }
        return false;
    }

    static String formatCompareDiff(List<ComparePatch> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (ComparePatch file : files) {
            if (file == null || file.patch() == null || file.patch().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("--- ").append(file.filename()).append('\n').append(file.patch());
            if (builder.length() >= MAX_COMPARE_DIFF_CHARS) {
                return builder.substring(0, MAX_COMPARE_DIFF_CHARS - 3) + "...";
            }
        }
        return builder.toString();
    }

    static List<TreeCandidate> extractTreeCandidates(List<TreeItem> tree, List<String> queryTokens) {
        if (tree == null || tree.isEmpty()) {
            return List.of();
        }
        List<TreeCandidate> candidates = new ArrayList<>();
        for (TreeItem item : tree) {
            if (item == null || !"blob".equals(item.type()) || !isRepositoryTreeCodeCandidate(item.path())) {
                continue;
            }
            int score = treeCandidateScore(item.path(), queryTokens);
            if (queryTokens.isEmpty() || score > 0) {
                candidates.add(new TreeCandidate(item.path(), score));
            }
        }
        candidates.sort(Comparator.comparingInt(TreeCandidate::score).reversed().thenComparing(TreeCandidate::path));
        return List.copyOf(candidates);
    }

    record TreeItem(String path, String type, String sha) {
    }

    record TreeCandidate(String path, int score) {
    }

    record ComparePatch(String filename, String patch) {
    }
}
