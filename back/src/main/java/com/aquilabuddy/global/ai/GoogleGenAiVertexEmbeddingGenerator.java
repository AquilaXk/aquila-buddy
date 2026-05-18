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

/**
 * Google Gen AI Java SDK로 Vertex AI embedding을 호출합니다.
 */
public class GoogleGenAiVertexEmbeddingGenerator implements VertexAiEmbeddingGenerator, AutoCloseable {

    private static final List<String> CLOUD_PLATFORM_SCOPE = List.of("https://www.googleapis.com/auth/cloud-platform");

    private final Client client;
    private final String model;
    private final int dimensions;

    public GoogleGenAiVertexEmbeddingGenerator(VertexAiProperties properties) {
        VertexAiProperties safeProperties = Objects.requireNonNull(properties, "properties must not be null.");
        Client.Builder builder = Client.builder()
            .project(safeProperties.projectIdOrThrow())
            .location(safeProperties.locationOrDefault())
            .enterprise(true);
        String credentialsPath = safeProperties.credentialsPathOrBlank();
        if (!credentialsPath.isBlank()) {
            builder.credentials(loadCredentials(credentialsPath));
        }
        this.client = builder.build();
        this.model = safeProperties.embeddingModelOrDefault();
        this.dimensions = safeProperties.embeddingDimensionsOrDefault();
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
            EmbedContentResponse response = client.models.embedContent(model, normalizedInput, config);
            return firstValues(response);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Vertex AI embedding request failed.", exception);
        }
    }

    @Override
    public void close() {
        client.close();
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
}
