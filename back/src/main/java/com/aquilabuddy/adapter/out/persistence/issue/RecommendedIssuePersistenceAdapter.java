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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * [Outgoing Adapter] 추천 이슈 조회 outbound port의 JPA 구현체입니다.
 *
 * <p>임베딩 생성을 위한 외부 HTTP 호출은 DB 트랜잭션 밖에서 수행하고, 쓰기만 짧은 배치 트랜잭션으로 커밋합니다.
 */
@Component
public class RecommendedIssuePersistenceAdapter implements
    LoadRecommendedIssuesPort,
    LoadCollectedIssuesPort,
    FindRecommendedIssuePort,
    SaveRecommendedIssuesPort,
    BackfillRecommendedIssueEmbeddingPort {

    private static final int MAX_FAILURE_REASON_SAMPLES = 10;

    private final RecommendedIssueJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;
    private final int expectedEmbeddingDimensions;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private volatile Boolean pgvectorIssueEmbeddingColumn;

    public RecommendedIssuePersistenceAdapter(
        RecommendedIssueJpaRepository repository,
        JdbcTemplate jdbcTemplate,
        ObjectProvider<EmbeddingClient> embeddingClientProvider,
        @Value("${app.embedding.expected-dimensions:1536}") int expectedEmbeddingDimensions
    ) {
        this(repository, jdbcTemplate, embeddingClientProvider, expectedEmbeddingDimensions, null);
    }

    @Autowired
    public RecommendedIssuePersistenceAdapter(
        RecommendedIssueJpaRepository repository,
        JdbcTemplate jdbcTemplate,
        ObjectProvider<EmbeddingClient> embeddingClientProvider,
        @Value("${app.embedding.expected-dimensions:1536}") int expectedEmbeddingDimensions,
        @Autowired(required = false) PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClientProvider = embeddingClientProvider;
        this.expectedEmbeddingDimensions = expectedEmbeddingDimensions;
        if (transactionManager == null) {
            this.writeTransactionTemplate = null;
            this.readTransactionTemplate = null;
        } else {
            this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
            TransactionTemplate readTx = new TransactionTemplate(transactionManager);
            readTx.setReadOnly(true);
            this.readTransactionTemplate = readTx;
        }
    }

    /**
     * 저장된 추천 이슈를 조회한 뒤 도메인 모델로 변환합니다.
     */
    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Optional<RecommendedIssue> findById(String id) {
        return repository.findById(id)
            .map(RecommendedIssuePersistenceMapper::toDomain);
    }

    /**
     * GitHub 이슈 식별자로 저장된 추천 이슈를 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<RecommendedIssue> findByRepoFullNameAndIssueNumber(String repoFullName, long issueNumber) {
        return repository.findByRepoFullNameAndIssueNumber(repoFullName, issueNumber)
            .map(RecommendedIssuePersistenceMapper::toDomain);
    }

    /**
     * 검색 후보의 임베딩을 트랜잭션 밖에서 계산한 뒤 엔티티와 벡터를 배치로 저장합니다.
     */
    @Override
    public void saveRecommendedIssues(List<RecommendedIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        List<RecommendedIssueJpaEntity> entities = new ArrayList<>(issues.size());
        List<IssueEmbeddingUpdate> embeddingUpdates = new ArrayList<>(issues.size());
        for (RecommendedIssue issue : issues) {
            entities.add(RecommendedIssuePersistenceMapper.toEntity(issue));
            String literal = createIssueEmbedding(issue);
            if (literal != null) {
                embeddingUpdates.add(new IssueEmbeddingUpdate(issue.id(), literal));
            }
        }

        executeInWriteTransaction(() -> {
            repository.saveAllAndFlush(entities);
            batchUpdateIssueEmbeddings(embeddingUpdates);
        });
    }

    @Override
    public EmbeddingBackfillResult backfillMissingIssueEmbeddings(int limit) {
        int requestedLimit = normalizeLimit(limit);
        IssueBackfillSnapshot snapshot = executeInReadTransaction(() -> new IssueBackfillSnapshot(
            repository.findByIssueEmbeddingIsNullOrderByScoreDescIssueNumberAsc(PageRequest.of(0, requestedLimit)),
            saturatedInt(repository.countByIssueEmbeddingIsNotNull())
        ));
        List<RecommendedIssueJpaEntity> candidates = snapshot.candidates();
        int skippedExisting = snapshot.skippedExisting();

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

        int skippedBlankText = 0;
        int skippedDimensionMismatch = 0;
        int failed = 0;
        List<String> failureReasons = new ArrayList<>();
        List<IssueEmbeddingUpdate> updates = new ArrayList<>(candidates.size());

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
                    addFailureReason(
                        failureReasons,
                        entity.getId() + ": expected " + expectedEmbeddingDimensions
                            + " dimensions but was " + vector.dimension()
                    );
                    continue;
                }
                updates.add(new IssueEmbeddingUpdate(entity.getId(), vector.toPgvectorLiteral()));
            } catch (RuntimeException exception) {
                failed++;
                addFailureReason(failureReasons, entity.getId() + ": " + exception.getClass().getSimpleName());
            }
        }

        if (!updates.isEmpty()) {
            executeInWriteTransaction(() -> batchUpdateIssueEmbeddings(updates));
        }

        return new EmbeddingBackfillResult(
            "issues",
            requestedLimit,
            candidates.size(),
            updates.size(),
            skippedExisting,
            skippedBlankText,
            0,
            skippedDimensionMismatch,
            failed,
            failureReasons
        );
    }

    private <T> T executeInReadTransaction(Supplier<T> action) {
        if (readTransactionTemplate == null) {
            return action.get();
        }
        return readTransactionTemplate.execute(status -> action.get());
    }

    private void executeInWriteTransaction(Runnable action) {
        if (writeTransactionTemplate == null) {
            action.run();
            return;
        }
        writeTransactionTemplate.executeWithoutResult(status -> action.run());
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

    private void batchUpdateIssueEmbeddings(List<IssueEmbeddingUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        String sql = issueEmbeddingColumnUsesPgvector()
            ? "UPDATE recommended_issues SET issue_embedding = CAST(? AS vector) WHERE issue_id = ?"
            : "UPDATE recommended_issues SET issue_embedding = ? WHERE issue_id = ?";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                IssueEmbeddingUpdate update = updates.get(index);
                ps.setString(1, update.issueEmbedding());
                ps.setString(2, update.issueId());
            }

            @Override
            public int getBatchSize() {
                return updates.size();
            }
        });
    }

    private boolean issueEmbeddingColumnUsesPgvector() {
        if (pgvectorIssueEmbeddingColumn != null) {
            return pgvectorIssueEmbeddingColumn;
        }

        try {
            Boolean result = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
                FROM information_schema.columns
                WHERE table_schema = 'public'
                    AND table_name = 'recommended_issues'
                    AND column_name = 'issue_embedding'
                    AND udt_name = 'vector'
                """,
                Boolean.class
            );
            pgvectorIssueEmbeddingColumn = Boolean.TRUE.equals(result);
        } catch (DataAccessException exception) {
            pgvectorIssueEmbeddingColumn = false;
        }
        return pgvectorIssueEmbeddingColumn;
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

    private static void addFailureReason(List<String> failureReasons, String reason) {
        if (failureReasons.size() < MAX_FAILURE_REASON_SAMPLES) {
            failureReasons.add(reason);
        }
    }

    private record IssueEmbeddingUpdate(String issueId, String issueEmbedding) {
    }

    private record IssueBackfillSnapshot(
        List<RecommendedIssueJpaEntity> candidates,
        int skippedExisting
    ) {
    }
}
