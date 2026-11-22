package com.aquilabuddy.adapter.out.persistence.issue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * `user_recommended_issues` Spring Data JPA repository입니다.
 */
public interface UserRecommendedIssueJpaRepository
    extends JpaRepository<UserRecommendedIssueJpaEntity, UserRecommendedIssueId> {

    List<UserRecommendedIssueJpaEntity> findAllByUserIdAndIssueIdIn(long userId, Collection<String> issueIds);

    List<UserRecommendedIssueJpaEntity> findAllByUserIdAndBookmarkedTrueOrderByBookmarkedAtDesc(long userId);

    Optional<UserRecommendedIssueJpaEntity> findByUserIdAndIssueId(long userId, String issueId);
}
