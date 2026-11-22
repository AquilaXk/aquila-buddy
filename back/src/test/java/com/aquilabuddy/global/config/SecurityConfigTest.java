package com.aquilabuddy.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.global.auth.csrf.CsrfCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.auth.oauth.CookieOAuth2AuthorizationRequestRepository;
import com.aquilabuddy.global.auth.oauth.GitHubOAuthAccessToken;
import com.aquilabuddy.global.auth.oauth.GitHubOAuthClient;
import com.aquilabuddy.global.auth.oauth.GitHubOAuthUser;
import com.aquilabuddy.global.auth.refresh.RefreshTokenCookieSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 프론트엔드 개발 서버에서 백엔드 API를 호출할 수 있도록 CORS 설정을 검증합니다.
 */
@SpringBootTest(properties = {
    "app.oauth2.redirect-uri=https://buddy.aquilaxk.site/oauth/callback",
    "app.cors.extension-allowed-origins=chrome-extension://abcdefghijklmnopabcdefghijklmnop"
})
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void allowsDesktopWebViewOriginsForApiPreflight() throws Exception {
        for (String origin : new String[]{"tauri://localhost", "http://tauri.localhost", "https://tauri.localhost"}) {
            mockMvc.perform(options("/api/v1/pr")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
    }

    @Test
    void desktopPkceAuthorizationRedirectsToLoopbackAndRejectsInvalidPorts() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        );

        for (String badPort : new String[]{"", "80", "1023", "65536", "8080@evil.com"}) {
            mockMvc.perform(get("/oauth2/authorization/github")
                    .param("client", "desktop")
                    .param("code_challenge", challenge)
                    .param("code_challenge_method", "S256")
                    .param("loopback_port", badPort))
                .andExpect(status().isBadRequest());
        }

        MvcResult authorization = mockMvc.perform(get("/oauth2/authorization/github")
                .param("client", "desktop")
                .param("code_challenge", challenge)
                .param("code_challenge_method", "S256")
                .param("loopback_port", "43210"))
            .andExpect(status().isFound())
            .andReturn();
        Cookie authorizationCookie = authorization.getResponse().getCookie(
            CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME
        );
        String state = stateFromLocation(authorization.getResponse().getHeader(HttpHeaders.LOCATION));
        org.mockito.BDDMockito.given(gitHubOAuthClient.exchangeCode(org.mockito.ArgumentMatchers.eq("code-desktop"), org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new GitHubOAuthAccessToken("gho_desktop_token"));
        org.mockito.BDDMockito.given(gitHubOAuthClient.loadUser("gho_desktop_token"))
            .willReturn(new GitHubOAuthUser(12345L, "aquila", "Aquila", "aquila@example.com"));

        MvcResult callback = mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-desktop")
                .param("state", state)
                .cookie(authorizationCookie))
            .andExpect(status().isFound())
            .andReturn();

        String location = callback.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith("http://127.0.0.1:43210/oauth/callback?ext_code=");
        assertThat(callback.getResponse().getCookies())
            .extracting(Cookie::getName)
            .doesNotContain(JwtCookieSupport.AUTH_COOKIE_NAME, RefreshTokenCookieSupport.REFRESH_COOKIE_NAME);

        String extCode = queryParamFromLocation(location, "ext_code");
        MvcResult exchange = mockMvc.perform(post("/api/v1/auth/extension/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + extCode + "\",\"codeVerifier\":\"" + verifier + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andReturn();

        String issuedJwt = com.jayway.jsonpath.JsonPath.read(exchange.getResponse().getContentAsString(), "$.data.token");
        assertThat(jwtProvider.getGithubId(issuedJwt)).isEqualTo("12345");
    }

    @Test
    void allowsConfiguredChromeExtensionOriginForApiPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/pr")
                .header(HttpHeaders.ORIGIN, "chrome-extension://abcdefghijklmnopabcdefghijklmnop")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                "chrome-extension://abcdefghijklmnopabcdefghijklmnop"
            ));
    }

    @Test
    void rejectsUnconfiguredChromeExtensionOriginForApiPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/pr")
                .header(HttpHeaders.ORIGIN, "chrome-extension://ponmlkjihgfedcbaponmlkjihgfedcba")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsProtectedApiWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void rejectsAnonymousIssueCrawlSearch() throws Exception {
        mockMvc.perform(post("/api/v1/issues/crawl/search")
                .param("q", "language:java label:\"good first issue\" state:open"))
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
    void rejectsCookieAuthenticatedMutationWithoutCsrfContext() throws Exception {
        String token = jwtProvider.generateToken("10001");

        mockMvc.perform(patch("/api/v1/users/me")
                .cookie(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileUpdateJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("CSRF 검증에 실패했습니다."));
    }

    @Test
    void allowsCookieAuthenticatedMutationFromTrustedOrigin() throws Exception {
        String token = jwtProvider.generateToken("10001");

        mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .cookie(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileUpdateJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void allowsCookieAuthenticatedMutationWithMatchingCsrfToken() throws Exception {
        String token = jwtProvider.generateToken("10001");

        mockMvc.perform(patch("/api/v1/users/me")
                .header("X-Aquila-CSRF-Token", "csrf-token-123")
                .cookie(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, token))
                .cookie(new Cookie("aquila_buddy_csrf", "csrf-token-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileUpdateJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void allowsBearerMutationWithoutCsrfContext() throws Exception {
        String token = jwtProvider.generateToken("10001");

        mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileUpdateJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void logoutClearsJwtCookieWithoutRequiringAuthentication() throws Exception {
        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

        assertThat(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .anySatisfy(setCookie -> assertThat(setCookie)
                .contains("__Host-aquila_buddy_token=;")
                .contains("Max-Age=0")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .doesNotContain("Domain="))
            .anySatisfy(setCookie -> assertThat(setCookie)
                .contains("aquila_buddy_token=;")
                .contains("Max-Age=0")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .doesNotContain("Domain="));
    }

    @Test
    void startsGithubOAuthWithCookieBackedAuthorizationRequest() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/github")
                .param("state", "/dashboard"))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("github.com/login/oauth/authorize")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("__Host-aquila_buddy_oauth2_authorization_request="),
                org.hamcrest.Matchers.containsString("Max-Age=180"),
                org.hamcrest.Matchers.containsString("HttpOnly"),
                org.hamcrest.Matchers.containsString("Secure"),
                org.hamcrest.Matchers.containsString("SameSite=Lax"),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Domain="))
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
                .contains("__Host-aquila_buddy_token=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .doesNotContain("Domain="));
        assertThat(callback.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .anySatisfy(setCookie -> assertThat(setCookie)
                .contains(RefreshTokenCookieSupport.REFRESH_COOKIE_NAME + "=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .doesNotContain("Domain="));

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
    void callbackMigratesExistingLoginProfileAndRedirectsToFrontendCallback() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO user_profiles (
                github_id,
                login,
                name,
                email,
                profile_ready,
                vector_updated_at
            ) VALUES (?, ?, ?, ?, TRUE, ?)
            """,
            77777L,
            "oauth-conflict-login",
            "Legacy OAuth User",
            "legacy-oauth@example.com",
            "2026-05-15T09:00:00Z"
        );
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
            .willReturn(new GitHubOAuthUser(88888L, "oauth-conflict-login", "Aquila", "aquila@example.com"));

        MvcResult callback = mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-123")
                .param("state", state)
                .cookie(authorizationCookie))
            .andExpect(status().isFound())
            .andExpect(header().string(
                HttpHeaders.LOCATION,
                "https://buddy.aquilaxk.site/oauth/callback"
            ))
            .andReturn();

        Cookie authCookie = callback.getResponse().getCookie(JwtCookieSupport.AUTH_COOKIE_NAME);
        assertThat(authCookie).isNotNull();
        mockMvc.perform(get("/api/v1/users/me")
            .cookie(authCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.githubId").value(88888))
            .andExpect(jsonPath("$.data.login").value("oauth-conflict-login"));
    }

    @Test
    void refreshRotatesRefreshCookieAndRejectsReuse() throws Exception {
        Cookie refreshCookie = loginAndExtractRefreshCookie();

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        Cookie rotatedRefreshCookie = refresh.getResponse().getCookie(RefreshTokenCookieSupport.REFRESH_COOKIE_NAME);
        assertThat(rotatedRefreshCookie).isNotNull();
        assertThat(rotatedRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());
        assertThat(refresh.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .anySatisfy(setCookie -> assertThat(setCookie).contains(JwtCookieSupport.AUTH_COOKIE_NAME + "="));
        assertThat(refresh.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .anySatisfy(setCookie -> assertThat(setCookie)
                .contains(CsrfCookieSupport.CSRF_COOKIE_NAME + "=")
                .contains("Max-Age=")
                .contains("Path=/")
                .contains("Secure")
                .contains("SameSite=Lax")
                .doesNotContain("HttpOnly"));
        Cookie authCookie = refresh.getResponse().getCookie(JwtCookieSupport.AUTH_COOKIE_NAME);
        Cookie csrfCookie = refresh.getResponse().getCookie(CsrfCookieSupport.CSRF_COOKIE_NAME);
        assertThat(authCookie).isNotNull();
        assertThat(csrfCookie).isNotNull();

        mockMvc.perform(post("/api/v1/issues/crawl/search")
                .param("q", "language:java label:\"good first issue\" state:open")
                .cookie(authCookie, csrfCookie)
                .header(CsrfCookieSupport.CSRF_HEADER_NAME, csrfCookie.getValue()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(refreshCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(rotatedRefreshCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void logoutRevokesRefreshCookie() throws Exception {
        Cookie refreshCookie = loginAndExtractRefreshCookie();

        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(refreshCookie))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(refreshCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void callbackFailureRedirectsToFrontendCallbackInsteadOfReturningUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-123")
                .param("state", "missing-state"))
            .andExpect(status().isFound())
            .andExpect(header().string(
                HttpHeaders.LOCATION,
                "https://buddy.aquilaxk.site/oauth/callback?error=authorization_request_not_found"
            ));
    }

    @Test
    void webOAuthPreservesSafeNextPathAndDropsUnsafeOpenRedirects() throws Exception {
        MvcResult safeAuth = mockMvc.perform(get("/oauth2/authorization/github")
                .param("next", "/issues/demo-1?tab=guide"))
            .andExpect(status().isFound())
            .andReturn();
        MockHttpServletResponse safeAuthResponse = safeAuth.getResponse();
        Cookie safeCookie = safeAuthResponse.getCookie(
            CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME
        );
        String safeState = stateFromLocation(safeAuthResponse.getHeader(HttpHeaders.LOCATION));
        org.mockito.BDDMockito.given(gitHubOAuthClient.exchangeCode(org.mockito.ArgumentMatchers.eq("code-next"), org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new GitHubOAuthAccessToken("gho_token"));
        org.mockito.BDDMockito.given(gitHubOAuthClient.loadUser("gho_token"))
            .willReturn(new GitHubOAuthUser(12345L, "aquila", "Aquila", "aquila@example.com"));

        mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-next")
                .param("state", safeState)
                .cookie(safeCookie))
            .andExpect(status().isFound())
            .andExpect(header().string(
                HttpHeaders.LOCATION,
                "https://buddy.aquilaxk.site/oauth/callback?next=%2Fissues%2Fdemo-1%3Ftab%3Dguide"
            ));

        for (String unsafeNext : new String[] {"//evil.com", "https://evil.com/steal", "/\\evil.com", "/dashboard"}) {
            MvcResult unsafeAuth = mockMvc.perform(get("/oauth2/authorization/github")
                    .param("next", unsafeNext))
                .andExpect(status().isFound())
                .andReturn();
            Cookie unsafeCookie = unsafeAuth.getResponse().getCookie(
                CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME
            );
            String unsafeState = stateFromLocation(unsafeAuth.getResponse().getHeader(HttpHeaders.LOCATION));
            org.mockito.BDDMockito.given(gitHubOAuthClient.exchangeCode(org.mockito.ArgumentMatchers.eq("code-unsafe"), org.mockito.ArgumentMatchers.anyString()))
                .willReturn(new GitHubOAuthAccessToken("gho_token"));

            mockMvc.perform(get("/login/oauth2/code/github")
                    .param("code", "code-unsafe")
                    .param("state", unsafeState)
                    .cookie(unsafeCookie))
                .andExpect(status().isFound())
                .andExpect(header().string(
                    HttpHeaders.LOCATION,
                    "https://buddy.aquilaxk.site/oauth/callback"
                ));
        }
    }

    @Test
    void extensionOAuthRequiresValidPkceChallenge() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/github")
                .param("client", "extension"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/oauth2/authorization/github")
                .param("client", "extension")
                .param("code_challenge", "too-short")
                .param("code_challenge_method", "S256"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/oauth2/authorization/github")
                .param("client", "extension")
                .param("code_challenge", "a".repeat(43))
                .param("code_challenge_method", "plain"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void extensionOAuthPkceHappyPathAndFailClosedExchange() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        );

        MvcResult authorization = mockMvc.perform(get("/oauth2/authorization/github")
                .param("client", "extension")
                .param("code_challenge", challenge)
                .param("code_challenge_method", "S256"))
            .andExpect(status().isFound())
            .andReturn();
        Cookie authorizationCookie = authorization.getResponse().getCookie(
            CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME
        );
        String state = stateFromLocation(authorization.getResponse().getHeader(HttpHeaders.LOCATION));
        org.mockito.BDDMockito.given(gitHubOAuthClient.exchangeCode(org.mockito.ArgumentMatchers.eq("code-ext"), org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new GitHubOAuthAccessToken("gho_ext_token"));
        org.mockito.BDDMockito.given(gitHubOAuthClient.loadUser("gho_ext_token"))
            .willReturn(new GitHubOAuthUser(12345L, "aquila", "Aquila", "aquila@example.com"));

        MvcResult callback = mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-ext")
                .param("state", state)
                .cookie(authorizationCookie))
            .andExpect(status().isFound())
            .andReturn();

        String location = callback.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith("https://buddy.aquilaxk.site/oauth/callback?ext_code=");
        String extCode = queryParamFromLocation(location, "ext_code");

        // Wrong verifier is rejected with 401 and consumes the one-time code
        mockMvc.perform(post("/api/v1/auth/extension/exchange")
                .cookie(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, jwtProvider.generateToken("10001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + extCode + "\",\"codeVerifier\":\"wrong-verifier\"}"))
            .andExpect(status().isUnauthorized());

        // Replaying the consumed code even with the right verifier fails with 401
        mockMvc.perform(post("/api/v1/auth/extension/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + extCode + "\",\"codeVerifier\":\"" + verifier + "\"}"))
            .andExpect(status().isUnauthorized());

        // Fresh PKCE flow succeeds without CSRF token even when auth cookie is attached
        MvcResult auth2 = mockMvc.perform(get("/oauth2/authorization/github")
                .param("client", "extension")
                .param("code_challenge", challenge)
                .param("code_challenge_method", "S256"))
            .andExpect(status().isFound())
            .andReturn();
        Cookie cookie2 = auth2.getResponse().getCookie(
            CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME
        );
        String state2 = stateFromLocation(auth2.getResponse().getHeader(HttpHeaders.LOCATION));
        MvcResult callback2 = mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-ext")
                .param("state", state2)
                .cookie(cookie2))
            .andExpect(status().isFound())
            .andReturn();
        String extCode2 = queryParamFromLocation(callback2.getResponse().getHeader(HttpHeaders.LOCATION), "ext_code");

        MvcResult exchange = mockMvc.perform(post("/api/v1/auth/extension/exchange")
                .cookie(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, jwtProvider.generateToken("10001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + extCode2 + "\",\"codeVerifier\":\"" + verifier + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.expiresInSeconds").value(3600))
            .andReturn();

        String issuedJwt = com.jayway.jsonpath.JsonPath.read(exchange.getResponse().getContentAsString(), "$.data.token");
        assertThat(jwtProvider.getGithubId(issuedJwt)).isEqualTo("12345");

        // Replay after successful exchange fails closed with 401
        mockMvc.perform(post("/api/v1/auth/extension/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + extCode2 + "\",\"codeVerifier\":\"" + verifier + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void oauthAuthorizationPreservesSafeNextAndLegacyStateAndRejectsOpenRedirect() throws Exception {
        org.mockito.BDDMockito.given(gitHubOAuthClient.exchangeCode(org.mockito.ArgumentMatchers.eq("code-next"), org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new GitHubOAuthAccessToken("gho_next_token"));
        org.mockito.BDDMockito.given(gitHubOAuthClient.loadUser("gho_next_token"))
            .willReturn(new GitHubOAuthUser(12345L, "aquila", "Aquila", "aquila@example.com"));

        MvcResult nextAuth = mockMvc.perform(get("/oauth2/authorization/github").param("next", "/issues/123"))
            .andExpect(status().isFound())
            .andReturn();
        MvcResult nextCallback = mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-next")
                .param("state", stateFromLocation(nextAuth.getResponse().getHeader(HttpHeaders.LOCATION)))
                .cookie(nextAuth.getResponse().getCookie(
                    CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME
                )))
            .andExpect(status().isFound())
            .andReturn();
        assertThat(nextCallback.getResponse().getHeader(HttpHeaders.LOCATION))
            .isEqualTo("https://buddy.aquilaxk.site/oauth/callback?next=%2Fissues%2F123");

        MvcResult legacyAuth = mockMvc.perform(get("/oauth2/authorization/github").param("state", "/profile"))
            .andExpect(status().isFound())
            .andReturn();
        MvcResult legacyCallback = mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-next")
                .param("state", stateFromLocation(legacyAuth.getResponse().getHeader(HttpHeaders.LOCATION)))
                .cookie(legacyAuth.getResponse().getCookie(
                    CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME
                )))
            .andExpect(status().isFound())
            .andReturn();
        assertThat(legacyCallback.getResponse().getHeader(HttpHeaders.LOCATION))
            .isEqualTo("https://buddy.aquilaxk.site/oauth/callback?next=%2Fprofile");

        MvcResult evilAuth = mockMvc.perform(get("/oauth2/authorization/github").param("next", "//evil.com"))
            .andExpect(status().isFound())
            .andReturn();
        MvcResult evilCallback = mockMvc.perform(get("/login/oauth2/code/github")
                .param("code", "code-next")
                .param("state", stateFromLocation(evilAuth.getResponse().getHeader(HttpHeaders.LOCATION)))
                .cookie(evilAuth.getResponse().getCookie(
                    CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME
                )))
            .andExpect(status().isFound())
            .andReturn();
        assertThat(evilCallback.getResponse().getHeader(HttpHeaders.LOCATION))
            .isEqualTo("https://buddy.aquilaxk.site/oauth/callback");
    }

    private static String stateFromLocation(String location) {
        return queryParamFromLocation(location, "state");
    }

    private static String queryParamFromLocation(String location, String name) {
        java.net.URI uri = java.net.URI.create(location);
        String prefix = name + "=";
        return java.util.Arrays.stream(uri.getQuery().split("&"))
            .filter(parameter -> parameter.startsWith(prefix))
            .map(parameter -> java.net.URLDecoder.decode(parameter.substring(prefix.length()), java.nio.charset.StandardCharsets.UTF_8))
            .findFirst()
            .orElseThrow();
    }

    private static String profileUpdateJson() {
        return """
            {
              "name": "Aquila",
              "email": "aquila@example.com",
              "primaryLanguages": ["Java"],
              "topics": ["spring"]
            }
            """;
    }

    private Cookie loginAndExtractRefreshCookie() throws Exception {
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
            .andReturn();
        Cookie refreshCookie = callback.getResponse().getCookie(RefreshTokenCookieSupport.REFRESH_COOKIE_NAME);
        assertThat(refreshCookie).isNotNull();
        return refreshCookie;
    }
}
