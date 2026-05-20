package com.aquilabuddy.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.auth.oauth.CookieOAuth2AuthorizationRequestRepository;
import com.aquilabuddy.global.auth.oauth.GitHubOAuthAccessToken;
import com.aquilabuddy.global.auth.oauth.GitHubOAuthClient;
import com.aquilabuddy.global.auth.oauth.GitHubOAuthUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 프론트엔드 개발 서버에서 백엔드 API를 호출할 수 있도록 CORS 설정을 검증합니다.
 */
@SpringBootTest(properties = "app.oauth2.redirect-uri=https://buddy.aquilaxk.site/oauth/callback")
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private GitHubOAuthClient gitHubOAuthClient;

    @Test
    void allowsFrontendDevelopmentOriginForApiPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/pr")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void rejectsProtectedApiWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void allowsProtectedApiWithBearerToken() throws Exception {
        String token = jwtProvider.generateToken("10001");

        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void allowsProtectedApiWithJwtCookie() throws Exception {
        String token = jwtProvider.generateToken("10001");

        mockMvc.perform(get("/api/v1/users/me")
                .cookie(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void allowsProtectedApiWithJwtCookieWhenBearerTokenIsStale() throws Exception {
        String token = jwtProvider.generateToken("10001");

        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer stale-local-storage-token")
                .cookie(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void logoutClearsJwtCookieWithoutRequiringAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString(JwtCookieSupport.AUTH_COOKIE_NAME + "=;"),
                org.hamcrest.Matchers.containsString("Max-Age=0"),
                org.hamcrest.Matchers.containsString("HttpOnly"),
                org.hamcrest.Matchers.containsString("Secure"),
                org.hamcrest.Matchers.containsString("SameSite=Lax")
            )));
    }

    @Test
    void startsGithubOAuthWithCookieBackedAuthorizationRequest() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/github")
                .param("state", "/dashboard"))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("github.com/login/oauth/authorize")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString(
                    CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME + "="
                ),
                org.hamcrest.Matchers.containsString("Max-Age=180"),
                org.hamcrest.Matchers.containsString("HttpOnly"),
                org.hamcrest.Matchers.containsString("Secure"),
                org.hamcrest.Matchers.containsString("SameSite=Lax")
            )));
    }

    @Test
    void callbackIssuesJwtCookieAndRedirectsToFrontendCallback() throws Exception {
        MvcResult authorization = mockMvc.perform(get("/oauth2/authorization/github"))
            .andExpect(status().isFound())
            .andReturn();
        MockHttpServletResponse authorizationResponse = authorization.getResponse();
        Cookie authorizationCookie = authorizationResponse.getCookie(
            CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME
        );
        String state = stateFromLocation(authorizationResponse.getHeader(HttpHeaders.LOCATION));
        org.mockito.BDDMockito.given(gitHubOAuthClient.exchangeCode(org.mockito.ArgumentMatchers.eq("code-123"), org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new GitHubOAuthAccessToken("gho_token"));
        org.mockito.BDDMockito.given(gitHubOAuthClient.loadUser("gho_token"))
            .willReturn(new GitHubOAuthUser(12345L, "aquila", "Aquila", "aquila@example.com"));

        MvcResult callback = mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-123")
                .param("state", state)
                .cookie(authorizationCookie))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "https://buddy.aquilaxk.site/oauth/callback"))
            .andReturn();
        assertThat(callback.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .anySatisfy(setCookie -> assertThat(setCookie)
                .contains(JwtCookieSupport.AUTH_COOKIE_NAME + "=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax"));

        Cookie authCookie = callback.getResponse().getCookie(JwtCookieSupport.AUTH_COOKIE_NAME);
        assertThat(authCookie).isNotNull();
        mockMvc.perform(get("/api/v1/users/me")
                .cookie(authCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.githubId").value(12345))
            .andExpect(jsonPath("$.data.login").value("aquila"));
    }

    @Test
    void callbackFailureRedirectsToFrontendCallbackInsteadOfReturningUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-123")
                .param("state", "missing-state"))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "https://buddy.aquilaxk.site/oauth/callback?error=oauth_failed"));
    }

    private static String stateFromLocation(String location) {
        java.net.URI uri = java.net.URI.create(location);
        return java.util.Arrays.stream(uri.getQuery().split("&"))
            .filter(parameter -> parameter.startsWith("state="))
            .map(parameter -> java.net.URLDecoder.decode(parameter.substring("state=".length()), java.nio.charset.StandardCharsets.UTF_8))
            .findFirst()
            .orElseThrow();
    }
}
