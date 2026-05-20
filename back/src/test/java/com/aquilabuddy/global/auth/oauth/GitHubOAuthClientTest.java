package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubOAuthClientTest {

    @Test
    void exchangesCodeAndLoadsGithubUser() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubOAuthClient client = new GitHubOAuthClient(
            builder,
            "client-id",
            "client-secret",
            "https://github.com/login/oauth/access_token",
            "https://api.github.com/user"
        );

        server.expect(requestTo("https://github.com/login/oauth/access_token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=client-id")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=client-secret")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("code=code-123")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "redirect_uri=https%3A%2F%2Fbuddy.aquilaxk.site%2Flogin%2Foauth2%2Fcode%2Fgithub"
            )))
            .andRespond(withSuccess(
                """
                    {"access_token":"gho_token","token_type":"bearer","scope":"read:user,user:email"}
                    """,
                MediaType.APPLICATION_JSON
            ));
        server.expect(requestTo("https://api.github.com/user"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gho_token"))
            .andRespond(withSuccess(
                """
                    {"id":12345,"login":"aquila","name":"Aquila","email":"aquila@example.com"}
                    """,
                MediaType.APPLICATION_JSON
            ));

        GitHubOAuthAccessToken token = client.exchangeCode(
            "code-123",
            "https://buddy.aquilaxk.site/login/oauth2/code/github"
        );
        GitHubOAuthUser user = client.loadUser(token.accessToken());

        assertThat(token.accessToken()).isEqualTo("gho_token");
        assertThat(user.id()).isEqualTo(12345L);
        assertThat(user.login()).isEqualTo("aquila");
        server.verify();
    }
}
