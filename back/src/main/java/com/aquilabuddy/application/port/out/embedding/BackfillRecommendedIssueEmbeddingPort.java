package com.aquilabuddy.application.port.out.embedding;

import com.aquilabuddy.domain.embedding.EmbeddingBackfillResult;

/**
 * 기존 추천 이슈 중 embedding이 비어 있는 데이터를 제한 수량으로 보강합니다.
 */
public interface BackfillRecommendedIssueEmbeddingPort {

    EmbeddingBackfillResult backfillMissingIssueEmbeddings(int limit);
}
