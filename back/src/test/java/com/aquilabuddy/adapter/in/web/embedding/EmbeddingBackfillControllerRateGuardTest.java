package com.aquilabuddy.adapter.in.web.embedding;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.application.port.in.embedding.BackfillEmbeddingsUseCase;
import com.aquilabuddy.application.port.in.embedding.RecordEmbeddingBackfillAuditUseCase;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillAuditOutcome;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillResult;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmbeddingBackfillController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "app.embedding.backfill.operation-token=test-operation-token",
    "app.embedding.backfill.max-limit=100",
    "app.embedding.backfill.min-interval-seconds=60"
})
class EmbeddingBackfillControllerRateGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackfillEmbeddingsUseCase backfillEmbeddingsUseCase;

    @MockitoBean
    private RecordEmbeddingBackfillAuditUseCase recordEmbeddingBackfillAuditUseCase;

    @Test
    void rejectsBackfillWhenPreviousRunIsInsideRateGuardWindow() throws Exception {
        given(backfillEmbeddingsUseCase.backfillEmbeddings(25))
            .willReturn(new EmbeddingBackfillSummary(
                new EmbeddingBackfillResult("profiles", 25, 0, 0, 0, 0, 0, 0, 0),
                new EmbeddingBackfillResult("issues", 25, 0, 0, 0, 0, 0, 0, 0)
            ));

        mockMvc.perform(post("/api/v1/embeddings/backfill")
                .header("X-Aquila-Operation-Token", "test-operation-token")
                .header("X-Aquila-Operator", "release-manager")
                .param("limit", "25"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/embeddings/backfill")
                .header("X-Aquila-Operation-Token", "test-operation-token")
                .header("X-Aquila-Operator", "release-manager")
                .param("limit", "25"))
            .andExpect(status().isTooManyRequests());

        then(recordEmbeddingBackfillAuditUseCase).should().record(argThat(event ->
            event.outcome() == EmbeddingBackfillAuditOutcome.RATE_LIMITED
                && event.operator().equals("release-manager")
                && event.reason().equals("rate-limit")
                && event.requestedLimit() == 25
                && event.effectiveLimit() == 25
        ));
    }
}
