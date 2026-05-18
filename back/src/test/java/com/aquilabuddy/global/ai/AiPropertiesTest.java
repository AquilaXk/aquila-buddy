package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiPropertiesTest {

    @Test
    void usesVertexAiAsDefaultProvider() {
        AiProperties properties = new AiProperties(null);

        assertThat(properties.providerOrDefault()).isEqualTo("vertex-ai");
    }

    @Test
    void trimsConfiguredProvider() {
        AiProperties properties = new AiProperties(" openai-compatible ");

        assertThat(properties.providerOrDefault()).isEqualTo("openai-compatible");
    }
}
