package com.aquilabuddy.global.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CookieCsrfProtectionFilterTest {

    private final CookieCsrfProtectionFilter filter = new CookieCsrfProtectionFilter(
        new ObjectMapper(),
        "http://localhost:3000"
    );

    @Test
    void rejectsCookieAuthenticatedMutationWithoutTrustedOriginOrToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/pr");
        request.setCookies(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, "jwt-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF 검증에 실패했습니다.");
    }

    @Test
    void allowsCookieAuthenticatedMutationWithTrustedReferer() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/users/me");
        request.addHeader(HttpHeaders.REFERER, "http://localhost:3000/profile");
        request.setCookies(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, "jwt-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsCookieAuthenticatedMutationWithMatchingDoubleSubmitToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/pr/draft-1");
        request.addHeader(CsrfCookieSupport.CSRF_HEADER_NAME, "csrf-token-123");
        request.setCookies(
            new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, "jwt-token"),
            new Cookie(CsrfCookieSupport.CSRF_COOKIE_NAME, "csrf-token-123")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsBearerOnlyMutation() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/pr");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsSessionRefreshWithoutCsrfToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setCookies(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, "jwt-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsLogoutWithoutCsrfToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.setCookies(new Cookie(JwtCookieSupport.AUTH_COOKIE_NAME, "jwt-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
