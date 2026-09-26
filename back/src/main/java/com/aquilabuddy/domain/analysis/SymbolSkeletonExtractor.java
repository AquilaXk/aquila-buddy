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
 * <p>전체 구현 본문을 제외하고 API 계약과 심볼 선언부만 유지하여 토큰 70% 이상을 절감합니다.
 */
public final class SymbolSkeletonExtractor {

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

    private static final Pattern PYTHON_DEF_PATTERN = Pattern.compile("^(\\s*)(?:async\\s+)?def\\s+([a-zA-Z0-9_]+)\\s*\\((.*?)\\)\\s*(?:->\\s*[^:]+)?:");
    private static final Pattern PYTHON_CLASS_PATTERN = Pattern.compile("^(\\s*)class\\s+([a-zA-Z0-9_]+)(?:\\(.*?\\))?:");

    private SymbolSkeletonExtractor() {
    }

    /**
     * 파일 확장자와 내용을 기반으로 심볼 스켈레톤을 추출합니다.
     */
    public static String extractSkeleton(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String trimmed = content.trim();
        if (trimmed.length() <= 120 || !trimmed.contains("\n")) {
            return trimmed;
        }

        String lowerPath = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".py")) {
            return extractPythonSkeleton(trimmed);
        }

        if (isBraceLanguage(lowerPath)) {
            return extractBraceSkeleton(trimmed);
        }

        return fallbackSkeleton(trimmed);
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
     * 중괄호 블록 기반 언어(Java, TS, Go 등)에서 메서드 내부를 제거하고 시그니처만 유지합니다.
     */
    private static String extractBraceSkeleton(String content) {
        String[] lines = content.split("\\R");
        List<String> outputLines = new ArrayList<>();
        List<String> annotationBuffer = new ArrayList<>();
        StringBuilder signatureBuffer = new StringBuilder();
        StringBuilder typeBuffer = new StringBuilder();

        int braceDepth = 0;
        int methodBodyDepth = -1;
        boolean accumulatingSignature = false;
        boolean accumulatingType = false;

        for (String rawLine : lines) {
            String stripped = rawLine.strip();
            if (stripped.isBlank() || stripped.startsWith("//") || stripped.startsWith("/*") || stripped.startsWith("*")) {
                continue;
            }

            // import 구문은 프롬프트 토큰 절감을 위해 과감히 생략합니다.
            if (stripped.startsWith("import ") || stripped.startsWith("package ")) {
                continue;
            }

            int lineOpens = countOccurrences(stripped, '{');
            int lineCloses = countOccurrences(stripped, '}');

            // 1. 이미 메서드 본문 내부인 경우 본문 줄을 건너뜁니다.
            if (methodBodyDepth >= 0) {
                braceDepth += (lineOpens - lineCloses);
                if (braceDepth <= methodBodyDepth) {
                    methodBodyDepth = -1;
                }
                continue;
            }

            // 2. 어노테이션 수집
            if (stripped.startsWith("@")) {
                annotationBuffer.add(rawLine);
                continue;
            }

            // 3. 멀티라인 타입 선언 누적 중
            if (accumulatingType) {
                typeBuffer.append(" ").append(stripped);
                if (stripped.contains("{")) {
                    flushAnnotations(annotationBuffer, outputLines);
                    outputLines.add(typeBuffer.toString().strip());
                    accumulatingType = false;
                    typeBuffer.setLength(0);
                    braceDepth += (lineOpens - lineCloses);
                }
                continue;
            }

            // 4. 타입 선언 (class, interface, record, enum 등)
            if (looksLikeTypeDeclaration(stripped)) {
                if (stripped.contains("{")) {
                    flushAnnotations(annotationBuffer, outputLines);
                    outputLines.add(rawLine);
                    braceDepth += (lineOpens - lineCloses);
                } else {
                    accumulatingType = true;
                    typeBuffer.append(stripped);
                }
                continue;
            }

            // 5. 멀티라인 시그니처 누적 중
            if (accumulatingSignature) {
                signatureBuffer.append(" ").append(stripped);
                if (stripped.contains("{") || stripped.endsWith(";")) {
                    flushAnnotations(annotationBuffer, outputLines);
                    String sig = signatureBuffer.toString().strip();
                    if (sig.endsWith("{")) {
                        sig = sig.substring(0, sig.length() - 1).strip();
                    }
                    outputLines.add(formatSignature(sig));
                    accumulatingSignature = false;
                    signatureBuffer.setLength(0);

                    // 한 줄에 열고 닫히는 메서드는 methodBodyDepth를 활성화하지 않습니다.
                    if (stripped.contains("{")) {
                        int netOpens = lineOpens - lineCloses;
                        if (netOpens > 0) {
                            methodBodyDepth = braceDepth;
                            braceDepth += netOpens;
                        } else {
                            braceDepth += netOpens;
                        }
                    }
                }
                continue;
            }

            // 6. 메서드 / 함수 시그니처 감지
            if (looksLikeMethodStart(stripped)) {
                if (stripped.contains("{")) {
                    flushAnnotations(annotationBuffer, outputLines);
                    String sig = stripped.substring(0, stripped.indexOf('{')).strip();
                    outputLines.add(formatSignature(sig));
                    // 한 줄에 정의와 종료가 모두 일어난 경우(one-line method) 다음 줄이 스킵되지 않도록 처리합니다.
                    int netOpens = lineOpens - lineCloses;
                    if (netOpens > 0) {
                        methodBodyDepth = braceDepth;
                        braceDepth += netOpens;
                    } else {
                        braceDepth += netOpens;
                    }
                } else if (stripped.endsWith(";")) {
                    flushAnnotations(annotationBuffer, outputLines);
                    outputLines.add(rawLine);
                    braceDepth += (lineOpens - lineCloses);
                } else {
                    // 다음 줄까지 이어지는 시그니처 시작
                    accumulatingSignature = true;
                    signatureBuffer.append(stripped);
                }
                continue;
            }

            // 7. 클래스/블록 닫는 괄호
            if (stripped.equals("}") || stripped.startsWith("}")) {
                annotationBuffer.clear();
                braceDepth += (lineOpens - lineCloses);
                outputLines.add(rawLine);
                continue;
            }

            // 8. 기타 최상위 / 인터페이스 메서드 등
            braceDepth += (lineOpens - lineCloses);
        }

        String result = String.join("\n", outputLines).trim();
        // 스켈레톤 추출 결과가 너무 빈약하면(오인식 등) 원본의 앞부분을 안전하게 반환합니다.
        if (result.length() < 30) {
            return fallbackSkeleton(content);
        }
        return result;
    }

    private static String extractPythonSkeleton(String content) {
        String[] lines = content.split("\\R");
        List<String> outputLines = new ArrayList<>();
        List<String> decoratorBuffer = new ArrayList<>();
        StringBuilder defBuffer = new StringBuilder();
        boolean accumulatingDef = false;

        for (String rawLine : lines) {
            String stripped = rawLine.strip();
            if (stripped.isBlank() || stripped.startsWith("#")) {
                continue;
            }

            // 파이썬 데코레이터(@app.get, @property 등)를 보존합니다.
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
                defBuffer.append(" ").append(stripped);
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
                continue;
            }
        }

        String result = String.join("\n", outputLines).trim();
        return result.length() < 30 ? fallbackSkeleton(content) : result;
    }

    private static String fallbackSkeleton(String content) {
        String[] lines = content.split("\\R");
        List<String> preserved = new ArrayList<>();
        for (String line : lines) {
            if (preserved.size() >= 15) {
                preserved.add("// ... [content truncated for skeleton]");
                break;
            }
            preserved.add(line);
        }
        return String.join("\n", preserved).trim();
    }

    private static boolean looksLikeTypeDeclaration(String line) {
        return TYPE_DECLARATION_PATTERN.matcher(line).find();
    }

    private static boolean looksLikeMethodStart(String line) {
        if (!line.contains("(") || line.startsWith("@")) {
            return false;
        }
        String firstWord = line.stripLeading().split("[\\s(]+", 2)[0];
        if (CONTROL_KEYWORDS.contains(firstWord)) {
            return false;
        }
        return METHOD_START_PATTERN.matcher(line).find() || (line.contains(")") && (line.contains("{") || line.endsWith(";")));
    }

    private static String formatSignature(String signature) {
        String cleaned = signature.strip();
        return "    " + cleaned + (cleaned.endsWith(";") ? "" : " { /* ... */ }");
    }

    private static void flushAnnotations(List<String> buffer, List<String> target) {
        target.addAll(buffer);
        buffer.clear();
    }

    private static int countOccurrences(String line, char ch) {
        int count = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!inQuote && (c == '"' || c == '\'')) {
                inQuote = true;
                quoteChar = c;
            } else if (inQuote && c == quoteChar && (i == 0 || line.charAt(i - 1) != '\\')) {
                inQuote = false;
            } else if (!inQuote && c == ch) {
                count++;
            }
        }
        return count;
    }
}
