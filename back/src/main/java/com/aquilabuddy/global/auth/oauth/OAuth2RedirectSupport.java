package com.aquilabuddy.global.auth.oauth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * OAuth2 로그인 redirect URL 및 flow 파라미터를 안전하게 검증·조립합니다.
 */
final class OAuth2RedirectSupport {

    static final String PARAM_CLIENT = "aquila_client";
    static final String PARAM_NEXT = "aquila_next";
    static final String PARAM_CODE_CHALLENGE = "aquila_code_challenge";
    static final String PARAM_CODE_CHALLENGE_METHOD = "aquila_code_challenge_method";
    static final String PARAM_LOOPBACK_PORT = "aquila_loopback_port";
    static final String CLIENT_WEB = "web";
    static final String CLIENT_EXTENSION = "extension";
    static final String CLIENT_DESKTOP = "desktop";
    static final String PKCE_METHOD_S256 = "S256";
    static final int MIN_LOOPBACK_PORT = 1024;
    static final int MAX_LOOPBACK_PORT = 65535;
    static final int MAX_NEXT_LENGTH = 512;

    private OAuth2RedirectSupport() {
    }

    static String sanitizeNext(String rawNext) {
        if (rawNext == null) {
            return null;
        }
        String trimmed = rawNext.trim();
        if (trimmed.isEmpty() || "/dashboard".equals(trimmed) || trimmed.length() > MAX_NEXT_LENGTH) {
            return null;
        }
        if (trimmed.charAt(0) != '/') {
            return null;
        }
        if (trimmed.length() >= 2 && (trimmed.charAt(1) == '/' || trimmed.charAt(1) == '\\')) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/%2f") || lower.startsWith("/%5c")) {
            return null;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char ch = trimmed.charAt(index);
            if (ch <= 0x20 || ch == 0x7F || ch == '\\') {
                return null;
            }
        }
        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() != null || uri.getAuthority() != null || uri.getHost() != null) {
                return null;
            }
            String path = uri.getPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return null;
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return trimmed;
    }

    static Integer sanitizeLoopbackPort(String rawPort) {
        if (rawPort == null || rawPort.isEmpty() || rawPort.length() > 5) {
            return null;
        }
        int parsed = 0;
        for (int index = 0; index < rawPort.length(); index++) {
            char ch = rawPort.charAt(index);
            if (ch < '0' || ch > '9') {
                return null;
            }
            parsed = parsed * 10 + (ch - '0');
        }
        if (parsed < MIN_LOOPBACK_PORT || parsed > MAX_LOOPBACK_PORT) {
            return null;
        }
        return parsed;
    }

    static String buildLoopbackCallbackUri(int port) {
        if (port < MIN_LOOPBACK_PORT || port > MAX_LOOPBACK_PORT) {
            throw new IllegalArgumentException("loopback port out of valid unprivileged range.");
        }
        return "http://127.0.0.1:" + port + "/oauth/callback";
    }

    static String appendQueryParam(String baseUri, String key, String rawValue) {
        String separator = baseUri.contains("?") ? "&" : "?";
        return baseUri + separator + key + "=" + URLEncoder.encode(rawValue, StandardCharsets.UTF_8);
    }
}
