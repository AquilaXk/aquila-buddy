package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.domain.ai.AiCallOperation;
import org.junit.jupiter.api.Test;

class VertexAiClientTest {

    @Test
    void delegatesCompletionPromptToVertexGenerator() {
        CapturingVertexAiTextGenerator generator = new CapturingVertexAiTextGenerator("generated guide");
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        VertexAiClient client = new VertexAiClient(generator, observer, "gemini-2.5-flash");

        String content = client.complete("system guidance", "user request");

        assertThat(content).isEqualTo("generated guide");
        assertThat(generator.prompt())
            .contains("System prompt:")
            .contains("system guidance")
            .contains("User prompt:")
            .contains("user request");
        assertThat(observer.firstObservation().provider()).isEqualTo("vertex-ai");
        assertThat(observer.firstObservation().operation()).isEqualTo(AiCallOperation.COMPLETION);
        assertThat(observer.firstObservation().model()).isEqualTo("gemini-2.5-flash");
        assertThat(observer.firstObservation().promptVersion()).isEqualTo("ai-client.complete.v1");
        assertThat(observer.firstObservation().success()).isTrue();
    }

    @Test
    void rejectsBlankGeneratedContent() {
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        VertexAiClient client = new VertexAiClient(new CapturingVertexAiTextGenerator(" "), observer, "gemini-2.5-flash");

        assertThatThrownBy(() -> client.complete("system", "user"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Vertex AI completion content");
        assertThat(observer.firstObservation().success()).isFalse();
        assertThat(observer.firstObservation().errorType()).isEqualTo("IllegalStateException");
        assertThat(observer.firstObservation().failureReason()).contains("Vertex AI completion content");
    }

    @Test
    void rejectsBlankPromptBeforeCallingVertex() {
        CapturingVertexAiTextGenerator generator = new CapturingVertexAiTextGenerator("generated guide");
        VertexAiClient client = new VertexAiClient(generator);

        assertThatThrownBy(() -> client.complete(" ", "user"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("systemPrompt");
        assertThat(generator.callCount()).isZero();
    }

    private static final class CapturingVertexAiTextGenerator implements VertexAiTextGenerator {

        private final String response;
        private String prompt;
        private int callCount;

        private CapturingVertexAiTextGenerator(String response) {
            this.response = response;
        }

        @Override
        public String generate(String prompt) {
            this.prompt = prompt;
            this.callCount++;
            return response;
        }

        private String prompt() {
            return prompt;
        }

        private int callCount() {
            return callCount;
        }
    }
}
