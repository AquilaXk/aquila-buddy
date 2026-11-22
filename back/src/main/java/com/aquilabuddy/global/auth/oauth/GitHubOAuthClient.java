package com.aquilabuddy.global.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * GitHub OAuth code를 access token으로 교환하고 token 소유자 정보를 조회합니다.
 */
@Component
public class GitHubOAuthClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String userUri;
    private final String emailsUri;

    public GitHubOAuthClient(
        RestClient.Builder restClientBuilder,
        @Value("${spring.security.oauth2.client.registration.github.client-id:local-client-id}") String clientId,
        @Value("${spring.security.oauth2.client.registration.github.client-secret:local-client-secret}") String clientSecret,
        @Value("${app.oauth2.github.token-uri:https://github.com/login/oauth/access_token}") String tokenUri,
        @Value("${app.oauth2.github.user-uri:https://api.github.com/user}") String userUri,
        @Value("${app.oauth2.github.emails-uri:https://api.github.com/user/emails}") String emailsUri
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.userUri = userUri;
        this.emailsUri = emailsUri;
    }

    public GitHubOAuthAccessToken exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        GitHubTokenResponse response;
        try {
            response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(GitHubTokenResponse.class);
        } catch (RestClientException exception) {
            throw oauthException("token_exchange_failed", "GitHub OAuth token 교환에 실패했습니다.", exception);
        }

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw oauthException(
                response == null || response.error() == null ? "invalid_token_response" : response.error(),
                response == null || response.errorDescription() == null
                    ? "GitHub OAuth token 응답이 올바르지 않습니다."
                    : response.errorDescription(),
                null
            );
        }

        return new GitHubOAuthAccessToken(response.accessToken());
    }

    public GitHubOAuthUser loadUser(String accessToken) {
        GitHubUserResponse response;
        try {
            response = restClient.get()
                .uri(userUri)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GitHubUserResponse.class);
        } catch (RestClientException exception) {
            throw oauthException("user_info_failed", "GitHub 사용자 정보를 가져오지 못했습니다.", exception);
        }

        if (response == null || response.id() == null) {
            throw oauthException("invalid_user_info", "GitHub ID를 가져올 수 없습니다.", null);
        }

        return new GitHubOAuthUser(
            response.id(),
            fallback(response.login(), "github-" + response.id()),
            fallback(response.name(), fallback(response.login(), "GitHub User")),
            resolveEmail(accessToken, response),
            response.avatarUrl()
        );
    }

    private String resolveEmail(String accessToken, GitHubUserResponse user) {
        GitHubEmailResponse[] emails = loadEmails(accessToken);
        String primaryVerifiedEmail = selectEmail(emails, true);
        if (primaryVerifiedEmail != null) {
            return primaryVerifiedEmail;
        }

        if (user.email() != null && !user.email().isBlank()) {
            return user.email();
        }

        String verifiedEmail = selectEmail(emails, false);
        if (verifiedEmail != null) {
            return verifiedEmail;
        }

        return fallback(user.login(), "github-" + user.id()) + "@users.noreply.github.com";
    }

    private GitHubEmailResponse[] loadEmails(String accessToken) {
        try {
            GitHubEmailResponse[] emails = restClient.get()
                .uri(emailsUri)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GitHubEmailResponse[].class);
            return emails == null ? new GitHubEmailResponse[0] : emails;
        } catch (RestClientException exception) {
            // GitHub email scope 누락이나 API 장애가 로그인 자체를 막지 않도록 공개 email/noreply fallback을 사용합니다.
            return new GitHubEmailResponse[0];
        }
    }

    private static String selectEmail(GitHubEmailResponse[] emails, boolean primaryOnly) {
        for (GitHubEmailResponse email : emails) {
            if (email == null || !Boolean.TRUE.equals(email.verified())) {
                continue;
            }
            if (primaryOnly && !Boolean.TRUE.equals(email.primary())) {
                continue;
            }
            if (email.email() != null && !email.email().isBlank()) {
                return email.email();
            }
        }
        return null;
    }

    private static OAuth2AuthenticationException oauthException(String code, String description, Throwable cause) {
        OAuth2Error error = new OAuth2Error(code, description, null);
        if (cause == null) {
            return new OAuth2AuthenticationException(error, description);
        }
        return new OAuth2AuthenticationException(error, description, cause);
    }

    private static String fallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private record GitHubTokenResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("error")
        String error,
        @JsonProperty("error_description")
        String errorDescription
    ) {
    }

    private record GitHubUserResponse(
        Long id,
        String login,
        String name,
        String email,
        @JsonProperty("avatar_url")
        String avatarUrl
    ) {
    }

    private record GitHubEmailResponse(
        String email,
        Boolean primary,
        Boolean verified
    ) {
    }
}
