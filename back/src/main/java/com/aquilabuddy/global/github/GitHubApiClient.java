package com.aquilabuddy.global.github;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GitHubApiClient {

    private final WebClient webClient;

    public GitHubApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.github.com")
            .build();
    }

    public WebClient webClient() {
        return webClient;
    }
}
