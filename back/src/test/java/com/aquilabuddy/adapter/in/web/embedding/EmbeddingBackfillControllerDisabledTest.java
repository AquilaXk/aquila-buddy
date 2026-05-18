package com.aquilabuddy.adapter.in.web.embedding;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.application.port.in.embedding.BackfillEmbeddingsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmbeddingBackfillController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmbeddingBackfillControllerDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackfillEmbeddingsUseCase backfillEmbeddingsUseCase;

    @Test
    void disablesBackfillWhenOperationTokenIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/v1/embeddings/backfill")
                .header("X-Aquila-Operation-Token", "test-operation-token")
                .param("limit", "25"))
            .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(backfillEmbeddingsUseCase);
    }
}
