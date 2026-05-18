package com.aquilabuddy.adapter.out.persistence.analysis;

import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.SaveIssueGuidePort;
import com.aquilabuddy.domain.analysis.CachedIssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuide;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * 이슈 가이드 조회 outbound port의 JPA 구현체입니다.
 *
 * <p>application service는 이 adapter를 직접 알지 못하고 {@link LoadIssueGuidePort}만
 * 의존하므로, 이후 AI 생성 결과 저장소로 바뀌어도 use case 경계는 유지됩니다.
 */
@Component
public class IssueGuidePersistenceAdapter implements LoadIssueGuidePort, SaveIssueGuidePort {

    private final IssueGuideJpaRepository repository;

    public IssueGuidePersistenceAdapter(IssueGuideJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public IssueGuide loadIssueGuide(String issueId) {
        return repository.findByIssueId(issueId)
            .map(IssueGuidePersistenceMapper::toDomain)
            .orElseThrow(() -> new NoSuchElementException("Issue guide not found: " + issueId));
    }

    @Override
    public CachedIssueGuide loadCachedIssueGuide(String issueId) {
        return repository.findByIssueId(issueId)
            .map(IssueGuidePersistenceMapper::toCachedDomain)
            .orElseThrow(() -> new NoSuchElementException("Issue guide not found: " + issueId));
    }

    @Override
    public void saveIssueGuide(IssueGuide guide) {
        repository.save(IssueGuidePersistenceMapper.toEntity(guide));
    }
}
