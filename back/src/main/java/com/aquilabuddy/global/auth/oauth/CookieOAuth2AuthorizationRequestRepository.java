package com.aquilabuddy.global.auth.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * OAuth2 callback state를 서버 세션에 의존하지 않도록 단기 HttpOnly cookie에 저장합니다.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String AUTHORIZATION_REQUEST_COOKIE_NAME =
        "aquila_buddy_oauth2_authorization_request";

    private static final Duration COOKIE_MAX_AGE = Duration.ofMinutes(3);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;

    public CookieOAuth2AuthorizationRequestRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String encodedAuthorizationRequest = findCookieValue(request);
        if (encodedAuthorizationRequest == null || encodedAuthorizationRequest.isBlank()) {
            return null;
        }

        try {
            byte[] decodedAuthorizationRequest = DECODER.decode(encodedAuthorizationRequest);
            AuthorizationRequestPayload payload = objectMapper.readValue(
                decodedAuthorizationRequest,
                AuthorizationRequestPayload.class
            );
            return payload.toAuthorizationRequest();
        } catch (IllegalArgumentException | IOException ignored) {
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(
        OAuth2AuthorizationRequest authorizationRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            expireAuthorizationRequestCookie(response);
            return;
        }

        AuthorizationRequestPayload payload = AuthorizationRequestPayload.from(authorizationRequest);
        String encodedAuthorizationRequest = encodePayload(payload);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie(encodedAuthorizationRequest, COOKIE_MAX_AGE));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        expireAuthorizationRequestCookie(response);
        return authorizationRequest;
    }

    private static String findCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
            .filter(cookie -> AUTHORIZATION_REQUEST_COOKIE_NAME.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private static void expireAuthorizationRequestCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie("", Duration.ZERO));
    }

    private static String responseCookie(String value, Duration maxAge) {
        return ResponseCookie.from(AUTHORIZATION_REQUEST_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
            .toString();
    }

    private String encodePayload(AuthorizationRequestPayload payload) {
        try {
            return ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OAuth2 authorization request cookie를 생성할 수 없습니다.", exception);
        }
    }

    private record AuthorizationRequestPayload(
        String authorizationUri,
        String authorizationRequestUri,
        String clientId,
        String redirectUri,
        Set<String> scopes,
        String state,
        Map<String, Object> additionalParameters,
        Map<String, Object> attributes
    ) {

        private static AuthorizationRequestPayload from(OAuth2AuthorizationRequest authorizationRequest) {
            return new AuthorizationRequestPayload(
                authorizationRequest.getAuthorizationUri(),
                authorizationRequest.getAuthorizationRequestUri(),
                authorizationRequest.getClientId(),
                authorizationRequest.getRedirectUri(),
                authorizationRequest.getScopes(),
                authorizationRequest.getState(),
                authorizationRequest.getAdditionalParameters(),
                authorizationRequest.getAttributes()
            );
        }

        private OAuth2AuthorizationRequest toAuthorizationRequest() {
            OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(authorizationUri)
                .clientId(clientId)
                .redirectUri(redirectUri)
                .scopes(scopes == null ? Set.of() : scopes)
                .state(state)
                .additionalParameters(additionalParameters == null ? Map.of() : additionalParameters)
                .attributes(attributes == null ? Map.of() : attributes);

            if (authorizationRequestUri != null && !authorizationRequestUri.isBlank()) {
                builder.authorizationRequestUri(authorizationRequestUri);
            }

            return builder.build();
        }
    }
}
