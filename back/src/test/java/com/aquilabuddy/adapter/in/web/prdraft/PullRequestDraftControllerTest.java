package com.aquilabuddy.adapter.in.web.prdraft;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PR 초안 생성 API가 입력값을 받아 기존 응답 envelope로 초안을 반환하는지 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PullRequestDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
            .andExpect(jsonPath("$.data.title").value("docs: spring-projects/spring-boot#2 변경 초안"))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("spring-projects/spring-boot #2")))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("main -> feat/validation-docs")))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("Add validation example")));
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
}
