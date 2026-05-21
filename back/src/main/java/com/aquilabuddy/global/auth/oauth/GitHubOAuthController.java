package com.aquilabuddy.global.auth.oauth;

import com.aquilabuddy.application.port.in.user.SyncAuthenticatedUserProfileUseCase;
import com.aquilabuddy.domain.user.AuthenticatedUserProfile;
import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * GitHub OAuth 시작과 승인 callback을 backend가 명시적으로 처리합니다.
 */
@Controller
public class GitHubOAuthController {

    private static final String REGISTRATION_ID = "github";
    private static final String REGISTRATION_ID_ATTRIBUTE = "registration_id";
    private static final String CALLBACK_PATH = "/login/oauth2/code/github";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final GitHubOAuthClient gitHubOAuthClient;
    private final SyncAuthenticatedUserProfileUseCase syncAuthenticatedUserProfileUseCase;
    private final OAuth2SuccessHandler successHandler;
    private final OAuth2FailureHandler failureHandler;
    private final SecureRandom secureRandom = new SecureRandom();

    public GitHubOAuthController(
        ClientRegistrationRepository clientRegistrationRepository,
        CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
        GitHubOAuthClient gitHubOAuthClient,
        SyncAuthenticatedUserProfileUseCase syncAuthenticatedUserProfileUseCase,
        OAuth2SuccessHandler successHandler,
        OAuth2FailureHandler failureHandler
    ) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.gitHubOAuthClient = gitHubOAuthClient;
        this.syncAuthenticatedUserProfileUseCase = syncAuthenticatedUserProfileUseCase;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
    }

    @GetMapping("/oauth2/authorization/github")
    public void authorize(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
        if (registration == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String state = generateState();
        String redirectUri = callbackUri(request);
        String authorizationRequestUri = authorizationRequestUri(registration, redirectUri, state);
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
            .authorizationRequestUri(authorizationRequestUri)
            .clientId(registration.getClientId())
            .redirectUri(redirectUri)
            .scopes(registration.getScopes())
            .state(state)
            .attributes(attributes -> attributes.put(REGISTRATION_ID_ATTRIBUTE, REGISTRATION_ID))
            .build();

        authorizationRequestRepository.saveAuthorizationRequest(authorizationRequest, request, response);
        response.sendRedirect(authorizationRequestUri);
    }

    @GetMapping(CALLBACK_PATH)
    public void callback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException, ServletException {
        OAuth2AuthorizationRequest authorizationRequest =
            authorizationRequestRepository.removeAuthorizationRequest(request, response);

        if (code == null || code.isBlank() || authorizationRequest == null || !authorizationRequest.getState().equals(state)) {
            redirectFailure(request, response, oauthException("authorization_request_not_found", "OAuth authorization state가 올바르지 않습니다."));
            return;
        }

        try {
            GitHubOAuthAccessToken token = gitHubOAuthClient.exchangeCode(code, authorizationRequest.getRedirectUri());
            GitHubOAuthUser user = gitHubOAuthClient.loadUser(token.accessToken());
            syncAuthenticatedUserProfile(user);
            OAuthPrincipal principal = new OAuthPrincipal(String.valueOf(user.id()), attributes(user));
            successHandler.onAuthenticationSuccess(
                request,
                response,
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
            );
        } catch (AuthenticationException exception) {
            redirectFailure(request, response, exception);
        } catch (OAuth2AuthorizationException exception) {
            redirectFailure(request, response, new OAuth2AuthenticationException(exception.getError(), exception.getMessage(), exception));
        } catch (RuntimeException exception) {
            redirectFailure(request, response, oauthException("oauth_callback_failed", "OAuth callback 처리에 실패했습니다."));
        }
    }

    private void syncAuthenticatedUserProfile(GitHubOAuthUser user) {
        try {
            syncAuthenticatedUserProfileUseCase.syncAuthenticatedUserProfile(new AuthenticatedUserProfile(
                user.id(),
                user.login(),
                user.name(),
                user.email(),
                user.avatarUrl()
            ));
        } catch (RuntimeException exception) {
            throw oauthException("profile_sync_failed", "OAuth 사용자 프로필 동기화에 실패했습니다.", exception);
        }
    }

    private void redirectFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException, ServletException {
        failureHandler.onAuthenticationFailure(request, response, exception);
    }

    private String authorizationRequestUri(ClientRegistration registration, String redirectUri, String state) {
        return UriComponentsBuilder.fromUriString(registration.getProviderDetails().getAuthorizationUri())
            .queryParam("response_type", OAuth2AuthorizationResponseType.CODE.getValue())
            .queryParam("client_id", registration.getClientId())
            .queryParam("scope", String.join(" ", registration.getScopes()))
            .queryParam("state", state)
            .queryParam("redirect_uri", redirectUri)
            .build()
            .encode()
            .toUriString();
    }

    private String callbackUri(HttpServletRequest request) {
        String scheme = headerOrDefault(request, "X-Forwarded-Proto", request.getScheme());
        String host = headerOrDefault(request, "X-Forwarded-Host", request.getHeader("Host"));
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }

        URI baseUri = URI.create(scheme + "://" + host);
        return UriComponentsBuilder.fromUri(baseUri)
            .replacePath(CALLBACK_PATH)
            .replaceQuery(null)
            .build()
            .toUriString();
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Map<String, Object> attributes(GitHubOAuthUser user) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("id", user.id());
        attributes.put("login", user.login());
        attributes.put("name", user.name());
        attributes.put("email", user.email());
        if (user.avatarUrl() != null) {
            attributes.put("avatar_url", user.avatarUrl());
        }
        return attributes;
    }

    private static String headerOrDefault(HttpServletRequest request, String header, String defaultValue) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static OAuth2AuthenticationException oauthException(String code, String description) {
        return oauthException(code, description, null);
    }

    private static OAuth2AuthenticationException oauthException(String code, String description, Throwable cause) {
        OAuth2Error error = new OAuth2Error(code, description, null);
        if (cause == null) {
            return new OAuth2AuthenticationException(error, description);
        }
        return new OAuth2AuthenticationException(error, description, cause);
    }
}
