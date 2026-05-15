package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 추천 이슈 조회 outbound port의 JPA 구현체입니다.
 *
 * <p>application service는 {@link LoadRecommendedIssuesPort}만 의존하고, 정렬/조회 세부사항은
 * 이 adapter와 repository 내부에 둡니다.
 */
@Component
public class RecommendedIssuePersistenceAdapter implements LoadRecommendedIssuesPort {

    private final RecommendedIssueJpaRepository repository;

    public RecommendedIssuePersistenceAdapter(RecommendedIssueJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * 저장된 추천 이슈를 조회한 뒤 도메인 모델로 변환합니다.
     */
    @Override
    public List<RecommendedIssue> loadRecommendedIssues() {
        return repository.findAllByOrderByScoreDescIssueNumberAsc()
            .stream()
            .map(RecommendedIssuePersistenceMapper::toDomain)
            .toList();
    }
}
