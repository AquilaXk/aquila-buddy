package com.aquilabuddy.adapter.out.fixture.prdraft;

import com.aquilabuddy.application.port.out.prdraft.TranslatePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 실제 AI 번역 연동 전까지 사용할 규칙 기반 PR 초안 영문 변환 adapter입니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fixture", matchIfMissing = true)
public class RuleBasedPullRequestDraftTranslationAdapter implements TranslatePullRequestDraftPort {

    /**
     * 저장 초안의 repo/issue/branch 맥락을 사용해 재현 가능한 영문 PR 초안을 생성합니다.
     */
    @Override
    public PullRequestDraft translate(StoredPullRequestDraft draft, String targetLanguage) {
        String normalizedLanguage = targetLanguage == null ? "" : targetLanguage.trim().toLowerCase(Locale.ROOT);
        if (!"en".equals(normalizedLanguage)) {
            throw new IllegalArgumentException("targetLanguage only supports en.");
        }

        String title = "docs: Prepare " + draft.upstreamRepo() + "#" + draft.githubIssueNumber() + " update";
        String body = String.join("\n",
            "## Summary",
            "- Prepare a pull request draft for " + draft.upstreamRepo() + " #" + draft.githubIssueNumber() + ".",
            "- Structure the change around the " + draft.baseBranch() + " -> " + draft.headBranch()
                + " branch flow.",
            "",
            "## Notes",
            "- Source draft title: " + draft.title(),
            "- Review the source draft body before submitting upstream.",
            "",
            "## Test Plan",
            "- Run the relevant tests after applying the change."
        );

        return new PullRequestDraft(title, body);
    }
}
