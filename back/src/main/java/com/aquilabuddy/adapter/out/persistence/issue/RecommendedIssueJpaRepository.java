package com.aquilabuddy.adapter.out.persistence.issue;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendedIssueJpaRepository extends JpaRepository<RecommendedIssueJpaEntity, String> {

    List<RecommendedIssueJpaEntity> findAllByOrderByScoreDescIssueNumberAsc();
}
