package com.aquilabuddy.adapter.out.langfuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LangfusePropertiesTest {

    @Test
    void disablesLangfuseByDefault() {
        LangfuseProperties properties = new LangfuseProperties(false, null, null, null, null);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.baseUrlOrDefault()).isEqualTo("https://cloud.langfuse.com");
        assertThat(properties.environmentOrDefault()).isEqualTo("local");
    }

    @Test
    void requiresKeysWhenEnabled() {
        LangfuseProperties properties = new LangfuseProperties(true, "https://langfuse.test", "pk", " ", "prod");

        assertThatThrownBy(properties::secretKeyOrThrow)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.ai.langfuse.secret-key");
    }
}
