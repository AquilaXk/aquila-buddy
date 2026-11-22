package com.aquilabuddy.adapter.out.persistence.analysis;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 이슈 가이드 persistence adapter가 사용하는 Spring Data repository입니다.
 */
public interface IssueGuideJpaRepository extends JpaRepository<IssueGuideJpaEntity, String> {

    Optional<IssueGuideJpaEntity> findByIssueId(String issueId);

    @Query("select g.issueId from IssueGuideJpaEntity g where g.issueId in :issueIds")
    List<String> findAnalyzedIssueIdsByIssueIdIn(@Param("issueIds") Collection<String> issueIds);
}
