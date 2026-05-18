package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class AiConfigurationDefaultsTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void applicationDefaultsToVertexAiProviderAndLocalCredentialFile() throws IOException {
        List<PropertySource<?>> sources = loadYaml("application.yml");

        assertThat(property(sources, "app.ai.provider")).isEqualTo("${AI_PROVIDER:vertex-ai}");
        assertThat(property(sources, "app.ai.vertex.credentials-path"))
            .isEqualTo("${VERTEX_AI_CREDENTIALS_PATH:${GOOGLE_APPLICATION_CREDENTIALS:./vertex-key.json}}");
    }

    @Test
    void devProfileDefaultsToVertexAiProvider() throws IOException {
        List<PropertySource<?>> sources = loadYaml("application-dev.yml");

        assertThat(property(sources, "app.ai.provider")).isEqualTo("${AI_PROVIDER:vertex-ai}");
    }

    private List<PropertySource<?>> loadYaml(String resourceName) throws IOException {
        return loader.load(resourceName, new FileSystemResource(Path.of("src/main/resources", resourceName)));
    }

    private static Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
            .map(source -> source.getProperty(name))
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);
    }
}
