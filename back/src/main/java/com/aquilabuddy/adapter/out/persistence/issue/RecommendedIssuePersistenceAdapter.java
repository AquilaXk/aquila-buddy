package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecommendedIssuePersistenceAdapter implements LoadRecommendedIssuesPort {

    private final RecommendedIssueJpaRepository repository;

    public RecommendedIssuePersistenceAdapter(RecommendedIssueJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<RecommendedIssue> loadRecommendedIssues() {
        return repository.findAllByOrderByScoreDescIssueNumberAsc()
            .stream()
            .map(RecommendedIssuePersistenceMapper::toDomain)
            .toList();
    }
}
