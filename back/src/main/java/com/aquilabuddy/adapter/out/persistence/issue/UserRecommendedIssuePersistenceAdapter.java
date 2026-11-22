package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.adapter.out.persistence.PostgresRlsSessionBinder;
import com.aquilabuddy.application.port.out.issue.UserRecommendedIssuePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.UserIssueState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Outgoing Adapter] 사용자별 추천 이슈 북마크 및 분석 상태를 RLS 세션과 함께 저장·조회합니다.
 */
@Component
public class UserRecommendedIssuePersistenceAdapter implements UserRecommendedIssuePort {

    private final UserRecommendedIssueJpaRepository userIssueRepository;
    private final RecommendedIssueJpaRepository recommendedIssueRepository;
    private final PostgresRlsSessionBinder rlsSessionBinder;

    public UserRecommendedIssuePersistenceAdapter(
        UserRecommendedIssueJpaRepository userIssueRepository,
        RecommendedIssueJpaRepository recommendedIssueRepository,
        PostgresRlsSessionBinder rlsSessionBinder
    ) {
        this.userIssueRepository = Objects.requireNonNull(userIssueRepository);
        this.recommendedIssueRepository = Objects.requireNonNull(recommendedIssueRepository);
        this.rlsSessionBinder = Objects.requireNonNull(rlsSessionBinder);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, UserIssueState> findStatesByUserAndIssueIds(long userId, Collection<String> issueIds) {
        if (userId <= 0 || issueIds == null || issueIds.isEmpty()) {
            return Map.of();
        }
        rlsSessionBinder.bindCurrentUser(userId);
        List<UserRecommendedIssueJpaEntity> rows = userIssueRepository.findAllByUserIdAndIssueIdIn(userId, issueIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, UserIssueState> states = LinkedHashMap.newLinkedHashMap(rows.size());
        for (UserRecommendedIssueJpaEntity row : rows) {
            states.put(row.getIssueId(), new UserIssueState(
                row.getIssueId(),
                row.isBookmarked(),
                row.getBookmarkedAt(),
                row.isAnalyzed(),
                row.getAnalyzedAt()
            ));
        }
        return Map.copyOf(states);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecommendedIssue> loadBookmarkedIssues(long userId) {
        if (userId <= 0) {
            return List.of();
        }
        rlsSessionBinder.bindCurrentUser(userId);
        List<UserRecommendedIssueJpaEntity> bookmarkedRows =
            userIssueRepository.findAllByUserIdAndBookmarkedTrueOrderByBookmarkedAtDesc(userId);
        if (bookmarkedRows.isEmpty()) {
            return List.of();
        }

        List<String> orderedIds = bookmarkedRows.stream()
            .map(UserRecommendedIssueJpaEntity::getIssueId)
            .toList();
        Map<String, RecommendedIssue> issueById = LinkedHashMap.newLinkedHashMap(orderedIds.size());
        for (RecommendedIssueJpaEntity entity : recommendedIssueRepository.findAllById(orderedIds)) {
            issueById.put(entity.getId(), RecommendedIssuePersistenceMapper.toDomain(entity).withBookmarked(true));
        }

        List<RecommendedIssue> result = new ArrayList<>(orderedIds.size());
        for (String issueId : orderedIds) {
            RecommendedIssue issue = issueById.get(issueId);
            if (issue != null) {
                result.add(issue);
            }
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional
    public void setBookmarked(long userId, String issueId, boolean bookmarked, Instant timestamp) {
        rlsSessionBinder.bindCurrentUser(userId);
        Instant effectiveTime = timestamp == null ? Instant.now() : timestamp;
        UserRecommendedIssueJpaEntity entity = userIssueRepository.findByUserIdAndIssueId(userId, issueId).orElse(null);
        if (entity == null) {
            if (!bookmarked) {
                return;
            }
            entity = new UserRecommendedIssueJpaEntity(userId, issueId, effectiveTime);
        }
        if (bookmarked) {
            entity.markBookmarked(effectiveTime);
        } else {
            entity.clearBookmark(effectiveTime);
        }
        userIssueRepository.save(entity);
    }

    @Override
    @Transactional
    public void markAnalyzed(long userId, String issueId, Instant timestamp) {
        if (userId <= 0 || issueId == null || issueId.isBlank() || !recommendedIssueRepository.existsById(issueId)) {
            return;
        }
        rlsSessionBinder.bindCurrentUser(userId);
        Instant effectiveTime = timestamp == null ? Instant.now() : timestamp;
        UserRecommendedIssueJpaEntity entity = userIssueRepository.findByUserIdAndIssueId(userId, issueId)
            .orElseGet(() -> new UserRecommendedIssueJpaEntity(userId, issueId, effectiveTime));
        entity.markAnalyzed(effectiveTime);
        userIssueRepository.save(entity);
    }
}
