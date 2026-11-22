package com.aquilabuddy.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CompositeAiCallObserverTest {

    @Test
    void recordsObservationToEveryAvailableObserver() {
        RecordingObserver first = new RecordingObserver();
        RecordingObserver second = new RecordingObserver();
        CompositeAiCallObserver observer = new CompositeAiCallObserver(
            provider(first),
            provider(second)
        );
        AiCallObservation observation = AiCallObservation.success(
            "vertex-ai",
            AiCallOperation.COMPLETION,
            "gemini-2.5-flash",
            "ai-client.complete.v1",
            Duration.ofMillis(10),
            1,
            2,
            Instant.parse("2026-05-18T07:00:00Z")
        );

        observer.record(observation);

        assertThat(first.observations).containsExactly(observation);
        assertThat(second.observations).containsExactly(observation);
    }

    private static ObjectProvider<AiCallObserver> provider(AiCallObserver observer) {
        return new ObjectProvider<>() {
            @Override
            public AiCallObserver getObject(Object... args) {
                return observer;
            }

            @Override
            public AiCallObserver getIfAvailable() {
                return observer;
            }

            @Override
            public AiCallObserver getIfUnique() {
                return observer;
            }

            @Override
            public AiCallObserver getObject() {
                return observer;
            }
        };
    }

    private static class RecordingObserver implements AiCallObserver {

        private final List<AiCallObservation> observations = new ArrayList<>();

        @Override
        public void record(AiCallObservation observation) {
            observations.add(observation);
        }
    }
}
