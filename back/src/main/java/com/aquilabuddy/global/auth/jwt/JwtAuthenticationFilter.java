package com.aquilabuddy.global.auth.jwt;

import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization Bearer token 또는 인증 cookie를 검증해 요청 단위 SecurityContext를 구성합니다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String token = firstValidToken(extractBearerToken(request), extractCookieToken(request));

        if (token != null) {
            String githubId = jwtProvider.getGithubId(token);
            OAuthPrincipal principal = new OAuthPrincipal(githubId, Map.of());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String firstValidToken(String bearerToken, String cookieToken) {
        if (bearerToken != null && jwtProvider.isValid(bearerToken)) {
            return bearerToken;
        }

        if (cookieToken != null && jwtProvider.isValid(cookieToken)) {
            return cookieToken;
        }

        return null;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    private String extractCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String hostPrefixedToken = extractCookieValue(cookies, JwtCookieSupport.AUTH_COOKIE_NAME);
        if (hostPrefixedToken != null) {
            return hostPrefixedToken;
        }

        // __Host- prefix 배포 전환 동안 기존 cookie도 logout 전까지 수용합니다.
        return extractCookieValue(cookies, JwtCookieSupport.LEGACY_AUTH_COOKIE_NAME);
    }

    private String extractCookieValue(Cookie[] cookies, String name) {
        return Arrays.stream(cookies)
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
    }
}
