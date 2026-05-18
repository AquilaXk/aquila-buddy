package com.aquilabuddy.adapter.in.web.embedding;

import com.aquilabuddy.application.port.in.embedding.BackfillEmbeddingsUseCase;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillSummary;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Incoming Web Adapter] 운영용 embedding backfill API 요청을 처리합니다.
 */
@RestController
@RequestMapping("/api/v1/embeddings")
public class EmbeddingBackfillController {

    private final BackfillEmbeddingsUseCase backfillEmbeddingsUseCase;

    public EmbeddingBackfillController(BackfillEmbeddingsUseCase backfillEmbeddingsUseCase) {
        this.backfillEmbeddingsUseCase = backfillEmbeddingsUseCase;
    }

    /**
     * profile_embedding과 issue_embedding 누락분을 같은 limit으로 보강합니다.
     */
    @PostMapping("/backfill")
    ApiResponse<EmbeddingBackfillSummary> backfill(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(backfillEmbeddingsUseCase.backfillEmbeddings(limit));
    }
}
