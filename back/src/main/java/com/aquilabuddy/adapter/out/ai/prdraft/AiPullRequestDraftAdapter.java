package com.aquilabuddy.adapter.out.ai.prdraft;

import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.TranslatePullRequestDraftPort;
import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryFormat;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * AI completion 결과를 PR 초안 생성/번역 outbound port로 연결합니다.
 */
@Component
@ConditionalOnExpression("'${app.ai.provider:fixture}' == 'openai-compatible' || '${app.ai.provider:fixture}' == 'vertex-ai'")
public class AiPullRequestDraftAdapter implements GeneratePullRequestDraftPort, TranslatePullRequestDraftPort {

    private static final String DRAFT_SYSTEM_PROMPT = """
        너는 오픈소스 기여자를 돕는 PR 초안 작성자다.
        입력된 저장소, 이슈, 브랜치, diff, 가이드 맥락만 사용해 PR 초안을 작성한다.
        CONTRIBUTING, PR template, merged PR sample이 있으면 저장소별 작성 관례로 우선 반영한다.
        응답은 반드시 다음 형식만 사용한다.
        TITLE: <한 줄 PR 제목>
        BODY: <마크다운 PR 본문>
        """;
    private static final String TRANSLATION_SYSTEM_PROMPT = """
        너는 한국어 PR 초안을 자연스러운 영문 PR 초안으로 바꾸는 번역자다.
        의미를 보존하고 과장하지 않는다.
        응답은 반드시 다음 형식만 사용한다.
        TITLE: <English PR title>
        BODY: <English markdown PR body>
        """;

    private final AiClient aiClient;

    public AiPullRequestDraftAdapter(AiClient aiClient) {
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null.");
    }

    @Override
    public PullRequestDraft generate(PullRequestDraftRequest request) {
        String response;
        try {
            response = aiClient.complete(AiCompletionUseCase.PR_DRAFT, DRAFT_SYSTEM_PROMPT, buildDraftPrompt(request));
        } catch (RuntimeException exception) {
            return ensureClosingIssueLine(fallbackDraft(request), request.githubIssueNumber());
        }
        return ensureClosingIssueLine(
            parseDraftOrFallback(response, () -> fallbackDraft(request), request.repositoryGuideContext().draftFormat()),
            request.githubIssueNumber()
        );
    }

    @Override
    public PullRequestDraft translate(StoredPullRequestDraft draft, String targetLanguage) {
        String normalizedLanguage = normalizeTargetLanguage(targetLanguage);
        String response;
        try {
            response = aiClient.complete(
                AiCompletionUseCase.PR_TRANSLATION,
                TRANSLATION_SYSTEM_PROMPT,
                buildTranslationPrompt(draft, normalizedLanguage)
            );
        } catch (RuntimeException exception) {
            return ensureClosingIssueLine(fallbackTranslation(draft), draft.githubIssueNumber());
        }
        return ensureClosingIssueLine(
            parseDraftOrFallback(response, () -> fallbackTranslation(draft)),
            draft.githubIssueNumber()
        );
    }

    private static String buildDraftPrompt(PullRequestDraftRequest request) {
        List<String> lines = new ArrayList<>(List.of(
            "Repository: " + request.upstreamRepo(),
            "Issue: #" + request.githubIssueNumber(),
            "Base branch: " + request.baseBranch(),
            "Head branch: " + request.headBranch(),
            "",
            "Diff:",
            request.diffContent() == null ? "(no diff content)" : request.diffContent()
        ));

        appendSection(lines, "Repository guide summary", request.repositoryGuideContext().guideSummaryLines());
        appendSection(lines, "Repository guide context", request.repositoryGuideContext().guidePromptLines());
        appendRepositoryGuideQualityRules(lines, request);
        appendSection(lines, "Repository draft format", request.repositoryGuideContext().draftFormat().promptLines());
        appendDraftQualityRequirements(lines);
        appendSection(lines, "Issue context", request.issueContext().contextSummaryLines());
        appendSection(lines, "Analysis context", request.analysisContext().contextSummaryLines());
        return String.join("\n", lines);
    }

    private static String buildTranslationPrompt(StoredPullRequestDraft draft, String targetLanguage) {
        return String.join("\n",
            "Repository: " + draft.upstreamRepo(),
            "Issue: #" + draft.githubIssueNumber(),
            "Base branch: " + draft.baseBranch(),
            "Head branch: " + draft.headBranch(),
            "Target language: " + targetLanguage,
            "Source title: " + draft.title(),
            "",
            "Source body:",
            draft.body()
        );
    }

    private static void appendSection(List<String> lines, String title, List<String> values) {
        if (values.isEmpty()) {
            return;
        }

        lines.add("");
        lines.add(title + ":");
        lines.addAll(values);
    }

    private static void appendRepositoryGuideQualityRules(List<String> lines, PullRequestDraftRequest request) {
        if (!request.repositoryGuideContext().hasAnyGuide()) {
            return;
        }

        lines.add("");
        lines.add("Repository guide quality rules:");
        lines.add("- Use PR template headings and checkboxes as required body structure when a PR template is present.");
        lines.add("- If there is no PR template, follow the common heading order inferred from merged PR samples.");
        lines.add("- Follow CONTRIBUTING instructions without inventing repository rules.");
        lines.add("- Use merged PR samples as structure references only when a PR template is absent.");
    }

    private static void appendDraftQualityRequirements(List<String> lines) {
        lines.add("");
        lines.add("Draft quality requirements:");
        lines.add("- Base Changes and Tests on the diff content.");
        lines.add("- Explain why the change matters using Issue context.");
        lines.add("- Convert Analysis context into implementation notes without copying it verbatim.");
        lines.add("- Do not mention files, tests, or side effects that are not present in the provided context.");
    }

    private static PullRequestDraft parseDraft(String response) {
        String normalized = Objects.requireNonNull(response, "AI response must not be null.").trim();
        int titleIndex = normalized.indexOf("TITLE:");
        int bodyIndex = normalized.indexOf("BODY:");
        if (titleIndex < 0 || bodyIndex < 0 || bodyIndex <= titleIndex) {
            throw new IllegalStateException("AI response must contain TITLE and BODY markers.");
        }

        String title = normalized.substring(titleIndex + "TITLE:".length(), bodyIndex).trim();
        String body = normalized.substring(bodyIndex + "BODY:".length()).trim();
        if (title.isBlank() || body.isBlank()) {
            throw new IllegalStateException("AI response TITLE and BODY must not be blank.");
        }
        return new PullRequestDraft(title, body);
    }

    private static PullRequestDraft parseDraftOrFallback(String response, Supplier<PullRequestDraft> fallback) {
        return parseDraftOrFallback(
            response,
            fallback,
            PullRequestDraftRepositoryFormat.generic("repository format validation is not required")
        );
    }

    private static PullRequestDraft parseDraftOrFallback(
        String response,
        Supplier<PullRequestDraft> fallback,
        PullRequestDraftRepositoryFormat format
    ) {
        try {
            PullRequestDraft draft = parseDraft(response);
            return format.isSatisfiedBy(draft.body()) ? draft : fallback.get();
        } catch (IllegalStateException | NullPointerException ignored) {
            return fallback.get();
        }
    }

    private static PullRequestDraft ensureClosingIssueLine(PullRequestDraft draft, long githubIssueNumber) {
        String closingLine = "close #" + githubIssueNumber;
        if (hasClosingIssueLine(draft.body(), githubIssueNumber)) {
            return draft;
        }
        return new PullRequestDraft(draft.title(), closingLine + "\n\n" + draft.body());
    }

    private static boolean hasClosingIssueLine(String body, long githubIssueNumber) {
        Pattern closingPattern = Pattern.compile(
            "(?im)^\\s*(close|closes|closed|fix|fixes|fixed|resolve|resolves|resolved)\\s+#"
                + githubIssueNumber + "\\b"
        );
        return closingPattern.matcher(body).find();
    }

    private static PullRequestDraft fallbackDraft(PullRequestDraftRequest request) {
        String title = "docs: " + request.upstreamRepo() + "#" + request.githubIssueNumber() + " 변경 초안";
        PullRequestDraftRepositoryFormat format = request.repositoryGuideContext().draftFormat();
        if (format.source() == PullRequestDraftRepositoryFormat.Source.TEMPLATE) {
            return new PullRequestDraft(title, repositoryTemplateFallbackBody(request, format));
        }
        if (format.source() == PullRequestDraftRepositoryFormat.Source.MERGED_PULL_REQUEST_SAMPLES) {
            return new PullRequestDraft(title, inferredRepositoryFormatFallbackBody(request, format));
        }

        List<String> lines = new ArrayList<>(List.of(
            "close #" + request.githubIssueNumber(),
            "",
            "## Summary",
            "- " + request.upstreamRepo() + " #" + request.githubIssueNumber() + " 변경 초안입니다."
        ));
        appendFallbackChangeAndTests(lines, request);
        appendFallbackIssueContext(lines, request);
        appendFallbackAnalysisContext(lines, request);
        return new PullRequestDraft(title, String.join("\n", lines));
    }

    private static String repositoryTemplateFallbackBody(
        PullRequestDraftRequest request,
        PullRequestDraftRepositoryFormat format
    ) {
        List<String> lines = new ArrayList<>(List.of(
            "close #" + request.githubIssueNumber(),
            ""
        ));
        format.templateBody().lines()
            .map(String::stripTrailing)
            .forEach(lines::add);
        appendFallbackChangeAndTests(lines, request);
        appendFallbackIssueContext(lines, request);
        appendFallbackAnalysisContext(lines, request);
        return String.join("\n", lines);
    }

    private static String inferredRepositoryFormatFallbackBody(
        PullRequestDraftRequest request,
        PullRequestDraftRepositoryFormat format
    ) {
        List<String> lines = new ArrayList<>(List.of("close #" + request.githubIssueNumber()));
        for (String heading : format.requiredHeadings()) {
            lines.add("");
            lines.add(heading);
            lines.add("- " + fallbackLineForHeading(heading, request));
        }
        appendFallbackIssueContext(lines, request);
        appendFallbackAnalysisContext(lines, request);
        return String.join("\n", lines);
    }

    private static void appendFallbackChangeAndTests(List<String> lines, PullRequestDraftRequest request) {
        lines.add("");
        lines.add("## Changes");
        lines.add("- " + firstMeaningfulLine(request.diffContent(), "요청된 변경 내용을 정리합니다."));
        lines.add("");
        lines.add("## Tests");
        lines.add("- 변경 후 관련 검증을 실행합니다.");
    }

    private static String fallbackLineForHeading(String heading, PullRequestDraftRequest request) {
        String normalizedHeading = heading.toLowerCase(Locale.ROOT);
        if (normalizedHeading.contains("test") || normalizedHeading.contains("검증")) {
            return "변경 후 관련 검증을 실행합니다.";
        }
        if (normalizedHeading.contains("why") || normalizedHeading.contains("reason")) {
            return request.issueContext().hasIssue()
                ? request.issueContext().reason()
                : request.upstreamRepo() + " #" + request.githubIssueNumber() + " 변경 목적을 정리합니다.";
        }
        return firstMeaningfulLine(request.diffContent(), request.upstreamRepo() + " #"
            + request.githubIssueNumber() + " 변경 내용을 정리합니다.");
    }

    private static PullRequestDraft fallbackTranslation(StoredPullRequestDraft draft) {
        String body = String.join("\n",
            "close #" + draft.githubIssueNumber(),
            "",
            "## Summary",
            "- Translation could not be generated automatically.",
            "",
            "## Source title",
            draft.title(),
            "",
            "## Source body",
            draft.body()
        );
        return new PullRequestDraft("docs: translate pull request draft", body);
    }

    private static String firstMeaningfulLine(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .filter(line -> !line.startsWith("diff --git"))
            .findFirst()
            .orElse(fallback);
    }

    private static void appendFallbackIssueContext(List<String> lines, PullRequestDraftRequest request) {
        if (!request.issueContext().hasIssue()) {
            return;
        }

        lines.add("");
        lines.add("## Issue Context");
        appendFallbackBullet(lines, request.issueContext().title());
        appendFallbackBullet(lines, request.issueContext().summary());
        appendFallbackBullet(lines, request.issueContext().reason());
    }

    private static void appendFallbackAnalysisContext(List<String> lines, PullRequestDraftRequest request) {
        if (!request.analysisContext().hasAnalysis()) {
            return;
        }

        lines.add("");
        lines.add("## Implementation Notes");
        appendFallbackBullet(lines, request.analysisContext().guideline());
        appendFallbackBullet(lines, request.analysisContext().pseudoCode());
        request.analysisContext().sideEffects().forEach(sideEffect -> appendFallbackBullet(lines, sideEffect));
    }

    private static void appendFallbackBullet(List<String> lines, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        lines.add("- " + value.strip());
    }

    private static String normalizeTargetLanguage(String targetLanguage) {
        String normalizedLanguage = targetLanguage == null ? "" : targetLanguage.trim().toLowerCase(Locale.ROOT);
        if (!"en".equals(normalizedLanguage)) {
            throw new IllegalArgumentException("targetLanguage only supports en.");
        }
        return normalizedLanguage;
    }
}
