package com.aquilabuddy.global.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google Gen AI Java SDK로 Vertex AI Gemini completion을 호출합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "vertex-ai")
public class GoogleGenAiVertexTextGenerator implements VertexAiTextGenerator, AutoCloseable {

    private static final List<String> CLOUD_PLATFORM_SCOPE = List.of("https://www.googleapis.com/auth/cloud-platform");

    private final VertexAiProperties properties;
    private volatile Client client;

    public GoogleGenAiVertexTextGenerator(VertexAiProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
    }

    private static Client buildClient(VertexAiProperties properties) {
        Client.Builder builder = Client.builder()
            .project(properties.projectIdOrThrow())
            .location(properties.locationOrDefault())
            .enterprise(true);
        String credentialsPath = properties.credentialsPathOrBlank();
        if (!credentialsPath.isBlank()) {
            builder.credentials(loadCredentials(credentialsPath));
        }
        return builder.build();
    }

    @Override
    public String generate(String prompt) {
        return generate(properties.modelOrDefault(), prompt);
    }

    @Override
    public String generate(String model, String prompt) {
        String selectedModel = requireModel(model);
        String normalizedPrompt = requirePrompt(prompt);
        try {
            GenerateContentResponse response = client().models.generateContent(selectedModel, normalizedPrompt, null);
            return response == null ? null : response.text();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Vertex AI completion request failed.", exception);
        }
    }

    @Override
    public void close() {
        Client initializedClient = client;
        if (initializedClient != null) {
            initializedClient.close();
        }
    }

    private static String requirePrompt(String prompt) {
        String normalized = Objects.requireNonNull(prompt, "prompt must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException("prompt must not be blank.");
        }
        return normalized;
    }

    private static String requireModel(String model) {
        String normalized = Objects.requireNonNull(model, "model must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException("model must not be blank.");
        }
        return normalized;
    }

    private static GoogleCredentials loadCredentials(String credentialsPath) {
        try (InputStream inputStream = Files.newInputStream(Path.of(credentialsPath))) {
            return GoogleCredentials.fromStream(inputStream).createScoped(CLOUD_PLATFORM_SCOPE);
        } catch (IOException exception) {
            throw new IllegalStateException("app.ai.vertex.credentials-path could not be loaded.", exception);
        }
    }

    private Client client() {
        Client initializedClient = client;
        if (initializedClient == null) {
            synchronized (this) {
                initializedClient = client;
                if (initializedClient == null) {
                    initializedClient = buildClient(properties);
                    client = initializedClient;
                }
            }
        }
        return initializedClient;
    }
}
