package com.aquilabuddy.adapter.out.ai.analysis;

import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.analysis.RepositoryCodeContextFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI guide가 실제 코드 맥락에 근거했는지 판단할 파일/심볼 anchor를 추출합니다.
 */
final class IssueGuideCodeContextAnchors {

    private static final Pattern TYPE_PATTERN = Pattern.compile("\\b(?:class|interface|record|enum)\\s+([A-Z][A-Za-z0-9_]*)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\b([a-z][A-Za-z0-9_]*)\\s*\\(");
    private static final Set<String> CONTROL_KEYWORDS = Set.of(
        "if",
        "for",
        "while",
        "switch",
        "catch",
        "return",
        "throw",
        "new"
    );

    private IssueGuideCodeContextAnchors() {
    }

    static List<String> groundingAnchors(RepositoryCodeContext codeContext) {
        if (codeContext == null || !codeContext.hasFiles()) {
            return List.of();
        }

        Set<String> anchors = new LinkedHashSet<>();
        for (RepositoryCodeContextFile file : codeContext.files()) {
            anchors.add(file.path());
            anchors.add(fileName(file.path()));
            anchors.add(fileStem(file.path()));
            anchors.addAll(extractTypeNames(file.content()));
            anchors.addAll(extractMethodNames(file.content()));
        }
        return anchors.stream()
            .filter(IssueGuideCodeContextAnchors::isStrongAnchor)
            .toList();
    }

    static CodeFocus primaryFocus(RepositoryCodeContext codeContext) {
        if (codeContext == null || !codeContext.hasFiles()) {
            return CodeFocus.empty();
        }

        RepositoryCodeContextFile file = codeContext.files().get(0);
        List<String> typeNames = extractTypeNames(file.content());
        List<String> methodNames = extractMethodNames(file.content());
        String primarySymbol = typeNames.isEmpty() ? fileStem(file.path()) : typeNames.get(0);
        return new CodeFocus(file.path(), fileName(file.path()), primarySymbol, methodNames);
    }

    private static List<String> extractTypeNames(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        Matcher matcher = TYPE_PATTERN.matcher(content);
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return List.copyOf(names);
    }

    private static List<String> extractMethodNames(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        Set<String> names = new LinkedHashSet<>();
        Arrays.stream(content.split("\\R"))
            .map(String::strip)
            .filter(line -> !line.isBlank())
            .filter(IssueGuideCodeContextAnchors::looksLikeMethodDeclaration)
            .forEach(line -> collectMethodName(line, names));
        return names.stream()
            .limit(5)
            .toList();
    }

    private static boolean looksLikeMethodDeclaration(String line) {
        if (!line.contains("(") || line.startsWith("@")) {
            return false;
        }
        String firstToken = line.split("\\s+", 2)[0];
        return !CONTROL_KEYWORDS.contains(firstToken);
    }

    private static void collectMethodName(String line, Set<String> names) {
        Matcher matcher = METHOD_PATTERN.matcher(line);
        List<String> candidates = new ArrayList<>();
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!CONTROL_KEYWORDS.contains(candidate)) {
                candidates.add(candidate);
            }
        }
        if (!candidates.isEmpty()) {
            names.add(candidates.get(candidates.size() - 1));
        }
    }

    private static boolean isStrongAnchor(String anchor) {
        if (anchor == null || anchor.isBlank()) {
            return false;
        }
        String normalized = anchor.strip();
        return normalized.length() >= 8 || normalized.contains("/") || normalized.contains(".");
    }

    private static String fileName(String path) {
        String normalized = path == null ? "" : path.strip();
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex < 0 ? normalized : normalized.substring(slashIndex + 1);
    }

    private static String fileStem(String path) {
        String fileName = fileName(path);
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex < 0 ? fileName : fileName.substring(0, extensionIndex);
    }

    record CodeFocus(String path, String fileName, String primarySymbol, List<String> methodNames) {

        private static CodeFocus empty() {
            return new CodeFocus("", "", "", List.of());
        }

        CodeFocus {
            path = path == null ? "" : path.strip();
            fileName = fileName == null ? "" : fileName.strip();
            primarySymbol = primarySymbol == null ? "" : primarySymbol.strip();
            methodNames = methodNames == null ? List.of() : List.copyOf(methodNames);
        }

        boolean hasFile() {
            return !path.isBlank();
        }

        String methodSummary() {
            if (methodNames.isEmpty()) {
                return "대표 메서드";
            }
            return String.join("(), ", methodNames) + "()";
        }
    }
}
