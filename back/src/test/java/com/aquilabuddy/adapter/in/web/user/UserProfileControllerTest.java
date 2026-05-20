package com.aquilabuddy.adapter.in.web.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.support.SecurityContextTestUtils;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 현재 사용자 프로필 API가 프로필 화면에 필요한 응답 필드를 제공하는지 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.embedding.expected-dimensions=3")
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setCurrentUser() {
        SecurityContextTestUtils.setCurrentGithubUser(10001L);
    }

    @AfterEach
    void clearCurrentUser() {
        SecurityContextTestUtils.clear();
    }

    @Test
    void returnsCurrentUserProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.message").value("success to response"))
            .andExpect(jsonPath("$.data.id").value("10001"))
            .andExpect(jsonPath("$.data.githubId").value(10001))
            .andExpect(jsonPath("$.data.login").value("aquila-dev"))
            .andExpect(jsonPath("$.data.name").value("Aquila Developer"))
            .andExpect(jsonPath("$.data.email").value("aquila@example.com"))
            .andExpect(jsonPath("$.data.primaryLanguages[0]").value("Java"))
            .andExpect(jsonPath("$.data.topics[0]").value("spring"))
            .andExpect(jsonPath("$.data.profileReady").value(true));
    }

    @Test
    void returnsUserProfileByGithubId() throws Exception {
        mockMvc.perform(get("/api/v1/users/{githubId}", "10001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.githubId").value(10001))
            .andExpect(jsonPath("$.data.login").value("aquila-dev"))
            .andExpect(jsonPath("$.data.name").value("Aquila Developer"))
            .andExpect(jsonPath("$.data.primaryLanguages[0]").value("Java"));
    }

    @Test
    void returnsNotFoundWhenUserProfileByGithubIdDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/users/{githubId}", "99999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void updatesCurrentUserProfile() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Aquila Maintainer",
                      "email": "maintainer@example.com",
                      "primaryLanguages": ["Java", "TypeScript"],
                      "topics": ["spring", "oss"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Aquila Maintainer"))
            .andExpect(jsonPath("$.data.email").value("maintainer@example.com"))
            .andExpect(jsonPath("$.data.primaryLanguages[0]").value("Java"))
            .andExpect(jsonPath("$.data.primaryLanguages[1]").value("TypeScript"))
            .andExpect(jsonPath("$.data.topics[0]").value("spring"))
            .andExpect(jsonPath("$.data.topics[1]").value("oss"));

        String storedName = jdbcTemplate.queryForObject(
            "SELECT name FROM user_profiles WHERE github_id = ?",
            String.class,
            10001L
        );

        assertThat(storedName).isEqualTo("Aquila Maintainer");
    }

    @Test
    void updatesCurrentUserProfileWithQueryParameters() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                .param("name", "Aquila Query Maintainer")
                .param("email", "query@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Aquila Query Maintainer"))
            .andExpect(jsonPath("$.data.email").value("query@example.com"))
            .andExpect(jsonPath("$.data.primaryLanguages[0]").value("Java"))
            .andExpect(jsonPath("$.data.topics[0]").value("spring"));
    }

    @Test
    void refreshesCurrentUserProfileVector() throws Exception {
        Instant beforeRefresh = Instant.now();

        mockMvc.perform(post("/api/v1/users/me/vector"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.profileReady").value(true))
            .andExpect(jsonPath("$.data.vectorUpdatedAt").isNotEmpty());

        String storedEmbedding = jdbcTemplate.queryForObject(
            "SELECT profile_embedding FROM user_profiles WHERE github_id = ?",
            String.class,
            10001L
        );
        String storedVectorUpdatedAt = jdbcTemplate.queryForObject(
            "SELECT vector_updated_at FROM user_profiles WHERE github_id = ?",
            String.class,
            10001L
        );
        assertThat(storedEmbedding).isEqualTo("[0.4,0.5,0.6]");
        assertThat(Instant.parse(storedVectorUpdatedAt))
            .isBetween(beforeRefresh, Instant.now());
    }

    @Test
    void rejectsInvalidProfileUpdateRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "",
                      "email": "maintainer@example.com",
                      "primaryLanguages": [],
                      "topics": ["spring"]
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class EmbeddingTestConfiguration {

        @Bean
        EmbeddingClient embeddingClient() {
            return input -> new EmbeddingVector(List.of(0.4, 0.5, 0.6));
        }
    }
}
