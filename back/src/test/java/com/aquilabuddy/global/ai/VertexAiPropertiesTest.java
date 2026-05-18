package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VertexAiPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(AiConfig.class);

    @Test
    void bindsVertexProviderProperties() {
        contextRunner
            .withPropertyValues(
                "app.ai.provider=vertex-ai",
                "app.ai.vertex.project-id=grepp-ai-project",
                "app.ai.vertex.location=us-central1",
                "app.ai.vertex.model=gemini-2.5-flash",
                "app.ai.vertex.embedding-model=gemini-embedding-001",
                "app.ai.vertex.embedding-dimensions=1536",
                "app.ai.vertex.credentials-path=/tmp/vertex-key.json"
            )
            .run(context -> {
                VertexAiProperties properties = context.getBean(VertexAiProperties.class);

                assertThat(properties.projectIdOrThrow()).isEqualTo("grepp-ai-project");
                assertThat(properties.locationOrDefault()).isEqualTo("us-central1");
                assertThat(properties.modelOrDefault()).isEqualTo("gemini-2.5-flash");
                assertThat(properties.embeddingModelOrDefault()).isEqualTo("gemini-embedding-001");
                assertThat(properties.embeddingDimensionsOrDefault()).isEqualTo(1536);
                assertThat(properties.credentialsPathOrBlank()).isEqualTo("/tmp/vertex-key.json");
            });
    }

    @Test
    void usesVertexDefaultsForBlankOptionalValues() {
        VertexAiProperties properties = new VertexAiProperties(
            " grepp-ai-project ",
            " ",
            " ",
            " ",
            null,
            null
        );

        assertThat(properties.projectIdOrThrow()).isEqualTo("grepp-ai-project");
        assertThat(properties.locationOrDefault()).isEqualTo("us-central1");
        assertThat(properties.modelOrDefault()).isEqualTo("gemini-2.5-flash");
        assertThat(properties.embeddingModelOrDefault()).isEqualTo("gemini-embedding-001");
        assertThat(properties.embeddingDimensionsOrDefault()).isEqualTo(1536);
        assertThat(properties.credentialsPathOrBlank()).isEmpty();
    }

    @Test
    void rejectsBlankProjectId() {
        VertexAiProperties properties = new VertexAiProperties(" ", "us-central1", "gemini-2.5-flash", null, null, null);

        assertThatThrownBy(properties::projectIdOrThrow)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.ai.vertex.project-id");
    }
}
