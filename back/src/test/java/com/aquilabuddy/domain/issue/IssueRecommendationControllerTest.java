package com.aquilabuddy.domain.issue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 추천 이슈 API가 기존 JSON 계약을 유지하는지 검증하는 통합 테스트입니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IssueRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsRecommendedIssues() throws Exception {
        mockMvc.perform(get("/api/v1/issues/recommend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value("demo-1"))
            .andExpect(jsonPath("$.data[0].repoFullName").value("vercel/next.js"))
            .andExpect(jsonPath("$.data[0].issueNumber").value(1))
            .andExpect(jsonPath("$.data[0].status").value("OPEN"))
            .andExpect(jsonPath("$.data[0].labels[0]").value("good first issue"))
            .andExpect(jsonPath("$.data[0].reason").isNotEmpty());
    }
}
