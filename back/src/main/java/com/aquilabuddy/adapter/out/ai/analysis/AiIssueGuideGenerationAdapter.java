package com.aquilabuddy.adapter.out.ai.analysis;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideStreamChunk;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * AI completion 결과를 issue guide 생성 outbound port로 연결합니다.
 */
@Component
@ConditionalOnExpression("'${app.ai.provider:fixture}' == 'openai-compatible' || '${app.ai.provider:fixture}' == 'vertex-ai'")
public class AiIssueGuideGenerationAdapter implements GenerateIssueGuidePort {

    private static final String SYSTEM_PROMPT = """
        You are an issue guide generator for an open-source contribution assistant.
        Write GUIDELINE, PSEUDO_CODE, and SIDE_EFFECTS in Korean.
        Use only the provided issue metadata and repository code context.
        Base GUIDELINE, PSEUDO_CODE, and SIDE_EFFECTS on repository code context when it is present.
        When repository code context is present, mention at least one exact file name, class, method, function, or config key from that context.
        Do not invent validation, error handling, database, UI, or test behavior that is not visible in the issue metadata or code context.
        If the provided code context is too thin to determine the exact implementation point, say what must be inspected next instead of pretending it is known.
        GUIDELINE must explain root cause, intended edit direction, and first verification in natural Korean sentences.
        GUIDELINE may mention target files or symbols, but must not become a repeated list of method names.
        Do not mention controller, service, UI, or API side effects unless those roles are visible in the selected repository code context.
        Do not write generic GUIDELINE text such as "follow the validation flow" unless it names the concrete failing condition and edit.
        PSEUDO_CODE must be language-neutral pseudocode, not a numbered explanation checklist.
        PSEUDO_CODE must use code-like lines such as GIVEN, WHEN, IF, CALL, RETURN, ASSERT, or VERIFY.
        PSEUDO_CODE must include concrete input values, failing condition, changed branch, and expected assertion when they are visible from the issue.
        Do not use abstract placeholder helpers such as apply_smallest_change, preserve_existing_flow, normal_input, failing_input, issue_failure, or target_symbol.
        Return exactly this marker format.
        GUIDELINE: <2-4 concrete Korean sentences with target, failure condition, edit direction, and first verification>
        PSEUDO_CODE:
        <4-8 lines of code-like pseudocode grounded in the target file or symbol; do not number the lines>
        SIDE_EFFECTS:
        - <2-4 concrete side effect or verification concern>
        """;
    private static final Pattern METHOD_REFERENCE_PATTERN = Pattern.compile("\\b[a-z][A-Za-z0-9_]*\\s*\\(\\)");
    private static final Pattern NUMBERED_LINE_PATTERN = Pattern.compile("^\\d+[.)]\\s+.*");
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^a-z0-9._/-]+");
    private static final Pattern ISSUE_TERM_SPLIT_PATTERN = Pattern.compile("[^\\p{L}\\p{N}_#()./-]+");
    private static final List<String> CODE_LIKE_SIGNALS = List.of(
        "GIVEN", "WHEN", "IF", "ELSE", "CALL", "RETURN", "ASSERT", "VERIFY", "THEN", "->", "="
    );
    private static final List<String> EDIT_SIGNALS = List.of(
        "수정", "변경", "교체", "적용", "개선", "fix", "update", "change", "replace", "implement", "adjust"
    );
    private static final List<String> VERIFICATION_SIGNALS = List.of(
        "검증", "테스트", "재현", "확인", "test", "verify", "regression", "assert"
    );
    private static final List<String> FORBIDDEN_TOKENS = List.of(
        "apply_smallest_change",
        "preserve_existing_flow",
        "normal_input",
        "failing_input",
        "issue_failure",
        "target_symbol",
        "target_file"
    );

    private final AiClient aiClient;

    @Autowired
    public AiIssueGuideGenerationAdapter(AiClient aiClient) {
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null.");
    }

    @Override
    public IssueGuide generateIssueGuide(RecommendedIssue issue) {
        return generateIssueGuide(issue, RepositoryCodeContext.empty());
    }

    @Override
    public IssueGuide generateIssueGuide(RecommendedIssue issue, RepositoryCodeContext codeContext) {
        RepositoryCodeContext safeContext = codeContext == null ? RepositoryCodeContext.empty() : codeContext;
        return parseGuide(
            issue,
            safeContext,
            aiClient.complete(AiCompletionUseCase.ISSUE_GUIDE, SYSTEM_PROMPT, buildPrompt(issue, safeContext))
        );
    }

    @Override
    public IssueGuide streamIssueGuide(
        RecommendedIssue issue,
        RepositoryCodeContext codeContext,
        Consumer<IssueGuideStreamChunk> onChunk
    ) {
        RepositoryCodeContext safeContext = codeContext == null ? RepositoryCodeContext.empty() : codeContext;
        Consumer<IssueGuideStreamChunk> safeConsumer = onChunk == null ? ignored -> { } : onChunk;
        IssueGuideStreamingSectionParser parser = new IssueGuideStreamingSectionParser(safeConsumer);
        String fullResponse = aiClient.stream(
            AiCompletionUseCase.ISSUE_GUIDE,
            SYSTEM_PROMPT,
            buildPrompt(issue, safeContext),
            parser::accept
        );
        parser.finish();
        return parseGuide(issue, safeContext, fullResponse);
    }

    private static String buildPrompt(RecommendedIssue issue, RepositoryCodeContext codeContext) {
        List<String> lines = new ArrayList<>(List.of(
            "Issue ID: " + issue.id(),
            "Repository: " + issue.repoFullName(),
            "Issue number: #" + issue.issueNumber(),
            "Title: " + issue.title(),
            "Summary: " + issue.summary(),
            "Labels: " + String.join(", ", issue.labels()),
            "Status: " + issue.status(),
            "Recommendation reason: " + issue.reason()
        ));
        RepositoryCodeContext safeContext = codeContext == null ? RepositoryCodeContext.empty() : codeContext;
        lines.addAll(safeContext.promptSummaryLines());
        return String.join("\n", lines);
    }

    private static IssueGuide parseGuide(RecommendedIssue issue, RepositoryCodeContext codeContext, String response) {
        String normalized = Objects.requireNonNull(response, "AI response must not be null.").trim();
        int guidelineIndex = normalized.indexOf("GUIDELINE:");
        int pseudoCodeIndex = normalized.indexOf("PSEUDO_CODE:");
        int sideEffectsIndex = normalized.indexOf("SIDE_EFFECTS:");
        if (guidelineIndex < 0 || pseudoCodeIndex < 0 || sideEffectsIndex < 0
            || pseudoCodeIndex <= guidelineIndex || sideEffectsIndex <= pseudoCodeIndex) {
            throw new IllegalStateException("AI issue guide response must contain GUIDELINE, PSEUDO_CODE, and SIDE_EFFECTS markers.");
        }

        String guideline = normalized.substring(guidelineIndex + "GUIDELINE:".length(), pseudoCodeIndex).trim();
        String pseudoCode = normalized.substring(pseudoCodeIndex + "PSEUDO_CODE:".length(), sideEffectsIndex).trim();
        List<String> sideEffects = parseSideEffects(normalized.substring(sideEffectsIndex + "SIDE_EFFECTS:".length()));
        if (guideline.isBlank() || pseudoCode.isBlank() || sideEffects.isEmpty()) {
            throw new IllegalStateException("AI issue guide response sections must not be blank.");
        }
        requireCodeLikePseudoCode(pseudoCode);

        IssueGuide guide = new IssueGuide(issue.id(), selectedFilePaths(codeContext), guideline, pseudoCode, sideEffects);
        requireGroundedInCodeContext(guide, codeContext);
        requireActionableGuideline(guide, issue, codeContext);
        return guide;
    }

    private static void requireGroundedInCodeContext(IssueGuide guide, RepositoryCodeContext codeContext) {
        List<String> anchors = IssueGuideCodeContextAnchors.groundingAnchors(codeContext);
        if (anchors.isEmpty()) {
            return;
        }

        String guideText = String.join("\n",
            guide.guideline(),
            guide.pseudoCode(),
            String.join("\n", guide.sideEffects())
        ).toLowerCase(Locale.ROOT);
        boolean grounded = anchors.stream()
            .map(anchor -> anchor.toLowerCase(Locale.ROOT))
            .anyMatch(guideText::contains);
        if (!grounded) {
            throw new IllegalStateException("AI issue guide response must reference repository code context.");
        }
    }

    private static void requireActionableGuideline(IssueGuide guide, RecommendedIssue issue, RepositoryCodeContext codeContext) {
        if (codeContext == null || !codeContext.hasFiles()) {
            return;
        }

        String guideline = guide.guideline().toLowerCase(Locale.ROOT);
        if (!containsAny(guideline, issueTerms(issue))) {
            throw new IllegalStateException("AI issue guide GUIDELINE must mention an issue-specific failure or requirement.");
        }
        if (!containsAny(guideline, editSignals())) {
            throw new IllegalStateException("AI issue guide GUIDELINE must include an edit direction.");
        }
        if (!containsAny(guideline, verificationSignals())) {
            throw new IllegalStateException("AI issue guide GUIDELINE must include first verification.");
        }
        requireNoExcessiveSymbolListing(guide);
        requireNoGenericRoleMismatch(guide, codeContext);
    }

    private static void requireNoExcessiveSymbolListing(IssueGuide guide) {
        Matcher matcher = METHOD_REFERENCE_PATTERN.matcher(guide.guideline());
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        if (count > 6) {
            throw new IllegalStateException("AI issue guide GUIDELINE must not be dominated by method name lists.");
        }
    }

    private static void requireNoGenericRoleMismatch(IssueGuide guide, RepositoryCodeContext codeContext) {
        String guideText = String.join("\n",
            guide.guideline(),
            String.join("\n", guide.sideEffects())
        ).toLowerCase(Locale.ROOT);
        String contextText = contextText(codeContext);
        if (guideText.contains("controller/service")
            && !contextText.contains("controller")
            && !contextText.contains("service")) {
            throw new IllegalStateException("AI issue guide must not mention generic controller/service side effects.");
        }
        if (guideText.contains("controller") && !contextText.contains("controller")) {
            throw new IllegalStateException("AI issue guide must not mention controller side effects without controller context.");
        }
        if (guideText.contains("service") && !contextText.contains("service")) {
            throw new IllegalStateException("AI issue guide must not mention service side effects without service context.");
        }
    }

    private static String contextText(RepositoryCodeContext codeContext) {
        if (codeContext == null || !codeContext.hasFiles()) {
            return "";
        }
        return codeContext.files()
            .stream()
            .map(file -> file.path() + "\n" + file.content())
            .collect(java.util.stream.Collectors.joining("\n"))
            .toLowerCase(Locale.ROOT);
    }

    private static void requireCodeLikePseudoCode(String pseudoCode) {
        List<String> lines = Arrays.stream(pseudoCode.split("\\R"))
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .toList();
        String upperPseudoCode = pseudoCode.toUpperCase(Locale.ROOT);
        long numberedLines = lines.stream()
            .filter(line -> NUMBERED_LINE_PATTERN.matcher(line).matches())
            .count();
        if (!containsAny(upperPseudoCode, codeLikeSignals())) {
            throw new IllegalStateException("AI issue guide PSEUDO_CODE must contain code-like control or assertion lines.");
        }
        if (numberedLines >= Math.max(2, lines.size() / 2)) {
            throw new IllegalStateException("AI issue guide PSEUDO_CODE must not be a numbered checklist.");
        }
        requireNoAbstractPseudoCodePlaceholders(pseudoCode);
    }

    private static void requireNoAbstractPseudoCodePlaceholders(String pseudoCode) {
        String normalized = pseudoCode.toLowerCase(Locale.ROOT);
        for (String token : FORBIDDEN_TOKENS) {
            if (normalized.contains(token)) {
                throw new IllegalStateException("AI issue guide PSEUDO_CODE must not use abstract placeholder: " + token);
            }
        }
        Matcher matcher = METHOD_REFERENCE_PATTERN.matcher(pseudoCode);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        if (count > 8) {
            throw new IllegalStateException("AI issue guide PSEUDO_CODE must not be dominated by method name lists.");
        }
    }

    private static List<String> codeLikeSignals() {
        return CODE_LIKE_SIGNALS;
    }

    private static List<String> issueTerms(RecommendedIssue issue) {
        return Arrays.stream(ISSUE_TERM_SPLIT_PATTERN.split(issue.title() + " " + issue.summary()))
            .map(term -> term.toLowerCase(Locale.ROOT).trim())
            .filter(term -> term.length() >= 4)
            .distinct()
            .toList();
    }

    private static List<String> editSignals() {
        return EDIT_SIGNALS;
    }

    private static List<String> verificationSignals() {
        return VERIFICATION_SIGNALS;
    }

    private static boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private static List<String> selectedFilePaths(RepositoryCodeContext codeContext) {
        if (codeContext == null || !codeContext.hasFiles()) {
            return List.of();
        }

        return codeContext.files().stream()
            .map(file -> file.path())
            .filter(path -> path != null && !path.isBlank())
            .distinct()
            .toList();
    }

    private static List<String> parseSideEffects(String sideEffectBlock) {
        return Arrays.stream(sideEffectBlock.split("\\R"))
            .map(String::trim)
            .map(line -> line.startsWith("-") ? line.substring(1).trim() : line)
            .filter(line -> !line.isBlank())
            .toList();
    }
}
