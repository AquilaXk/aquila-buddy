package com.aquilabuddy.adapter.in.web.embedding;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.application.port.in.embedding.BackfillEmbeddingsUseCase;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillResult;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmbeddingBackfillController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmbeddingBackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackfillEmbeddingsUseCase backfillEmbeddingsUseCase;

    @Test
    void backfillsEmbeddingsWithRequestedLimit() throws Exception {
        given(backfillEmbeddingsUseCase.backfillEmbeddings(25))
            .willReturn(new EmbeddingBackfillSummary(
                new EmbeddingBackfillResult("profiles", 25, 2, 2, 1, 0, 0, 0, 0),
                new EmbeddingBackfillResult("issues", 25, 3, 2, 1, 0, 0, 0, 1)
            ));

        mockMvc.perform(post("/api/v1/embeddings/backfill").param("limit", "25"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.profiles.target").value("profiles"))
            .andExpect(jsonPath("$.data.profiles.updated").value(2))
            .andExpect(jsonPath("$.data.issues.target").value("issues"))
            .andExpect(jsonPath("$.data.issues.failed").value(1));

        then(backfillEmbeddingsUseCase).should().backfillEmbeddings(25);
    }
}
