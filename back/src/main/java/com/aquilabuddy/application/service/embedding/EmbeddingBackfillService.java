package com.aquilabuddy.application.service.embedding;

import com.aquilabuddy.application.port.in.embedding.BackfillEmbeddingsUseCase;
import com.aquilabuddy.application.port.out.embedding.BackfillRecommendedIssueEmbeddingPort;
import com.aquilabuddy.application.port.out.embedding.BackfillUserProfileEmbeddingPort;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillResult;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillSummary;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 기존 사용자/이슈 embedding 누락분을 같은 limit 기준으로 보강합니다.
 */
@Service
public class EmbeddingBackfillService implements BackfillEmbeddingsUseCase {

    private final BackfillUserProfileEmbeddingPort backfillUserProfileEmbeddingPort;
    private final BackfillRecommendedIssueEmbeddingPort backfillRecommendedIssueEmbeddingPort;

    public EmbeddingBackfillService(
        BackfillUserProfileEmbeddingPort backfillUserProfileEmbeddingPort,
        BackfillRecommendedIssueEmbeddingPort backfillRecommendedIssueEmbeddingPort
    ) {
        this.backfillUserProfileEmbeddingPort = backfillUserProfileEmbeddingPort;
        this.backfillRecommendedIssueEmbeddingPort = backfillRecommendedIssueEmbeddingPort;
    }

    @Override
    public EmbeddingBackfillSummary backfillEmbeddings(int limit) {
        EmbeddingBackfillResult profiles = backfillUserProfileEmbeddingPort.backfillMissingProfileEmbeddings(limit);
        EmbeddingBackfillResult issues = backfillRecommendedIssueEmbeddingPort.backfillMissingIssueEmbeddings(limit);
        return new EmbeddingBackfillSummary(profiles, issues);
    }
}
