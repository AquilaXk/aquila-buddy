package com.aquilabuddy.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class IssueGuideWorkflowTest {

    @Test
    void createsWorkflowStepsFromIssueGuide() {
        IssueGuide guide = new IssueGuide(
            "demo-1",
            "Controller 응답 계약을 확인하고 누락된 검증을 추가합니다.",
            "1. Controller test를 추가한다.\n2. Service 입력값을 검증한다.",
            List.of("기존 API 응답 필드를 유지해야 합니다.")
        );

        IssueGuideWorkflow workflow = IssueGuideWorkflow.from(guide);

        assertThat(workflow.issueKey()).isEqualTo("demo-1");
        assertThat(workflow.readyCount()).isEqualTo(4);
        assertThat(workflow.steps())
            .extracting(IssueGuideWorkflowStep::key)
            .containsExactly("guide-review", "implementation-plan", "side-effect-check", "pr-draft");
        assertThat(workflow.steps())
            .extracting(IssueGuideWorkflowStep::status)
            .containsOnly(IssueGuideWorkflowStepStatus.READY);
        assertThat(workflow.steps().get(0).detail()).contains("Controller 응답 계약");
        assertThat(workflow.steps().get(1).detail()).contains("Controller test");
        assertThat(workflow.steps().get(2).detail()).contains("기존 API 응답 필드");
    }

    @Test
    void keepsPrDraftWaitingWhenGuideIsIncomplete() {
        IssueGuide guide = new IssueGuide(
            "demo-2",
            "Controller 응답 계약을 확인합니다.",
            "",
            List.of()
        );

        IssueGuideWorkflow workflow = IssueGuideWorkflow.from(guide);

        assertThat(workflow.readyCount()).isEqualTo(1);
        assertThat(workflow.steps())
            .extracting(IssueGuideWorkflowStep::status)
            .containsExactly(
                IssueGuideWorkflowStepStatus.READY,
                IssueGuideWorkflowStepStatus.WAITING,
                IssueGuideWorkflowStepStatus.WAITING,
                IssueGuideWorkflowStepStatus.WAITING
            );
    }
}
