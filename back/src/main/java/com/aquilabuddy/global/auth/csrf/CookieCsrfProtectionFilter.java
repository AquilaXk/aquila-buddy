package com.aquilabuddy.global.auth.csrf;

import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HttpOnly 인증 cookie가 포함된 상태 변경 API 요청에 Origin/Referer 또는 double-submit token 검증을 적용합니다.
 */
@Component
public class CookieCsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_METHODS = Set.of(
        HttpMethod.POST.name(),
        HttpMethod.PATCH.name(),
        HttpMethod.PUT.name(),
        HttpMethod.DELETE.name()
    );
    private static final Set<String> SESSION_ENDPOINTS = Set.of(
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout"
    );
    private static final String DEFAULT_ALLOWED_ORIGINS =
        "http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001";

    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;

    public CookieCsrfProtectionFilter(
        ObjectMapper objectMapper,
        @Value("${app.csrf.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}") String allowedOrigins
    ) {
        this.objectMapper = objectMapper;
        this.allowedOrigins = splitOrigins(allowedOrigins);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresCsrfProtection(request) || !hasAuthenticationCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (hasTrustedOrigin(request) || hasMatchingCsrfToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error("CSRF 검증에 실패했습니다.")));
    }

    private boolean requiresCsrfProtection(HttpServletRequest request) {
        return PROTECTED_METHODS.contains(request.getMethod())
            && request.getRequestURI().startsWith("/api/")
            && !SESSION_ENDPOINTS.contains(request.getRequestURI());
    }

    private boolean hasAuthenticationCookie(HttpServletRequest request) {
        return findCookieValue(request, JwtCookieSupport.AUTH_COOKIE_NAME) != null;
    }

    private boolean hasTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && allowedOrigins.contains(origin)) {
            return true;
        }

        String referer = request.getHeader(HttpHeaders.REFERER);
        return originFromReferer(referer).map(allowedOrigins::contains).orElse(false);
    }

    private boolean hasMatchingCsrfToken(HttpServletRequest request) {
        String headerToken = request.getHeader(CsrfCookieSupport.CSRF_HEADER_NAME);
        String cookieToken = findCookieValue(request, CsrfCookieSupport.CSRF_COOKIE_NAME);
        if (headerToken == null || headerToken.isBlank() || cookieToken == null || cookieToken.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
            headerToken.getBytes(StandardCharsets.UTF_8),
            cookieToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static java.util.Optional<String> originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return java.util.Optional.empty();
        }

        try {
            URI uri = new URI(referer);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return java.util.Optional.empty();
            }
            int port = uri.getPort();
            return java.util.Optional.of(port < 0 ? scheme + "://" + host : scheme + "://" + host + ":" + port);
        } catch (URISyntaxException exception) {
            return java.util.Optional.empty();
        }
    }

    private static String findCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
    }

    private static Set<String> splitOrigins(String origins) {
        if (origins == null || origins.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
