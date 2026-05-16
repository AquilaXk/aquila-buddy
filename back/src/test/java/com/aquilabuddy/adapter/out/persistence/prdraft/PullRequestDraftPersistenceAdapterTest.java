package com.aquilabuddy.adapter.out.persistence.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftUpdateRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * PR 초안 persistence adapter가 생성 입력과 생성 결과를 함께 저장하고 재조회하는지 검증합니다.
 */
@DataJpaTest
@Import(PullRequestDraftPersistenceAdapter.class)
class PullRequestDraftPersistenceAdapterTest {

    @Autowired
    private PullRequestDraftPersistenceAdapter adapter;

    @Autowired
    private PullRequestDraftJpaRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void savesGeneratedDraftWithRequestContext() {
        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            123,
            "main",
            "feat/readme-fix",
            "diff --git a/README.md b/README.md"
        );
        PullRequestDraft generated = new PullRequestDraft(
            "[Docs] README 기여 안내 보완",
            "close #123\n\n## 요약\n- README 안내를 보완합니다."
        );

        StoredPullRequestDraft stored = adapter.savePullRequestDraft(request, generated);

        assertThat(stored.id()).isNotBlank();
        assertThat(stored.upstreamRepo()).isEqualTo("spring-projects/spring-boot");
        assertThat(stored.githubIssueNumber()).isEqualTo(123);
        assertThat(stored.baseBranch()).isEqualTo("main");
        assertThat(stored.headBranch()).isEqualTo("feat/readme-fix");
        assertThat(stored.title()).isEqualTo("[Docs] README 기여 안내 보완");
        assertThat(stored.body()).contains("close #123");
        assertThat(stored.createdAt()).isNotNull();

        assertThat(adapter.loadPullRequestDraft(stored.id()))
            .get()
            .usingRecursiveComparison()
            .isEqualTo(stored);
    }

    @Test
    void loadsRecentDraftsNewestFirst() {
        repository.save(new PullRequestDraftJpaEntity(
            "older",
            "spring-projects/spring-boot",
            10,
            "main",
            "feat/older",
            "[Docs] 오래된 초안",
            "close #10",
            Instant.parse("2026-05-15T01:00:00Z")
        ));
        repository.save(new PullRequestDraftJpaEntity(
            "newer",
            "vercel/next.js",
            11,
            "main",
            "feat/newer",
            "[Fix] 최신 초안",
            "close #11",
            Instant.parse("2026-05-15T02:00:00Z")
        ));

        List<StoredPullRequestDraft> drafts = adapter.loadRecentPullRequestDrafts();

        assertThat(drafts)
            .extracting(StoredPullRequestDraft::id)
            .containsExactly("newer", "older");
        assertThat(drafts.getFirst().title()).isEqualTo("[Fix] 최신 초안");
    }

    @Test
    void updatesDraftTitleAndBody() {
        repository.save(new PullRequestDraftJpaEntity(
            "draft-1",
            "spring-projects/spring-boot",
            10,
            "main",
            "feat/update",
            "[Docs] 기존 초안",
            "old body",
            Instant.parse("2026-05-15T01:00:00Z")
        ));

        StoredPullRequestDraft updated = adapter.updatePullRequestDraft(
            "draft-1",
            new PullRequestDraftUpdateRequest("[Docs] 수정된 초안", "updated body")
        ).orElseThrow();

        assertThat(updated.title()).isEqualTo("[Docs] 수정된 초안");
        assertThat(updated.body()).isEqualTo("updated body");
        assertThat(updated.createdAt()).isEqualTo(Instant.parse("2026-05-15T01:00:00Z"));
        assertThat(adapter.loadPullRequestDraft("draft-1"))
            .get()
            .extracting(StoredPullRequestDraft::title)
            .isEqualTo("[Docs] 수정된 초안");
    }

    @Test
    void returnsEmptyWhenUpdatingMissingDraft() {
        assertThat(adapter.updatePullRequestDraft(
            "missing",
            new PullRequestDraftUpdateRequest("[Docs] 수정된 초안", "updated body")
        )).isEmpty();
    }
}
