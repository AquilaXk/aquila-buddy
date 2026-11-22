package com.aquilabuddy.application.port.out.analysis;

import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideStreamChunk;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.function.Consumer;

/**
 * 저장된 guide가 없을 때 추천 이슈 맥락으로 작업 가이드를 생성하는 outbound port입니다.
 */
public interface GenerateIssueGuidePort {

    /**
     * 추천 이슈 정보를 기반으로 guide를 생성합니다.
     */
    IssueGuide generateIssueGuide(RecommendedIssue issue);

    /**
     * 추천 이슈와 관련 저장소 코드 맥락을 함께 사용해 guide를 생성합니다.
     */
    default IssueGuide generateIssueGuide(RecommendedIssue issue, RepositoryCodeContext codeContext) {
        return generateIssueGuide(issue);
    }

    /**
     * 추천 이슈와 저장소 코드 맥락으로 가이드를 스트리밍 생성하고, 섹션 청크를 즉시 전달합니다.
     */
    default IssueGuide streamIssueGuide(
        RecommendedIssue issue,
        RepositoryCodeContext codeContext,
        Consumer<IssueGuideStreamChunk> onChunk
    ) {
        IssueGuide guide = generateIssueGuide(issue, codeContext);
        if (onChunk != null && guide != null) {
            if (guide.guideline() != null && !guide.guideline().isBlank()) {
                onChunk.accept(IssueGuideStreamChunk.guideline(guide.guideline()));
            }
            if (guide.pseudoCode() != null && !guide.pseudoCode().isBlank()) {
                onChunk.accept(IssueGuideStreamChunk.pseudoCode(guide.pseudoCode()));
            }
        }
        return guide;
    }
}
