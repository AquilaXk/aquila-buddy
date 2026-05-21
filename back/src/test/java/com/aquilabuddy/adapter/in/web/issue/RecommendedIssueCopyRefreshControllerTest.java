package com.aquilabuddy.adapter.in.web.issue;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.application.port.in.issue.RefreshRecommendedIssueCopyUseCase;
import com.aquilabuddy.domain.issue.RecommendedIssueCopyRefreshResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendedIssueCopyRefreshController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "app.recommendation.copy-refresh.operation-token=test-operation-token",
    "app.recommendation.copy-refresh.max-limit=100"
})
class RecommendedIssueCopyRefreshControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshRecommendedIssueCopyUseCase refreshRecommendedIssueCopyUseCase;

    @Test
    void refreshesRecommendedIssueCopyInDryRunModeByDefault() throws Exception {
        given(refreshRecommendedIssueCopyUseCase.refreshRecommendedIssueCopy(25, true))
            .willReturn(new RecommendedIssueCopyRefreshResult(
                "recommended-issue-copy",
                25,
                2,
                0,
                8,
                0,
                true,
                List.of(new RecommendedIssueCopyRefreshResult.Sample(
                    "issue-1",
                    "owner/repo",
                    10,
                    "copy_output_version=legacy"
                )),
                List.of()
            ));

        mockMvc.perform(post("/api/v1/issues/copy-refresh")
                .header("X-Aquila-Operation-Token", "test-operation-token")
                .header("X-Aquila-Operator", "release-manager")
                .param("limit", "25"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.target").value("recommended-issue-copy"))
            .andExpect(jsonPath("$.data.dryRun").value(true))
            .andExpect(jsonPath("$.data.candidates").value(2))
            .andExpect(jsonPath("$.data.samples[0].issueId").value("issue-1"));

        then(refreshRecommendedIssueCopyUseCase).should().refreshRecommendedIssueCopy(25, true);
    }

    @Test
    void allowsExplicitLiveRefreshAndCapsLimit() throws Exception {
        given(refreshRecommendedIssueCopyUseCase.refreshRecommendedIssueCopy(100, false))
            .willReturn(new RecommendedIssueCopyRefreshResult(
                "recommended-issue-copy",
                100,
                3,
                3,
                0,
                0,
                false,
                List.of(),
                List.of()
            ));

        mockMvc.perform(post("/api/v1/issues/copy-refresh")
                .header("X-Aquila-Operation-Token", "test-operation-token")
                .param("limit", "1000")
                .param("dryRun", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requestedLimit").value(100))
            .andExpect(jsonPath("$.data.updated").value(3))
            .andExpect(jsonPath("$.data.dryRun").value(false));

        then(refreshRecommendedIssueCopyUseCase).should().refreshRecommendedIssueCopy(100, false);
    }

    @Test
    void rejectsRefreshWhenOperationTokenIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/issues/copy-refresh").param("limit", "25"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(refreshRecommendedIssueCopyUseCase);
    }
}
