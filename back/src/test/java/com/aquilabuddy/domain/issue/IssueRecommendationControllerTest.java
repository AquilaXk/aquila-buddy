package com.aquilabuddy.domain.issue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.support.SecurityContextTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 추천 이슈 API가 기존 JSON 계약을 유지하는지 검증하는 통합 테스트입니다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
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
    void returnsCollectedIssueCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/issues"))
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
    void returnsIssueDetailByInternalId() throws Exception {
        mockMvc.perform(get("/api/v1/issues/{id}", "demo-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value("demo-1"))
            .andExpect(jsonPath("$.data.repoFullName").value("vercel/next.js"))
            .andExpect(jsonPath("$.data.issueNumber").value(1))
            .andExpect(jsonPath("$.data.labels[0]").value("good first issue"));
    }

    @Test
    void returnsNotFoundWhenIssueDetailDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/issues/{id}", "missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("fail"))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.message").value("추천 이슈를 찾을 수 없습니다."));
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
            .andExpect(jsonPath("$.data[0].isAnalyzed").value(true))
            .andExpect(jsonPath("$.data[0].analysisResultId").value("demo-1"));
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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextTestUtils.clear();
    }

    @Test
    void returnsNotFoundWhenLookupTargetDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/issues/lookup")
                .param("repo", "missing/repo")
                .param("number", "404"))
            .andExpect(status().isNotFound());
    }

    @Test
    void managesPerUserBookmarksAndIsolatesBetweenUsers() throws Exception {
        SecurityContextTestUtils.setCurrentGithubUser(10001L);

        mockMvc.perform(post("/api/v1/issues/{issueId}/bookmark", "demo-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value("demo-1"))
            .andExpect(jsonPath("$.data.bookmarked").value(true));

        mockMvc.perform(get("/api/v1/issues/bookmarks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("demo-1"))
            .andExpect(jsonPath("$.data[0].bookmarked").value(true));

        mockMvc.perform(get("/api/v1/issues/recommend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id=='demo-1')].bookmarked").value(org.hamcrest.Matchers.contains(true)))
            .andExpect(jsonPath("$.data[?(@.id=='demo-2')].bookmarked").value(org.hamcrest.Matchers.contains(false)));

        mockMvc.perform(get("/api/v1/issues/recommend/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("demo-1"))
            .andExpect(jsonPath("$.data[0].bookmarked").value(true))
            .andExpect(jsonPath("$.data[0].isAnalyzed").value(true));

        // Different user must not see user 10001's bookmarks or analyzed flags
        SecurityContextTestUtils.setCurrentGithubUser(20002L);

        mockMvc.perform(get("/api/v1/issues/bookmarks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/v1/issues/recommend/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("demo-1"))
            .andExpect(jsonPath("$.data[0].bookmarked").value(false))
            .andExpect(jsonPath("$.data[0].isAnalyzed").value(false));

        // User 10001 unbookmarks demo-1 cleanly
        SecurityContextTestUtils.setCurrentGithubUser(10001L);
        mockMvc.perform(delete("/api/v1/issues/{issueId}/bookmark", "demo-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bookmarked").value(false));

        mockMvc.perform(get("/api/v1/issues/bookmarks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsBookmarkOperationsForMissingIssueOrUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/issues/bookmarks"))
            .andExpect(status().isUnauthorized());

        SecurityContextTestUtils.setCurrentGithubUser(10001L);
        mockMvc.perform(post("/api/v1/issues/{issueId}/bookmark", "missing-issue"))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/issues/{issueId}/bookmark", "missing-issue"))
            .andExpect(status().isNotFound());
    }
}
