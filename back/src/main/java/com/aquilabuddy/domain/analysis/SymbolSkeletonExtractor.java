package com.aquilabuddy.domain.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 코드 파일에서 클래스, 함수, 메서드 시그니처만 추출하여 프롬프트 토큰을 절감하는 추출기입니다.
 *
 * <p>1-pass 라인 스캔으로 중간 배열 할당 없이 API 계약과 심볼 선언부만 유지하며,
 * 대용량 입력은 상한({@value #MAX_CONTENT_CHARS}자)까지만 안전하게 처리합니다.
 */
public final class SymbolSkeletonExtractor {

    static final int MAX_CONTENT_CHARS = 262_144;

    private static final Set<String> CONTROL_KEYWORDS = Set.of(
        "if", "for", "while", "switch", "catch", "synchronized", "finally", "return", "throw"
    );

    private static final Pattern TYPE_DECLARATION_PATTERN = Pattern.compile(
        "^\\s*(?:(?:export|public|protected|private|static|final|abstract|sealed|default)\\s+)*"
            + "(?:class|interface|record|enum|struct|trait|type)\\b"
    );

    private static final Pattern METHOD_START_PATTERN = Pattern.compile(
        "^\\s*(?:(?:export|public|protected|private|static|final|abstract|async|override|fun|def|func|fn)\\s+)*"
            + "(?:\\([a-zA-Z0-9_*&,\\s]+\\)\\s+)?"
            + "[a-zA-Z0-9_<>\\[\\],\\s?*&]+\\s+([a-zA-Z0-9_]+)\\s*\\("
    );

    private static final Pattern PYTHON_DEF_PATTERN = Pattern.compile(
        "^(\\s*)(?:async\\s+)?def\\s+([a-zA-Z0-9_]+)\\s*\\((.*?)\\)\\s*(?:->\\s*[^:]+)?:"
    );
    private static final Pattern PYTHON_CLASS_PATTERN = Pattern.compile(
        "^(\\s*)class\\s+([a-zA-Z0-9_]+)(?:\\(.*?\\))?:"
    );

    private SymbolSkeletonExtractor() {
    }

    /**
     * 파일 확장자와 내용을 기반으로 심볼 스켈레톤을 추출합니다.
     */
    public static String extractSkeleton(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String bounded = content.length() > MAX_CONTENT_CHARS
            ? content.substring(0, MAX_CONTENT_CHARS)
            : content;
        String trimmed = bounded.trim();
        if (trimmed.length() <= 120 || trimmed.indexOf('\n') < 0) {
            return trimmed;
        }

        String lowerPath = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".py")) {
            return extractPythonSkeleton(bounded);
        }
        if (isBraceLanguage(lowerPath)) {
            return extractBraceSkeleton(bounded);
        }
        return fallbackSkeleton(bounded);
    }

    private static boolean isBraceLanguage(String path) {
        return path.endsWith(".java")
            || path.endsWith(".kt")
            || path.endsWith(".ts")
            || path.endsWith(".tsx")
            || path.endsWith(".js")
            || path.endsWith(".jsx")
            || path.endsWith(".go")
            || path.endsWith(".rs")
            || path.endsWith(".cs")
            || path.endsWith(".c")
            || path.endsWith(".cpp");
    }

    /**
     * 중괄호 블록 기반 언어(Java, TS, Go 등)에서 1-pass 스캔으로 메서드 본문을 제거하고 시그니처만 유지합니다.
     */
    private static String extractBraceSkeleton(String content) {
        List<String> outputLines = new ArrayList<>();
        List<String> annotationBuffer = new ArrayList<>();
        StringBuilder signatureBuffer = new StringBuilder();
        StringBuilder typeBuffer = new StringBuilder();

        int braceDepth = 0;
        int methodBodyDepth = -1;
        boolean accumulatingSignature = false;
        boolean accumulatingType = false;

        int length = content.length();
        int lineStart = 0;
        while (lineStart < length) {
            int lineEnd = findLineEnd(content, lineStart, length);
            String rawLine = content.substring(lineStart, lineEnd);
            lineStart = advancePastLineBreak(content, lineEnd, length);

            String stripped = rawLine.strip();
            if (stripped.isBlank() || stripped.startsWith("//") || stripped.startsWith("/*") || stripped.startsWith("*")) {
                continue;
            }
            if (stripped.startsWith("import ") || stripped.startsWith("package ")) {
                continue;
            }

            int packedBraces = packBraceCounts(stripped);
            int braceDelta = (packedBraces >> 16) - (packedBraces & 0xFFFF);

            if (methodBodyDepth >= 0) {
                braceDepth += braceDelta;
                if (braceDepth <= methodBodyDepth) {
                    methodBodyDepth = -1;
                }
                continue;
            }

            if (stripped.startsWith("@")) {
                annotationBuffer.add(rawLine);
                continue;
            }

            if (accumulatingType) {
                typeBuffer.append(' ').append(stripped);
                if (stripped.indexOf('{') >= 0) {
                    flushAnnotations(annotationBuffer, outputLines);
                    outputLines.add(typeBuffer.toString().strip());
                    accumulatingType = false;
                    typeBuffer.setLength(0);
                    braceDepth += braceDelta;
                }
                continue;
            }

            if (looksLikeTypeDeclaration(stripped)) {
                if (stripped.indexOf('{') >= 0) {
                    flushAnnotations(annotationBuffer, outputLines);
                    outputLines.add(rawLine);
                    braceDepth += braceDelta;
                } else {
                    accumulatingType = true;
                    typeBuffer.append(stripped);
                }
                continue;
            }

            if (accumulatingSignature) {
                signatureBuffer.append(' ').append(stripped);
                if (stripped.indexOf('{') >= 0 || stripped.endsWith(";")) {
                    flushAnnotations(annotationBuffer, outputLines);
                    String sig = signatureBuffer.toString().strip();
                    if (sig.endsWith("{")) {
                        sig = sig.substring(0, sig.length() - 1).strip();
                    }
                    outputLines.add(formatSignature(sig));
                    accumulatingSignature = false;
                    signatureBuffer.setLength(0);

                    if (stripped.indexOf('{') >= 0) {
                        if (braceDelta > 0) {
                            methodBodyDepth = braceDepth;
                        }
                        braceDepth += braceDelta;
                    }
                }
                continue;
            }

            if (looksLikeMethodStart(stripped)) {
                int openBracePos = stripped.indexOf('{');
                if (openBracePos >= 0) {
                    flushAnnotations(annotationBuffer, outputLines);
                    String sig = stripped.substring(0, openBracePos).strip();
                    outputLines.add(formatSignature(sig));
                    if (braceDelta > 0) {
                        methodBodyDepth = braceDepth;
                    }
                    braceDepth += braceDelta;
                } else if (stripped.endsWith(";")) {
                    flushAnnotations(annotationBuffer, outputLines);
                    outputLines.add(rawLine);
                    braceDepth += braceDelta;
                } else {
                    accumulatingSignature = true;
                    signatureBuffer.append(stripped);
                }
                continue;
            }

            if (stripped.startsWith("}")) {
                annotationBuffer.clear();
                braceDepth += braceDelta;
                outputLines.add(rawLine);
                continue;
            }

            braceDepth += braceDelta;
        }

        String result = String.join("\n", outputLines).trim();
        if (result.length() < 30) {
            return fallbackSkeleton(content);
        }
        return result;
    }

    private static String extractPythonSkeleton(String content) {
        List<String> outputLines = new ArrayList<>();
        List<String> decoratorBuffer = new ArrayList<>();
        StringBuilder defBuffer = new StringBuilder();
        boolean accumulatingDef = false;

        int length = content.length();
        int lineStart = 0;
        while (lineStart < length) {
            int lineEnd = findLineEnd(content, lineStart, length);
            String rawLine = content.substring(lineStart, lineEnd);
            lineStart = advancePastLineBreak(content, lineEnd, length);

            String stripped = rawLine.strip();
            if (stripped.isBlank() || stripped.startsWith("#")) {
                continue;
            }

            if (stripped.startsWith("@")) {
                decoratorBuffer.add(rawLine);
                continue;
            }

            Matcher classMatcher = PYTHON_CLASS_PATTERN.matcher(rawLine);
            if (classMatcher.find()) {
                outputLines.addAll(decoratorBuffer);
                decoratorBuffer.clear();
                outputLines.add(rawLine);
                continue;
            }

            if (accumulatingDef) {
                defBuffer.append(' ').append(stripped);
                if (stripped.endsWith(":")) {
                    outputLines.addAll(decoratorBuffer);
                    decoratorBuffer.clear();
                    String fullDef = defBuffer.toString().strip();
                    if (fullDef.endsWith(":")) {
                        fullDef = fullDef.substring(0, fullDef.length() - 1).strip();
                    }
                    outputLines.add(fullDef + ": ...");
                    accumulatingDef = false;
                    defBuffer.setLength(0);
                }
                continue;
            }

            Matcher defMatcher = PYTHON_DEF_PATTERN.matcher(rawLine);
            if (defMatcher.find()) {
                outputLines.addAll(decoratorBuffer);
                decoratorBuffer.clear();
                String indent = defMatcher.group(1);
                String sig = rawLine.strip();
                if (sig.endsWith(":")) {
                    sig = sig.substring(0, sig.length() - 1).strip();
                    outputLines.add(indent + sig + ": ...");
                } else {
                    accumulatingDef = true;
                    defBuffer.append(indent).append(sig);
                }
                continue;
            }

            if (stripped.startsWith("def ") || stripped.startsWith("async def ")) {
                accumulatingDef = true;
                defBuffer.append(rawLine);
            }
        }

        String result = String.join("\n", outputLines).trim();
        return result.length() < 30 ? fallbackSkeleton(content) : result;
    }

    private static String fallbackSkeleton(String content) {
        List<String> preserved = new ArrayList<>(16);
        int length = content.length();
        int lineStart = 0;
        while (lineStart < length) {
            int lineEnd = findLineEnd(content, lineStart, length);
            String line = content.substring(lineStart, lineEnd);
            lineStart = advancePastLineBreak(content, lineEnd, length);
            if (preserved.size() >= 15) {
                preserved.add("// ... [content truncated for skeleton]");
                break;
            }
            preserved.add(line);
        }
        return String.join("\n", preserved).trim();
    }

    private static int findLineEnd(String content, int start, int length) {
        int index = start;
        while (index < length) {
            char ch = content.charAt(index);
            if (ch == '\n' || ch == '\r') {
                return index;
            }
            index++;
        }
        return length;
    }

    private static int advancePastLineBreak(String content, int lineEnd, int length) {
        if (lineEnd < length && content.charAt(lineEnd) == '\r') {
            lineEnd++;
        }
        if (lineEnd < length && content.charAt(lineEnd) == '\n') {
            lineEnd++;
        }
        return lineEnd;
    }

    private static boolean looksLikeTypeDeclaration(String line) {
        return TYPE_DECLARATION_PATTERN.matcher(line).find();
    }

    private static boolean looksLikeMethodStart(String line) {
        if (line.indexOf('(') < 0 || line.startsWith("@")) {
            return false;
        }
        String firstWord = extractFirstToken(line);
        if (CONTROL_KEYWORDS.contains(firstWord)) {
            return false;
        }
        return METHOD_START_PATTERN.matcher(line).find()
            || (line.indexOf(')') >= 0 && (line.indexOf('{') >= 0 || line.endsWith(";")));
    }

    private static String extractFirstToken(String line) {
        int length = line.length();
        int start = 0;
        while (start < length && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < length) {
            char ch = line.charAt(end);
            if (ch == '(' || Character.isWhitespace(ch)) {
                break;
            }
            end++;
        }
        return line.substring(start, end);
    }

    private static String formatSignature(String signature) {
        String cleaned = signature.strip();
        return "    " + cleaned + (cleaned.endsWith(";") ? "" : " { /* ... */ }");
    }

    private static void flushAnnotations(List<String> buffer, List<String> target) {
        target.addAll(buffer);
        buffer.clear();
    }

    /**
     * 직전 연속 역슬래시의 짝수 여부로 이스케이프되지 않은 따옴표 종료({@code "\\"})를 정확히 판정하고
     * 여는/닫는 중괄호 개수를 단일 32비트 int(상위 16비트='{', 하위 16비트='}')에 패킹합니다.
     */
    static int packBraceCounts(String line) {
        int openCount = 0;
        int closeCount = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        int trailingBackslashes = 0;

        for (int i = 0, length = line.length(); i < length; i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '\\') {
                    trailingBackslashes++;
                    continue;
                }
                if (c == quoteChar && (trailingBackslashes & 1) == 0) {
                    inQuote = false;
                }
                trailingBackslashes = 0;
                continue;
            }

            if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
                trailingBackslashes = 0;
            } else if (c == '{') {
                openCount++;
            } else if (c == '}') {
                closeCount++;
            }
        }
        return ((openCount & 0xFFFF) << 16) | (closeCount & 0xFFFF);
    }
}
