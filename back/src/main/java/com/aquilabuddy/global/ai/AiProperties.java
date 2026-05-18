package com.aquilabuddy.global.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    String provider,
    OpenAiCompatible openaiCompatible
) {

    private static final String DEFAULT_PROVIDER = "fixture";

    public String providerOrDefault() {
        return hasText(provider) ? provider.trim() : DEFAULT_PROVIDER;
    }

    public OpenAiCompatible openaiCompatibleOrDefault() {
        return openaiCompatible == null ? OpenAiCompatible.defaults() : openaiCompatible;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    public record OpenAiCompatible(
        String apiBaseUrl,
        String apiKey,
        String chatModel,
        Double temperature
    ) {

        private static final String DEFAULT_API_BASE_URL = "https://api.openai.com/v1";
        private static final String DEFAULT_CHAT_MODEL = "gpt-4o-mini";
        private static final double DEFAULT_TEMPERATURE = 0.2;

        static OpenAiCompatible defaults() {
            return new OpenAiCompatible(DEFAULT_API_BASE_URL, "", DEFAULT_CHAT_MODEL, DEFAULT_TEMPERATURE);
        }

        public String apiBaseUrlOrDefault() {
            return hasText(apiBaseUrl) ? trimTrailingSlash(apiBaseUrl.trim()) : DEFAULT_API_BASE_URL;
        }

        public String apiKeyOrBlank() {
            return apiKey == null ? "" : apiKey.trim();
        }

        public String chatModelOrDefault() {
            return hasText(chatModel) ? chatModel.trim() : DEFAULT_CHAT_MODEL;
        }

        public double temperatureOrDefault() {
            return temperature == null ? DEFAULT_TEMPERATURE : temperature;
        }

        private static String trimTrailingSlash(String value) {
            String normalized = value;
            while (normalized.endsWith("/") && normalized.length() > 1) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
    }
}
