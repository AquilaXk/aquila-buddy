package com.aquilabuddy.adapter.out.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.application.port.out.user.CurrentUserGithubIdPort;
import com.aquilabuddy.domain.analysis.IssueGuideGenerationDailyLimitExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * 이슈 guide 생성 제한이 사용자별 일일 quota와 요청 이력을 DB에 남기는지 검증합니다.
 */
@DataJpaTest
class PersistentIssueGuideGenerationLimitAdapterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-18T01:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private IssueGuideGenerationRequestJpaRepository repository;

    private MutableCurrentUserGithubIdPort currentUserGithubIdPort;
    private PersistentIssueGuideGenerationLimitAdapter adapter;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        currentUserGithubIdPort = new MutableCurrentUserGithubIdPort(10001L);
        adapter = new PersistentIssueGuideGenerationLimitAdapter(repository, currentUserGithubIdPort, FIXED_CLOCK, 2);
    }

    @Test
    void defaultDailyLimitIsFiveRequestsPerUserAndDate() {
        PersistentIssueGuideGenerationLimitAdapter defaultLimitAdapter =
            new PersistentIssueGuideGenerationLimitAdapter(
                repository,
                currentUserGithubIdPort,
                FIXED_CLOCK,
                PersistentIssueGuideGenerationLimitAdapter.DEFAULT_DAILY_LIMIT
            );

        defaultLimitAdapter.acquireGenerationSlot("issue-1");
        defaultLimitAdapter.acquireGenerationSlot("issue-2");
        defaultLimitAdapter.acquireGenerationSlot("issue-3");
        defaultLimitAdapter.acquireGenerationSlot("issue-4");
        defaultLimitAdapter.acquireGenerationSlot("issue-5");

        assertThatThrownBy(() -> defaultLimitAdapter.acquireGenerationSlot("issue-6"))
            .isInstanceOf(IssueGuideGenerationDailyLimitExceededException.class)
            .hasMessage("이슈 분석 일일 생성 제한을 초과했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void persistsDailyUsageAndRejectsOverQuotaForCurrentUser() {
        adapter.acquireGenerationSlot("issue-1");
        adapter.acquireGenerationSlot("issue-2");

        assertThatThrownBy(() -> adapter.acquireGenerationSlot("issue-3"))
            .isInstanceOf(IssueGuideGenerationDailyLimitExceededException.class)
            .hasMessage("이슈 분석 일일 생성 제한을 초과했습니다. 잠시 후 다시 시도해 주세요.");

        assertThat(repository.findAllByOrderByRequestIdAsc())
            .extracting(
                IssueGuideGenerationRequestJpaEntity::getUserGithubId,
                IssueGuideGenerationRequestJpaEntity::getIssueId,
                IssueGuideGenerationRequestJpaEntity::getRequestedOn,
                IssueGuideGenerationRequestJpaEntity::getStatus
            )
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(10001L, "issue-1", FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDate(), IssueGuideGenerationStatus.ACQUIRED),
                org.assertj.core.groups.Tuple.tuple(10001L, "issue-2", FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDate(), IssueGuideGenerationStatus.ACQUIRED)
            );
    }

    @Test
    void recordsCacheHitWithoutUsingDailyGenerationQuota() {
        adapter.recordCacheHit("cached-issue");
        adapter.acquireGenerationSlot("issue-1");
        adapter.acquireGenerationSlot("issue-2");

        assertThatThrownBy(() -> adapter.acquireGenerationSlot("issue-3"))
            .isInstanceOf(IssueGuideGenerationDailyLimitExceededException.class)
            .hasMessage("이슈 분석 일일 생성 제한을 초과했습니다. 잠시 후 다시 시도해 주세요.");

        assertThat(repository.findAllByOrderByRequestIdAsc())
            .extracting(
                IssueGuideGenerationRequestJpaEntity::getIssueId,
                IssueGuideGenerationRequestJpaEntity::getStatus
            )
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("cached-issue", IssueGuideGenerationStatus.CACHE_HIT),
                org.assertj.core.groups.Tuple.tuple("issue-1", IssueGuideGenerationStatus.ACQUIRED),
                org.assertj.core.groups.Tuple.tuple("issue-2", IssueGuideGenerationStatus.ACQUIRED)
            );
    }

    @Test
    void quotaIsSeparatedByUserAndRequestDate() {
        adapter.acquireGenerationSlot("issue-1");
        adapter.acquireGenerationSlot("issue-2");

        currentUserGithubIdPort.githubId = 20002L;

        adapter.acquireGenerationSlot("issue-3");

        PersistentIssueGuideGenerationLimitAdapter nextDayAdapter =
            new PersistentIssueGuideGenerationLimitAdapter(
                repository,
                currentUserGithubIdPort,
                Clock.fixed(Instant.parse("2026-05-19T00:30:00Z"), ZoneOffset.UTC),
                2
            );

        nextDayAdapter.acquireGenerationSlot("issue-4");

        assertThat(repository.findAllByOrderByRequestIdAsc()).hasSize(4);
    }

    @Test
    void recordsGenerationResultOnLatestAcquiredRequest() {
        adapter.acquireGenerationSlot("issue-1");
        adapter.acquireGenerationSlot("issue-2");

        adapter.recordGenerationSucceeded("issue-1");
        adapter.recordGenerationFailed("issue-2", "AI generation failed");

        assertThat(repository.findAllByOrderByRequestIdAsc())
            .extracting(
                IssueGuideGenerationRequestJpaEntity::getIssueId,
                IssueGuideGenerationRequestJpaEntity::getStatus,
                IssueGuideGenerationRequestJpaEntity::getFailureReason
            )
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("issue-1", IssueGuideGenerationStatus.SUCCEEDED, null),
                org.assertj.core.groups.Tuple.tuple("issue-2", IssueGuideGenerationStatus.FAILED, "AI generation failed")
            );
    }

    private static class MutableCurrentUserGithubIdPort implements CurrentUserGithubIdPort {

        private long githubId;

        private MutableCurrentUserGithubIdPort(long githubId) {
            this.githubId = githubId;
        }

        @Override
        public long currentGithubId() {
            return githubId;
        }
    }
}
