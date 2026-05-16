package com.aquilabuddy.global.config;

import java.util.List;
import com.aquilabuddy.global.auth.jwt.JwtAuthenticationFilter;
import com.aquilabuddy.global.auth.jwt.JwtProperties;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.auth.oauth.CustomOAuth2UserService;
import com.aquilabuddy.global.auth.oauth.OAuth2FailureHandler;
import com.aquilabuddy.global.auth.oauth.OAuth2SuccessHandler;
import com.aquilabuddy.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
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

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oauth2SuccessHandler;
    private final OAuth2FailureHandler oauth2FailureHandler;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
        CustomOAuth2UserService customOAuth2UserService,
        OAuth2SuccessHandler oauth2SuccessHandler,
        OAuth2FailureHandler oauth2FailureHandler,
        JwtProvider jwtProvider,
        ObjectMapper objectMapper
    ) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.oauth2FailureHandler = oauth2FailureHandler;
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
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
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oauth2SuccessHandler)
                .failureHandler(oauth2FailureHandler)
            )
            .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error("로그인이 필요합니다.")));
            }))
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    /**
     * 로컬 프론트엔드 개발 서버에서 백엔드 API를 직접 호출할 수 있도록 API CORS를 허용합니다.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:3000",
            "http://localhost:3001",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:3001",
            "chrome-extension://*"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
