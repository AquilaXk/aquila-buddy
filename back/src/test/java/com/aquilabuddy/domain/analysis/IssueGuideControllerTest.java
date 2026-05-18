package com.aquilabuddy.domain.analysis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 이슈 가이드 API가 기존 JSON 계약을 유지하는지 검증하는 통합 테스트입니다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class IssueGuideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsIssueGuide() throws Exception {
        mockMvc.perform(get("/api/v1/issues/demo-1/guide"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.issueKey").value("demo-1"))
            .andExpect(jsonPath("$.data.filePaths[0]").value("front/src/app/issues/[id]/page.tsx"))
            .andExpect(jsonPath("$.data.guideline").isNotEmpty())
            .andExpect(jsonPath("$.data.pseudoCode").isNotEmpty())
            .andExpect(jsonPath("$.data.sideEffects[0]").isNotEmpty());
    }

    @Test
    void returnsIssueGuideWorkflow() throws Exception {
        mockMvc.perform(get("/api/v1/issues/demo-1/guide/workflow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.issueKey").value("demo-1"))
            .andExpect(jsonPath("$.data.readyCount").value(4))
            .andExpect(jsonPath("$.data.steps[0].key").value("guide-review"))
            .andExpect(jsonPath("$.data.steps[0].status").value("READY"))
            .andExpect(jsonPath("$.data.steps[3].key").value("pr-draft"));
    }

    @Test
    void returnsIssuePseudoCode() throws Exception {
        mockMvc.perform(get("/api/v1/issues/demo-1/pseudo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.issueKey").value("demo-1"))
            .andExpect(jsonPath("$.data.filePaths[0]").value("front/src/app/issues/[id]/page.tsx"))
            .andExpect(jsonPath("$.data.pseudoCode").isNotEmpty());
    }
}
