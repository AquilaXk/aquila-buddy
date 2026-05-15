package com.aquilabuddy.adapter.out.persistence.analysis;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 이슈 가이드 persistence adapter가 사용하는 Spring Data repository입니다.
 */
public interface IssueGuideJpaRepository extends JpaRepository<IssueGuideJpaEntity, String> {

    Optional<IssueGuideJpaEntity> findByIssueId(String issueId);
}
