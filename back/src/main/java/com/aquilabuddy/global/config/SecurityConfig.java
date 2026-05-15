package com.aquilabuddy.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 기본 필터 체인을 구성합니다.
 */
@Configuration
public class SecurityConfig {

    /**
     * 초기 개발 단계에서는 모든 API 접근을 허용하고, 인증 어댑터 도입 시 보호 경로를 좁힙니다.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/api/v1/health",
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                )
                .permitAll()
                .anyRequest()
                .permitAll()
            )
            .build();
    }
}
