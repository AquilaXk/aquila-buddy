package com.aquilabuddy.adapter.out.persistence.analysis;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 이슈 guide 생성 요청 이력을 조회/저장하는 Spring Data repository입니다.
 */
public interface IssueGuideGenerationRequestJpaRepository
    extends JpaRepository<IssueGuideGenerationRequestJpaEntity, Long> {

    long countByUserGithubIdAndRequestedOnAndStatusIn(
        long userGithubId,
        LocalDate requestedOn,
        Collection<IssueGuideGenerationStatus> statuses
    );

    List<IssueGuideGenerationRequestJpaEntity> findAllByOrderByRequestIdAsc();
}
