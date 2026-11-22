package com.aquilabuddy.domain.analysis;

import java.util.List;

/**
 * 이슈 가이드 실시간 SSE 스트리밍 중 방출되는 단계별 청크/완료 이벤트 모델입니다.
 */
public record IssueGuideStreamChunk(
    Step step,
    String message,
    String chunk,
    List<String> filePaths,
    List<String> sideEffects,
    IssueGuide completedGuide
) {

    public enum Step {
        ANALYZING,
        FILES,
        GUIDELINE,
        PSEUDO_CODE,
        SIDE_EFFECTS,
        COMPLETE
    }

    public static IssueGuideStreamChunk analyzing(String message) {
        return new IssueGuideStreamChunk(Step.ANALYZING, message, null, null, null, null);
    }

    public static IssueGuideStreamChunk files(List<String> filePaths) {
        return new IssueGuideStreamChunk(
            Step.FILES,
            null,
            null,
            filePaths == null ? List.of() : List.copyOf(filePaths),
            null,
            null
        );
    }

    public static IssueGuideStreamChunk guideline(String chunk) {
        return new IssueGuideStreamChunk(Step.GUIDELINE, null, chunk, null, null, null);
    }

    public static IssueGuideStreamChunk pseudoCode(String chunk) {
        return new IssueGuideStreamChunk(Step.PSEUDO_CODE, null, chunk, null, null, null);
    }

    public static IssueGuideStreamChunk sideEffectsChunk(String chunk) {
        return new IssueGuideStreamChunk(Step.SIDE_EFFECTS, null, chunk, null, null, null);
    }

    public static IssueGuideStreamChunk sideEffects(List<String> sideEffects) {
        return new IssueGuideStreamChunk(
            Step.SIDE_EFFECTS,
            null,
            null,
            null,
            sideEffects == null ? List.of() : List.copyOf(sideEffects),
            null
        );
    }

    public static IssueGuideStreamChunk complete(IssueGuide guide) {
        return new IssueGuideStreamChunk(Step.COMPLETE, null, null, null, null, guide);
    }
}
