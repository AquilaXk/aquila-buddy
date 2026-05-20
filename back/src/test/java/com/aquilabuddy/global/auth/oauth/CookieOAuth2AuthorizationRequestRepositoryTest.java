package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private final CookieOAuth2AuthorizationRequestRepository repository =
        new CookieOAuth2AuthorizationRequestRepository(new ObjectMapper());

    @Test
    void savesAuthorizationRequestInSecureHttpOnlyCookie() {
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(authorizationRequest, new MockHttpServletRequest(), response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
            .contains(CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME + "=")
            .contains("Path=/")
            .contains("Max-Age=180")
            .contains("Secure")
            .contains("HttpOnly")
            .contains("SameSite=Lax");
    }

    @Test
    void loadsAuthorizationRequestFromCookie() {
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorizationRequest, new MockHttpServletRequest(), response);

        MockHttpServletRequest nextRequest = new MockHttpServletRequest();
        nextRequest.setCookies(cookieFrom(response));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(nextRequest);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("state-123");
        assertThat(loaded.getRedirectUri()).isEqualTo("https://buddy.aquilaxk.site/login/oauth2/code/github");
    }

    @Test
    void removeAuthorizationRequestExpiresCookieAndReturnsLoadedRequest() {
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorizationRequest, new MockHttpServletRequest(), saveResponse);
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setCookies(cookieFrom(saveResponse));
        MockHttpServletResponse removeResponse = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(callbackRequest, removeResponse);

        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("state-123");
        assertThat(removeResponse.getHeader(HttpHeaders.SET_COOKIE))
            .contains(CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME + "=;")
            .contains("Max-Age=0")
            .contains("Secure")
            .contains("HttpOnly")
            .contains("SameSite=Lax");
    }

    private static OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://github.com/login/oauth/authorize")
            .clientId("client-id")
            .redirectUri("https://buddy.aquilaxk.site/login/oauth2/code/github")
            .scopes(Set.of("read:user", "user:email"))
            .state("state-123")
            .build();
    }

    private static Cookie cookieFrom(MockHttpServletResponse response) {
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        String name = CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME;
        int start = setCookie.indexOf(name + "=") + name.length() + 1;
        int end = setCookie.indexOf(';');

        return new Cookie(name, setCookie.substring(start, end));
    }
}
