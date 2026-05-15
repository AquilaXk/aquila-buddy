package com.aquilabuddy.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 외부 HTTP adapter들이 공유할 WebClient builder를 제공합니다.
 */
@Configuration
public class WebClientConfig {

    /**
     * baseUrl과 인증 헤더는 각 outbound adapter에서 필요한 값으로 확장합니다.
     */
    @Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
