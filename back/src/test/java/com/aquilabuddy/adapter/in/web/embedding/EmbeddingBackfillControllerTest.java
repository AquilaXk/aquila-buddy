package com.aquilabuddy.adapter.in.web.embedding;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.application.port.in.embedding.BackfillEmbeddingsUseCase;
import com.aquilabuddy.application.port.in.embedding.RecordEmbeddingBackfillAuditUseCase;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillAuditOutcome;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillResult;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillSummary;
import java.util.List;
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
    "app.embedding.backfill.max-limit=100"
})
class EmbeddingBackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackfillEmbeddingsUseCase backfillEmbeddingsUseCase;

    @MockitoBean
    private RecordEmbeddingBackfillAuditUseCase recordEmbeddingBackfillAuditUseCase;

    @Test
    void backfillsEmbeddingsWithRequestedLimit() throws Exception {
        given(backfillEmbeddingsUseCase.backfillEmbeddings(25))
            .willReturn(new EmbeddingBackfillSummary(
                new EmbeddingBackfillResult(
                    "profiles",
                    25,
                    2,
                    2,
                    1,
                    0,
                    0,
                    0,
                    0,
                    List.of()
                ),
                new EmbeddingBackfillResult(
                    "issues",
                    25,
                    3,
                    2,
                    1,
                    0,
                    0,
                    0,
                    1,
                    List.of("issue-1: IllegalStateException")
                )
            ));

        mockMvc.perform(post("/api/v1/embeddings/backfill")
                .header("X-Aquila-Operation-Token", "test-operation-token")
                .header("X-Aquila-Operator", "release-manager")
                .param("limit", "25"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.profiles.target").value("profiles"))
            .andExpect(jsonPath("$.data.profiles.updated").value(2))
            .andExpect(jsonPath("$.data.profiles.failureReasons").isArray())
            .andExpect(jsonPath("$.data.issues.target").value("issues"))
            .andExpect(jsonPath("$.data.issues.failed").value(1))
            .andExpect(jsonPath("$.data.issues.failureReasons[0]").value("issue-1: IllegalStateException"));

        then(backfillEmbeddingsUseCase).should().backfillEmbeddings(25);
        then(recordEmbeddingBackfillAuditUseCase).should().record(argThat(event ->
            event.outcome() == EmbeddingBackfillAuditOutcome.SUCCEEDED
                && event.operator().equals("release-manager")
                && event.requestedLimit() == 25
                && event.effectiveLimit() == 25
                && event.profileUpdated() == 2
                && event.issueUpdated() == 2
                && event.failed() == 1
        ));
    }

    @Test
    void capsBackfillLimitAtConfiguredMaximum() throws Exception {
        given(backfillEmbeddingsUseCase.backfillEmbeddings(100))
            .willReturn(new EmbeddingBackfillSummary(
                new EmbeddingBackfillResult("profiles", 100, 0, 0, 0, 0, 0, 0, 0, List.of()),
                new EmbeddingBackfillResult("issues", 100, 0, 0, 0, 0, 0, 0, 0, List.of())
            ));

        mockMvc.perform(post("/api/v1/embeddings/backfill")
                .header("X-Aquila-Operation-Token", "test-operation-token")
                .param("limit", "5000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.profiles.requestedLimit").value(100))
            .andExpect(jsonPath("$.data.issues.requestedLimit").value(100));

        then(backfillEmbeddingsUseCase).should().backfillEmbeddings(100);
    }

    @Test
    void usesDefaultBackfillLimitWhenLimitIsOmitted() throws Exception {
        given(backfillEmbeddingsUseCase.backfillEmbeddings(50))
            .willReturn(new EmbeddingBackfillSummary(
                new EmbeddingBackfillResult("profiles", 50, 0, 0, 0, 0, 0, 0, 0, List.of()),
                new EmbeddingBackfillResult("issues", 50, 0, 0, 0, 0, 0, 0, 0, List.of())
            ));

        mockMvc.perform(post("/api/v1/embeddings/backfill")
                .header("X-Aquila-Operation-Token", "test-operation-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.profiles.requestedLimit").value(50))
            .andExpect(jsonPath("$.data.issues.requestedLimit").value(50));

        then(backfillEmbeddingsUseCase).should().backfillEmbeddings(50);
    }

    @Test
    void rejectsBackfillWhenOperationTokenIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/embeddings/backfill").param("limit", "25"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(backfillEmbeddingsUseCase);
        then(recordEmbeddingBackfillAuditUseCase).should().record(argThat(event ->
            event.outcome() == EmbeddingBackfillAuditOutcome.DENIED
                && event.reason().equals("missing-token")
                && event.requestedLimit() == 25
        ));
    }

    @Test
    void rejectsBackfillWhenOperationTokenDoesNotMatch() throws Exception {
        mockMvc.perform(post("/api/v1/embeddings/backfill")
                .header("X-Aquila-Operation-Token", "wrong-token")
                .header("X-Aquila-Operator", "release-manager")
                .param("limit", "25"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(backfillEmbeddingsUseCase);
        then(recordEmbeddingBackfillAuditUseCase).should().record(argThat(event ->
            event.outcome() == EmbeddingBackfillAuditOutcome.DENIED
                && event.operator().equals("release-manager")
                && event.reason().equals("invalid-token")
                && event.requestedLimit() == 25
        ));
    }
}
