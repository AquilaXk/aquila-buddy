package com.aquilabuddy.application.port.in.embedding;

import com.aquilabuddy.domain.embedding.EmbeddingBackfillSummary;

/**
 * 운영자가 기존 사용자/이슈 embedding 누락분을 제한 수량으로 보강하는 use case입니다.
 */
public interface BackfillEmbeddingsUseCase {

    EmbeddingBackfillSummary backfillEmbeddings(int limit);
}
