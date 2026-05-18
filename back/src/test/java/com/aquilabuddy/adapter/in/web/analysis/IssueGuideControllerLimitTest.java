package com.aquilabuddy.adapter.in.web.analysis;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.application.port.in.analysis.GetIssuePseudoCodeUseCase;
import com.aquilabuddy.domain.analysis.IssueGuideGenerationDailyLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IssueGuideController.class)
@AutoConfigureMockMvc(addFilters = false)
class IssueGuideControllerLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetIssueGuideUseCase getIssueGuideUseCase;

    @MockitoBean
    private GetIssuePseudoCodeUseCase getIssuePseudoCodeUseCase;

    @Test
    void returnsTooManyRequestsEnvelopeWhenDailyAnalysisLimitIsExceeded() throws Exception {
        given(getIssueGuideUseCase.findByIssueId("demo-1"))
            .willThrow(new IssueGuideGenerationDailyLimitExceededException(10001L));

        mockMvc.perform(get("/api/v1/issues/demo-1/guide"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("fail"))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.message")
                .value("이슈 분석 일일 생성 제한을 초과했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
