package com.aquilabuddy.global.github;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * GitHub REST API 호출에 사용할 공통 WebClient wrapper입니다.
 */
@Component
public class GitHubApiClient {

    private final WebClient webClient;

    public GitHubApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.github.com")
            .build();
    }

    /**
     * GitHub baseUrl이 설정된 WebClient 인스턴스를 반환합니다.
     */
    public WebClient webClient() {
        return webClient;
    }
}
