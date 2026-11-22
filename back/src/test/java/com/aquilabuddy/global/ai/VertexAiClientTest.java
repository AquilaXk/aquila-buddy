package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
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
    void routesCompletionModelByUseCaseAndRecordsSelectedModel() {
        CapturingVertexAiTextGenerator generator = new CapturingVertexAiTextGenerator("generated guide");
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        VertexAiProperties properties = new VertexAiProperties(
            "grepp-ai-project",
            "us-central1",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash",
            "gemini-embedding-001",
            1536,
            null
        );
        VertexAiClient client = new VertexAiClient(generator, observer, properties);

        client.complete(AiCompletionUseCase.ISSUE_SUMMARY, "system", "summary request");
        client.complete(AiCompletionUseCase.ISSUE_GUIDE, "system", "guide request");

        assertThat(generator.models()).containsExactly("gemini-2.5-flash", "gemini-2.5-pro");
        assertThat(observer.observations())
            .extracting(observation -> observation.model())
            .containsExactly("gemini-2.5-flash", "gemini-2.5-pro");
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

    @Test
    void streamsTokensViaVertexGeneratorAndRecordsObservation() {
        VertexAiTextGenerator streamingGenerator = new VertexAiTextGenerator() {
            @Override
            public String generate(String prompt) {
                throw new AssertionError("non-streaming generate must not be used");
            }

            @Override
            public String generateStream(String model, String prompt, java.util.function.Consumer<String> onDelta) {
                onDelta.accept("chunk-1 ");
                onDelta.accept("chunk-2");
                return "chunk-1 chunk-2";
            }
        };
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        VertexAiClient client = new VertexAiClient(streamingGenerator, observer, "gemini-2.5-pro");
        java.util.List<String> deltas = new java.util.ArrayList<>();

        String content = client.stream(AiCompletionUseCase.ISSUE_GUIDE, "system", "user", deltas::add);

        assertThat(deltas).containsExactly("chunk-1 ", "chunk-2");
        assertThat(content).isEqualTo("chunk-1 chunk-2");
        assertThat(observer.firstObservation().provider()).isEqualTo("vertex-ai");
        assertThat(observer.firstObservation().model()).isEqualTo("gemini-2.5-pro");
        assertThat(observer.firstObservation().success()).isTrue();
    }

    @Test
    void failsClosedWhenVertexStreamThrowsOrReturnsBlank() {
        RecordingAiCallObserver observer = new RecordingAiCallObserver();
        VertexAiClient client = new VertexAiClient(new CapturingVertexAiTextGenerator(" "), observer, "gemini-2.5-flash");

        assertThatThrownBy(() -> client.stream(AiCompletionUseCase.ISSUE_GUIDE, "system", "user", delta -> { }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Vertex AI completion content");
        assertThat(observer.firstObservation().success()).isFalse();
    }

    private static final class CapturingVertexAiTextGenerator implements VertexAiTextGenerator {

        private final String response;
        private String prompt;
        private int callCount;
        private final java.util.List<String> models = new java.util.ArrayList<>();

        private CapturingVertexAiTextGenerator(String response) {
            this.response = response;
        }

        @Override
        public String generate(String model, String prompt) {
            this.models.add(model);
            return generate(prompt);
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

        private java.util.List<String> models() {
            return models;
        }
    }
}
