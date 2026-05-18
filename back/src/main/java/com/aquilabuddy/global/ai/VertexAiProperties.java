package com.aquilabuddy.global.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.vertex")
public record VertexAiProperties(
    String projectId,
    String location,
    String model,
    String credentialsPath
) {

    private static final String DEFAULT_LOCATION = "us-central1";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

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

    public String credentialsPathOrBlank() {
        return hasText(credentialsPath) ? credentialsPath.trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
