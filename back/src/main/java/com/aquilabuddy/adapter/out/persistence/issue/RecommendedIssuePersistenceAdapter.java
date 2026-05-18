package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.application.port.out.embedding.BackfillRecommendedIssueEmbeddingPort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.application.port.out.issue.LoadCollectedIssuesPort;
import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SaveRecommendedIssuesPort;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillResult;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 추천 이슈 조회 outbound port의 JPA 구현체입니다.
 *
 * <p>application service는 {@link LoadRecommendedIssuesPort}만 의존하고, 정렬/조회 세부사항은
 * 이 adapter와 repository 내부에 둡니다.
 */
@Component
public class RecommendedIssuePersistenceAdapter implements
    LoadRecommendedIssuesPort,
    LoadCollectedIssuesPort,
    FindRecommendedIssuePort,
    SaveRecommendedIssuesPort,
    BackfillRecommendedIssueEmbeddingPort {

    private final RecommendedIssueJpaRepository repository;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;
    private final int expectedEmbeddingDimensions;

    public RecommendedIssuePersistenceAdapter(
        RecommendedIssueJpaRepository repository,
        ObjectProvider<EmbeddingClient> embeddingClientProvider,
        @Value("${app.embedding.expected-dimensions:1536}") int expectedEmbeddingDimensions
    ) {
        this.repository = repository;
        this.embeddingClientProvider = embeddingClientProvider;
        this.expectedEmbeddingDimensions = expectedEmbeddingDimensions;
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

    /**
     * 저장된 전체 수집 이슈 catalog를 조회합니다.
     */
    @Override
    public List<RecommendedIssue> loadCollectedIssues() {
        return repository.findAllByOrderByScoreDescIssueNumberAsc()
            .stream()
            .map(RecommendedIssuePersistenceMapper::toDomain)
            .toList();
    }

    /**
     * 내부 이슈 ID로 저장된 추천 이슈를 조회합니다.
     */
    @Override
    public Optional<RecommendedIssue> findById(String id) {
        return repository.findById(id)
            .map(RecommendedIssuePersistenceMapper::toDomain);
    }

    /**
     * GitHub 이슈 식별자로 저장된 추천 이슈를 조회합니다.
     */
    @Override
    public Optional<RecommendedIssue> findByRepoFullNameAndIssueNumber(String repoFullName, long issueNumber) {
        return repository.findByRepoFullNameAndIssueNumber(repoFullName, issueNumber)
            .map(RecommendedIssuePersistenceMapper::toDomain);
    }

    /**
     * 검색 후보를 저장소 모델로 변환해 이후 fallback 조회에 사용할 수 있게 저장합니다.
     */
    @Override
    public void saveRecommendedIssues(List<RecommendedIssue> issues) {
        List<RecommendedIssueJpaEntity> entities = issues.stream()
            .map(issue -> {
                RecommendedIssueJpaEntity entity = RecommendedIssuePersistenceMapper.toEntity(issue);
                entity.updateIssueEmbedding(createIssueEmbedding(issue));
                return entity;
            })
            .toList();

        repository.saveAll(entities);
    }

    @Override
    public EmbeddingBackfillResult backfillMissingIssueEmbeddings(int limit) {
        int requestedLimit = normalizeLimit(limit);
        List<RecommendedIssueJpaEntity> candidates = repository.findByIssueEmbeddingIsNullOrderByScoreDescIssueNumberAsc(
            PageRequest.of(0, requestedLimit)
        );
        int skippedExisting = saturatedInt(repository.countByIssueEmbeddingIsNotNull());
        EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            return new EmbeddingBackfillResult(
                "issues",
                requestedLimit,
                candidates.size(),
                0,
                skippedExisting,
                0,
                candidates.size(),
                0,
                0
            );
        }

        int updated = 0;
        int skippedBlankText = 0;
        int skippedDimensionMismatch = 0;
        int failed = 0;
        for (RecommendedIssueJpaEntity entity : candidates) {
            String input = buildIssueEmbeddingInput(entity);
            if (input.isBlank()) {
                skippedBlankText++;
                continue;
            }
            try {
                EmbeddingVector vector = embeddingClient.embed(input);
                if (vector.dimension() != expectedEmbeddingDimensions) {
                    skippedDimensionMismatch++;
                    continue;
                }
                entity.updateIssueEmbedding(vector.toPgvectorLiteral());
                updated++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        repository.saveAll(candidates);

        return new EmbeddingBackfillResult(
            "issues",
            requestedLimit,
            candidates.size(),
            updated,
            skippedExisting,
            skippedBlankText,
            0,
            skippedDimensionMismatch,
            failed
        );
    }

    private String createIssueEmbedding(RecommendedIssue issue) {
        EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            return null;
        }

        try {
            EmbeddingVector vector = embeddingClient.embed(buildIssueEmbeddingInput(issue));
            if (vector.dimension() != expectedEmbeddingDimensions) {
                return null;
            }
            return vector.toPgvectorLiteral();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String buildIssueEmbeddingInput(RecommendedIssue issue) {
        return String.join("\n",
            "Repository: " + issue.repoFullName(),
            "Issue number: #" + issue.issueNumber(),
            "Title: " + issue.title(),
            "Summary: " + issue.summary(),
            "Labels: " + String.join(", ", issue.labels()),
            "Status: " + issue.status(),
            "Reason: " + issue.reason()
        ).trim();
    }

    private static String buildIssueEmbeddingInput(RecommendedIssueJpaEntity issue) {
        return String.join("\n",
            "Repository: " + issue.getRepoFullName(),
            "Issue number: #" + issue.getIssueNumber(),
            "Title: " + issue.getTitle(),
            "Summary: " + issue.getSummary(),
            "Labels: " + String.join(", ", issue.getLabels()),
            "Status: " + issue.getStatus(),
            "Reason: " + issue.getReason()
        ).trim();
    }

    private static int normalizeLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 500);
    }

    private static int saturatedInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
