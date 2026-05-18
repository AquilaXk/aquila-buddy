package com.aquilabuddy.global.ai;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google Gen AI Java SDK로 Vertex AI embedding을 호출합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "vertex-ai")
public class GoogleGenAiVertexEmbeddingGenerator implements VertexAiEmbeddingGenerator, AutoCloseable {

    private static final List<String> CLOUD_PLATFORM_SCOPE = List.of("https://www.googleapis.com/auth/cloud-platform");

    private final VertexAiProperties properties;
    private final String model;
    private final int dimensions;
    private volatile Client client;

    public GoogleGenAiVertexEmbeddingGenerator(VertexAiProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
        this.model = this.properties.embeddingModelOrDefault();
        this.dimensions = this.properties.embeddingDimensionsOrDefault();
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
    public List<Double> embed(String input) {
        String normalizedInput = requireInput(input);
        try {
            EmbedContentConfig config = EmbedContentConfig.builder()
                .taskType("RETRIEVAL_DOCUMENT")
                .outputDimensionality(dimensions)
                .autoTruncate(true)
                .build();
            EmbedContentResponse response = client().models.embedContent(model, normalizedInput, config);
            return firstValues(response);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Vertex AI embedding request failed.", exception);
        }
    }

    @Override
    public void close() {
        Client initializedClient = client;
        if (initializedClient != null) {
            initializedClient.close();
        }
    }

    private static List<Double> firstValues(EmbedContentResponse response) {
        List<ContentEmbedding> embeddings = response == null ? List.of() : response.embeddings().orElse(List.of());
        if (embeddings.isEmpty()) {
            throw new IllegalStateException("Vertex AI embedding response did not include embeddings.");
        }
        List<Float> values = embeddings.getFirst().values().orElse(List.of());
        if (values.isEmpty()) {
            throw new IllegalStateException("Vertex AI embedding response did not include vector values.");
        }
        return values.stream()
            .map(Float::doubleValue)
            .toList();
    }

    private static String requireInput(String input) {
        String normalized = Objects.requireNonNull(input, "input must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("input must not be blank.");
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
