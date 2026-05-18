package com.aquilabuddy.adapter.in.web.ai;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.application.port.in.ai.GetAiCallObservabilityUseCase;
import com.aquilabuddy.domain.ai.AiCallObservabilityReport;
import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiCallObservabilityController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiCallObservabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetAiCallObservabilityUseCase getAiCallObservabilityUseCase;

    @Test
    void returnsAiCallObservabilitySummary() throws Exception {
        given(getAiCallObservabilityUseCase.getRecentAiCallObservability(10))
            .willReturn(new AiCallObservabilityReport(
                10,
                1,
                1,
                0,
                42,
                120,
                32,
                List.of(new AiCallObservation(
                    "openai-compatible",
                    AiCallOperation.COMPLETION,
                    "gpt-4o-mini",
                    "ai-client.complete.v1",
                    42,
                    true,
                    null,
                    120,
                    32,
                    Instant.parse("2026-05-18T06:00:00Z")
                ))
            ));

        mockMvc.perform(get("/api/v1/ai/observability").param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.requestedLimit").value(10))
            .andExpect(jsonPath("$.data.successCount").value(1))
            .andExpect(jsonPath("$.data.averageLatencyMillis").value(42))
            .andExpect(jsonPath("$.data.totalInputTokens").value(120))
            .andExpect(jsonPath("$.data.totalOutputTokens").value(32))
            .andExpect(jsonPath("$.data.recentCalls[0].provider").value("openai-compatible"))
            .andExpect(jsonPath("$.data.recentCalls[0].model").value("gpt-4o-mini"))
            .andExpect(jsonPath("$.data.recentCalls[0].promptVersion").value("ai-client.complete.v1"));

        then(getAiCallObservabilityUseCase).should().getRecentAiCallObservability(10);
    }
}
