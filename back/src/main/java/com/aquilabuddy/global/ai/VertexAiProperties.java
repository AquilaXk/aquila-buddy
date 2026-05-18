package com.aquilabuddy.global.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.vertex")
public record VertexAiProperties(
    String projectId,
    String location,
    String model,
    String embeddingModel,
    Integer embeddingDimensions,
    String credentialsPath
) {

    private static final String DEFAULT_LOCATION = "us-central1";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final String DEFAULT_EMBEDDING_MODEL = "gemini-embedding-001";
    private static final int DEFAULT_EMBEDDING_DIMENSIONS = 1536;

    public String projectIdOrThrow() {
        if (!hasText(projectId)) {
            throw new IllegalStateException("app.ai.vertex.project-id must not be blank.");
        }
        return projectId.trim();
    }

    public String locationOrDefault() {
        return hasText(location) ? location.trim() : DEFAULT_LOCATION;
    }

    public String modelOrDefault() {
        return hasText(model) ? model.trim() : DEFAULT_MODEL;
    }

    public String embeddingModelOrDefault() {
        return hasText(embeddingModel) ? embeddingModel.trim() : DEFAULT_EMBEDDING_MODEL;
    }

    public int embeddingDimensionsOrDefault() {
        return embeddingDimensions == null ? DEFAULT_EMBEDDING_DIMENSIONS : embeddingDimensions;
    }

    public String credentialsPathOrBlank() {
        return hasText(credentialsPath) ? credentialsPath.trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
