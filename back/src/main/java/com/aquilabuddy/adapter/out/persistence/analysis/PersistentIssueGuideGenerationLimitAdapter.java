package com.aquilabuddy.adapter.out.persistence.analysis;

import com.aquilabuddy.application.port.out.analysis.IssueGuideGenerationLimitPort;
import com.aquilabuddy.application.port.out.user.CurrentUserGithubIdPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자별 이슈 guide 생성 제한을 DB 요청 이력 기준으로 적용합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.analysis.issue-guide", name = "limit-store", havingValue = "database", matchIfMissing = true)
public class PersistentIssueGuideGenerationLimitAdapter implements IssueGuideGenerationLimitPort {

    private static final List<IssueGuideGenerationStatus> QUOTA_COUNTED_STATUSES = List.of(
        IssueGuideGenerationStatus.ACQUIRED,
        IssueGuideGenerationStatus.SUCCEEDED,
        IssueGuideGenerationStatus.FAILED
    );

    private final IssueGuideGenerationRequestJpaRepository repository;
    private final CurrentUserGithubIdPort currentUserGithubIdPort;
    private final Clock clock;
    private final int dailyLimit;

    @Autowired
    public PersistentIssueGuideGenerationLimitAdapter(
        IssueGuideGenerationRequestJpaRepository repository,
        CurrentUserGithubIdPort currentUserGithubIdPort,
        @Value("${app.analysis.issue-guide.daily-limit:20}") int dailyLimit
    ) {
        this(repository, currentUserGithubIdPort, Clock.systemUTC(), dailyLimit);
    }

    PersistentIssueGuideGenerationLimitAdapter(
        IssueGuideGenerationRequestJpaRepository repository,
        CurrentUserGithubIdPort currentUserGithubIdPort,
        Clock clock,
        int dailyLimit
    ) {
        this.repository = repository;
        this.currentUserGithubIdPort = currentUserGithubIdPort;
        this.clock = clock;
        this.dailyLimit = Math.max(0, dailyLimit);
    }

    @Override
    @Transactional
    public void acquireGenerationSlot(String issueId) {
        long userGithubId = currentUserGithubIdPort.currentGithubId();
        Instant requestedAt = clock.instant();
        LocalDate requestedOn = requestedAt.atZone(ZoneOffset.UTC).toLocalDate();
        long usedToday = repository.countByUserGithubIdAndRequestedOnAndStatusIn(
            userGithubId,
            requestedOn,
            QUOTA_COUNTED_STATUSES
        );
        if (usedToday >= dailyLimit) {
            throw new IllegalStateException("Issue guide generation daily limit exceeded: " + userGithubId);
        }

        repository.save(IssueGuideGenerationRequestJpaEntity.acquired(
            userGithubId,
            issueId,
            requestedOn,
            requestedAt
        ));
    }

    @Override
    @Transactional
    public void recordGenerationSucceeded(String issueId) {
        loadLatestAcquiredRequest(issueId).ifPresent(IssueGuideGenerationRequestJpaEntity::markSucceeded);
    }

    @Override
    @Transactional
    public void recordGenerationFailed(String issueId, String reason) {
        loadLatestAcquiredRequest(issueId).ifPresent(request -> request.markFailed(reason));
    }

    private Optional<IssueGuideGenerationRequestJpaEntity> loadLatestAcquiredRequest(String issueId) {
        return repository.findFirstByUserGithubIdAndIssueIdAndStatusOrderByRequestedAtDescRequestIdDesc(
            currentUserGithubIdPort.currentGithubId(),
            issueId,
            IssueGuideGenerationStatus.ACQUIRED
        );
    }
}
