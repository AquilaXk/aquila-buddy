package com.aquilabuddy.adapter.out.ai.prdraft;

import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.TranslatePullRequestDraftPort;
import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI completion 결과를 PR 초안 생성/번역 outbound port로 연결합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai-compatible")
public class AiPullRequestDraftAdapter implements GeneratePullRequestDraftPort, TranslatePullRequestDraftPort {

    private static final String DRAFT_SYSTEM_PROMPT = """
        너는 오픈소스 기여자를 돕는 PR 초안 작성자다.
        입력된 저장소, 이슈, 브랜치, diff, 가이드 맥락만 사용해 PR 초안을 작성한다.
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
        return parseDraft(aiClient.complete(DRAFT_SYSTEM_PROMPT, buildDraftPrompt(request)));
    }

    @Override
    public PullRequestDraft translate(StoredPullRequestDraft draft, String targetLanguage) {
        String normalizedLanguage = normalizeTargetLanguage(targetLanguage);
        return parseDraft(aiClient.complete(TRANSLATION_SYSTEM_PROMPT, buildTranslationPrompt(draft, normalizedLanguage)));
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

    private static String normalizeTargetLanguage(String targetLanguage) {
        String normalizedLanguage = targetLanguage == null ? "" : targetLanguage.trim().toLowerCase(Locale.ROOT);
        if (!"en".equals(normalizedLanguage)) {
            throw new IllegalArgumentException("targetLanguage only supports en.");
        }
        return normalizedLanguage;
    }
}
