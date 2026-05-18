package com.aquilabuddy.application.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.ai.LoadAiCallObservabilityPort;
import com.aquilabuddy.domain.ai.AiCallObservabilityReport;
import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiCallObservabilityServiceTest {

    @Test
    void delegatesRecentObservabilityLimitToPort() {
        RecordingLoadAiCallObservabilityPort port = new RecordingLoadAiCallObservabilityPort();
        AiCallObservabilityService service = new AiCallObservabilityService(port);

        AiCallObservabilityReport report = service.getRecentAiCallObservability(30);

        assertThat(port.limit).isEqualTo(30);
        assertThat(report.successCount()).isEqualTo(1);
        assertThat(report.recentCalls()).hasSize(1);
    }

    private static class RecordingLoadAiCallObservabilityPort implements LoadAiCallObservabilityPort {

        private int limit;

        @Override
        public AiCallObservabilityReport loadRecentAiCallObservability(int limit) {
            this.limit = limit;
            return new AiCallObservabilityReport(
                limit,
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
            );
        }
    }
}
