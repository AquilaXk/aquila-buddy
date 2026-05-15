package com.aquilabuddy.adapter.in.web.prdraft;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.aquilabuddy.adapter.out.persistence.prdraft.PullRequestDraftJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * PR 초안 API가 저장된 초안을 생성하고 목록/상세로 재조회하는지 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PullRequestDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PullRequestDraftJpaRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void createsPullRequestDraft() throws Exception {
        mockMvc.perform(post("/api/v1/pr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "upstreamRepo": "spring-projects/spring-boot",
                      "githubIssueNumber": 2,
                      "baseBranch": "main",
                      "headBranch": "feat/validation-docs",
                      "diffContent": "diff --git a/docs.adoc b/docs.adoc\\n+Add validation example"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.upstreamRepo").value("spring-projects/spring-boot"))
            .andExpect(jsonPath("$.data.githubIssueNumber").value(2))
            .andExpect(jsonPath("$.data.title").value("docs: spring-projects/spring-boot#2 변경 초안"))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("spring-projects/spring-boot #2")))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("main -> feat/validation-docs")))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("Add validation example")));
    }

    @Test
    void listsPullRequestDrafts() throws Exception {
        String id = createDraft();

        mockMvc.perform(get("/api/v1/pr"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(id))
            .andExpect(jsonPath("$.data[0].title").value("docs: spring-projects/spring-boot#2 변경 초안"));
    }

    @Test
    void returnsPullRequestDraftDetail() throws Exception {
        String id = createDraft();

        mockMvc.perform(get("/api/v1/pr/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(id))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("main -> feat/validation-docs")));
    }

    @Test
    void returnsNotFoundForMissingDraft() throws Exception {
        mockMvc.perform(get("/api/v1/pr/{id}", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankRepository() throws Exception {
        mockMvc.perform(post("/api/v1/pr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "upstreamRepo": "",
                      "githubIssueNumber": 2,
                      "baseBranch": "main",
                      "headBranch": "feat/validation-docs",
                      "diffContent": "diff --git a/docs.adoc b/docs.adoc"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("upstreamRepo")));
    }

    private String createDraft() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/pr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "upstreamRepo": "spring-projects/spring-boot",
                      "githubIssueNumber": 2,
                      "baseBranch": "main",
                      "headBranch": "feat/validation-docs",
                      "diffContent": "diff --git a/docs.adoc b/docs.adoc\\n+Add validation example"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }
}
