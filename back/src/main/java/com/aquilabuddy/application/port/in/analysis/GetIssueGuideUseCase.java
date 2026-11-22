package com.aquilabuddy.application.port.in.analysis;

import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideStreamChunk;
import com.aquilabuddy.domain.analysis.IssueGuideWorkflow;
import java.util.function.Consumer;

/**
 * [Incoming Port] 이슈 작업 가이드를 조회하는 유스케이스 계약입니다.
 */
public interface GetIssueGuideUseCase {

    /**
     * 이슈 식별자를 기준으로 구현 가이드와 영향 범위를 조회합니다.
     */
    IssueGuide findByIssueId(String issueId);

    IssueGuideWorkflow findWorkflowByIssueId(String issueId);

    /**
     * 이슈 가이드를 조회·생성하면서 실시간 SSE 스트리밍 청크를 전달합니다.
     */
    default void streamByIssueId(String issueId, Consumer<IssueGuideStreamChunk> consumer) {
        consumer.accept(IssueGuideStreamChunk.analyzing("저장소 코드 맥락 분석 중..."));
        IssueGuide guide = findByIssueId(issueId);
        consumer.accept(IssueGuideStreamChunk.files(guide.filePaths()));
        if (guide.guideline() != null && !guide.guideline().isBlank()) {
            consumer.accept(IssueGuideStreamChunk.guideline(guide.guideline()));
        }
        if (guide.pseudoCode() != null && !guide.pseudoCode().isBlank()) {
            consumer.accept(IssueGuideStreamChunk.pseudoCode(guide.pseudoCode()));
        }
        consumer.accept(IssueGuideStreamChunk.sideEffects(guide.sideEffects()));
        consumer.accept(IssueGuideStreamChunk.complete(guide));
    }
}
