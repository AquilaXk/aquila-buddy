package com.aquilabuddy.adapter.out.persistence.prdraft;

import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftTranslationPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 생성된 PR 초안 번역 결과를 JPA 저장소에 보관하고 조회합니다.
 */
@Component
public class PullRequestDraftTranslationPersistenceAdapter implements
    SavePullRequestDraftTranslationPort,
    LoadPullRequestDraftTranslationPort {

    private final PullRequestDraftTranslationJpaRepository repository;

    public PullRequestDraftTranslationPersistenceAdapter(PullRequestDraftTranslationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PullRequestDraftTranslation savePullRequestDraftTranslation(
        String draftId,
        String targetLanguage,
        PullRequestDraft translatedDraft
    ) {
        PullRequestDraftTranslationJpaEntity entity = PullRequestDraftTranslationPersistenceMapper.toEntity(
            UUID.randomUUID().toString(),
            draftId,
            targetLanguage,
            translatedDraft,
            Instant.now()
        );

        return PullRequestDraftTranslationPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public List<PullRequestDraftTranslation> loadPullRequestDraftTranslations(String draftId) {
        return repository.findAllByDraftIdOrderByCreatedAtDesc(draftId)
            .stream()
            .map(PullRequestDraftTranslationPersistenceMapper::toDomain)
            .toList();
    }

    @Override
    public Optional<PullRequestDraftTranslation> loadPullRequestDraftTranslation(String id) {
        return repository.findById(id)
            .map(PullRequestDraftTranslationPersistenceMapper::toDomain);
    }
}
