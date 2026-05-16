package com.aquilabuddy.adapter.out.persistence.prdraft;

import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.UpdatePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftUpdateRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 생성된 PR 초안을 JPA 저장소에 보관하고 조회합니다.
 */
@Component
public class PullRequestDraftPersistenceAdapter implements
    SavePullRequestDraftPort,
    LoadPullRequestDraftPort,
    UpdatePullRequestDraftPort {

    private final PullRequestDraftJpaRepository repository;

    public PullRequestDraftPersistenceAdapter(PullRequestDraftJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public StoredPullRequestDraft savePullRequestDraft(PullRequestDraftRequest request, PullRequestDraft draft) {
        PullRequestDraftJpaEntity entity = PullRequestDraftPersistenceMapper.toEntity(
            UUID.randomUUID().toString(),
            request,
            draft,
            Instant.now()
        );

        return PullRequestDraftPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public List<StoredPullRequestDraft> loadRecentPullRequestDrafts() {
        return repository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(PullRequestDraftPersistenceMapper::toDomain)
            .toList();
    }

    @Override
    public Optional<StoredPullRequestDraft> loadPullRequestDraft(String id) {
        return repository.findById(id)
            .map(PullRequestDraftPersistenceMapper::toDomain);
    }

    @Override
    public Optional<StoredPullRequestDraft> updatePullRequestDraft(
        String id,
        PullRequestDraftUpdateRequest request
    ) {
        return repository.findById(id)
            .map(entity -> {
                entity.updateDraft(request.title(), request.body());
                return PullRequestDraftPersistenceMapper.toDomain(repository.save(entity));
            });
    }
}
