package com.aquilabuddy.global.config;

import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import com.aquilabuddy.global.auth.csrf.CookieCsrfProtectionFilter;
import com.aquilabuddy.global.auth.jwt.JwtAuthenticationFilter;
import com.aquilabuddy.global.auth.jwt.JwtProperties;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 기본 필터 체인을 구성합니다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String DEFAULT_FRONTEND_ALLOWED_ORIGINS =
        "http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001";
    private static final String DEFAULT_EXTENSION_ALLOWED_ORIGINS = "chrome-extension://*";
    private static final Set<String> PROTECTED_PROFILES = Set.of("prod", "production", "staging");

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;
    private final CookieCsrfProtectionFilter cookieCsrfProtectionFilter;

    public SecurityConfig(
        JwtProvider jwtProvider,
        ObjectMapper objectMapper,
        CookieCsrfProtectionFilter cookieCsrfProtectionFilter
    ) {
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
        this.cookieCsrfProtectionFilter = cookieCsrfProtectionFilter;
    }

    /**
     * OAuth2 로그인과 JWT Bearer token 기반 API 인증을 함께 구성합니다.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**")
                .permitAll()
                .requestMatchers(
                    "/api/v1/health",
                    "/api/v1/auth/logout",
                    "/actuator/health",
                    "/oauth2/**",
                    "/login/**",
                    "/oauth/callback",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                )
                .permitAll()
                .anyRequest()
                .authenticated()
            )
            .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error("로그인이 필요합니다.")));
            }))
            .addFilterBefore(cookieCsrfProtectionFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    /**
     * 로컬 프론트엔드 개발 서버에서 백엔드 API를 직접 호출할 수 있도록 API CORS를 허용합니다.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.frontend-allowed-origins:" + DEFAULT_FRONTEND_ALLOWED_ORIGINS + "}") String frontendAllowedOrigins,
        @Value("${app.cors.extension-allowed-origins:" + DEFAULT_EXTENSION_ALLOWED_ORIGINS + "}") String extensionAllowedOrigins,
        Environment environment
    ) {
        List<String> allowedOriginPatterns = Stream.concat(
                splitOrigins(frontendAllowedOrigins).stream(),
                splitOrigins(extensionAllowedOrigins).stream()
            )
            .toList();
        validateExtensionWildcard(allowedOriginPatterns, environment);

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static List<String> splitOrigins(String origins) {
        if (origins == null || origins.isBlank()) {
            return List.of();
        }

        return Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList();
    }

    private static void validateExtensionWildcard(List<String> allowedOriginPatterns, Environment environment) {
        boolean protectedProfile = Arrays.stream(environment.getActiveProfiles())
            .map(String::toLowerCase)
            .anyMatch(PROTECTED_PROFILES::contains);
        if (!protectedProfile) {
            return;
        }

        if (allowedOriginPatterns.contains(DEFAULT_EXTENSION_ALLOWED_ORIGINS)) {
            throw new IllegalStateException(
                "app.cors.extension-allowed-origins must not contain chrome-extension://* in prod or staging."
            );
        }
    }

    /**
     * 환경 변수가 없을 때도 애플리케이션 context가 뜨도록 로컬용 placeholder registration을 구성합니다.
     */
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
        @Value("${spring.security.oauth2.client.registration.github.client-id:local-client-id}") String clientId,
        @Value("${spring.security.oauth2.client.registration.github.client-secret:local-client-secret}") String clientSecret
    ) {
        return new InMemoryClientRegistrationRepository(
            CommonOAuth2Provider.GITHUB
                .getBuilder("github")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("read:user", "user:email")
                .build()
        );
    }
}
