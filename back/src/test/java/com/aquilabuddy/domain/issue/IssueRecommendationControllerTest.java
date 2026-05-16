package com.aquilabuddy.domain.issue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void returnsRecommendationHistory() throws Exception {
        mockMvc.perform(get("/api/v1/issues/recommend/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value("demo-1"))
            .andExpect(jsonPath("$.data[0].repoFullName").value("vercel/next.js"))
            .andExpect(jsonPath("$.data[0].issueNumber").value(1))
            .andExpect(jsonPath("$.data[0].status").value("OPEN"))
            .andExpect(jsonPath("$.data[0].labels[0]").value("good first issue"))
            .andExpect(jsonPath("$.data[0].isAnalyzed").value(false))
            .andExpect(jsonPath("$.data[0].analysisResultId").doesNotExist());
    }

    @Test
    void crawlsIssuesBySearchQuery() throws Exception {
        mockMvc.perform(post("/api/v1/issues/crawl/search")
                .param("q", "language:java label:\"good first issue\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void rejectsBlankIssueSearchQuery() throws Exception {
        mockMvc.perform(post("/api/v1/issues/crawl/search")
                .param("q", ""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("q")));
    }

    @Test
    void returnsIssueLookupByRepositoryAndIssueNumber() throws Exception {
        mockMvc.perform(get("/api/v1/issues/lookup")
                .param("repo", "vercel/next.js")
                .param("number", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value("demo-1"))
            .andExpect(jsonPath("$.data.repoFullName").value("vercel/next.js"))
            .andExpect(jsonPath("$.data.issueNumber").value(1))
            .andExpect(jsonPath("$.data.labels[0]").value("good first issue"));
    }

    @Test
    void returnsNotFoundWhenLookupTargetDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/issues/lookup")
                .param("repo", "missing/repo")
                .param("number", "404"))
            .andExpect(status().isNotFound());
    }
}
