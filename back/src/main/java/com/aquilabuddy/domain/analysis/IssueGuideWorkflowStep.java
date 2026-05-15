package com.aquilabuddy.domain.analysis;

/**
 * 이슈 가이드를 실제 작업 순서로 옮기기 위한 단일 workflow 단계입니다.
 */
public record IssueGuideWorkflowStep(
    String key,
    String label,
    String description,
    String detail,
    IssueGuideWorkflowStepStatus status
) {
}
