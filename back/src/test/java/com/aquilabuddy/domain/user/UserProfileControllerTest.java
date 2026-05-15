package com.aquilabuddy.domain.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsCurrentUserProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.githubId").value(10001))
            .andExpect(jsonPath("$.data.login").value("aquila-dev"))
            .andExpect(jsonPath("$.data.name").value("Aquila Developer"))
            .andExpect(jsonPath("$.data.email").value("aquila@example.com"))
            .andExpect(jsonPath("$.data.primaryLanguages[0]").value("Java"))
            .andExpect(jsonPath("$.data.topics[0]").value("spring"))
            .andExpect(jsonPath("$.data.profileReady").value(true));
    }
}
