package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiEnvironmentExamplesTest {

    @Test
    void envExamplesUseVertexAiAndDoNotExposeLegacyApiKeyNames() throws IOException {
        String rootExample = Files.readString(Path.of("../.env.example"));
        String backendExample = Files.readString(Path.of(".env.example"));

        assertThat(rootExample)
            .contains("AI_PROVIDER=vertex-ai")
            .contains("GOOGLE_CLOUD_PROJECT_ID=grepp-ai-project")
            .contains("VERTEX_AI_LOCATION=us-central1")
            .contains("VERTEX_AI_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_CREDENTIALS_PATH=./vertex-key.json")
            .contains("LANGFUSE_ENABLED=false")
            .contains("LANGFUSE_BASE_URL=https://cloud.langfuse.com")
            .contains("LANGFUSE_PUBLIC_KEY=")
            .contains("LANGFUSE_SECRET_KEY=")
            .doesNotContain(legacyGlmApiKeyName())
            .doesNotContain("GEMINI_API_KEY");
        assertThat(backendExample)
            .contains("AI_PROVIDER=vertex-ai")
            .contains("GOOGLE_CLOUD_PROJECT_ID=grepp-ai-project")
            .contains("VERTEX_AI_LOCATION=us-central1")
            .contains("VERTEX_AI_MODEL=gemini-2.5-flash")
            .contains("VERTEX_AI_CREDENTIALS_PATH=./vertex-key.json")
            .contains("LANGFUSE_ENABLED=false")
            .contains("LANGFUSE_BASE_URL=https://cloud.langfuse.com")
            .contains("LANGFUSE_PUBLIC_KEY=")
            .contains("LANGFUSE_SECRET_KEY=")
            .doesNotContain(legacyGlmApiKeyName())
            .doesNotContain("GEMINI_API_KEY");
    }

    @Test
    void runtimeDocsAndDevConfigurationUseVertexAiOnlyDefaults() throws IOException {
        String applicationDev = Files.readString(Path.of("src/main/resources/application-dev.yml"));
        String readme = Files.readString(Path.of("../README.md"));
        String smokeScript = Files.readString(Path.of("../tools/smoke/validate-ai-runtime-env.sh"));

        assertThat(applicationDev)
            .contains("provider: ${AI_PROVIDER:vertex-ai}")
            .doesNotContain(legacyGlmApiKeyName());
        assertThat(readme)
            .contains("기본 운영 AI provider는 `vertex-ai`입니다.")
            .contains("LANGFUSE_ENABLED=true")
            .contains("Langfuse tracing은 선택 기능")
            .doesNotContain("AI_PROVIDER=openai-compatible")
            .doesNotContain(legacyGlmApiKeyName());
        assertThat(smokeScript)
            .contains("provider=\"${AI_PROVIDER:-vertex-ai}\"")
            .doesNotContain("openai-compatible")
            .doesNotContain(legacyGlmApiKeyName());
    }

    private static String legacyGlmApiKeyName() {
        return "G" + "LM_API_KEY";
    }

}
