package com.aquilabuddy.adapter.out.persistence.analysis;

import com.aquilabuddy.application.port.out.analysis.LoadAnalyzedIssueIdsPort;
import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.SaveIssueGuidePort;
import com.aquilabuddy.domain.analysis.CachedIssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuide;
import java.util.Collection;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Outgoing Adapter] 이슈 가이드 조회/저장 outbound port의 JPA 구현체입니다.
 */
@Component
public class IssueGuidePersistenceAdapter implements
    LoadIssueGuidePort,
    SaveIssueGuidePort,
    LoadAnalyzedIssueIdsPort {

    private final IssueGuideJpaRepository repository;

    public IssueGuidePersistenceAdapter(IssueGuideJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public IssueGuide loadIssueGuide(String issueId) {
        return repository.findByIssueId(issueId)
            .map(IssueGuidePersistenceMapper::toDomain)
            .orElseThrow(() -> new NoSuchElementException("Issue guide not found: " + issueId));
    }

    @Override
    @Transactional(readOnly = true)
    public CachedIssueGuide loadCachedIssueGuide(String issueId) {
        return repository.findByIssueId(issueId)
            .map(IssueGuidePersistenceMapper::toCachedDomain)
            .orElseThrow(() -> new NoSuchElementException("Issue guide not found: " + issueId));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findAnalyzedIssueIds(Collection<String> issueIds) {
        if (issueIds == null || issueIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(new HashSet<>(repository.findAnalyzedIssueIdsByIssueIdIn(issueIds)));
    }

    @Override
    @Transactional
    public void saveIssueGuide(IssueGuide guide) {
        repository.save(IssueGuidePersistenceMapper.toEntity(guide));
    }
}
