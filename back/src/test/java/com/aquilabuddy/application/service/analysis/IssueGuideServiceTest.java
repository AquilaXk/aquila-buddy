package com.aquilabuddy.application.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssuePseudoCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 이슈 가이드 application service가 저장된 guide를 목적별 projection으로 변환하는지 검증합니다.
 */
class IssueGuideServiceTest {

    @Test
    void findsPseudoCodeByIssueId() {
        IssueGuideService service = new IssueGuideService(new StubIssueGuidePort());

        IssuePseudoCode pseudoCode = service.findPseudoCodeByIssueId("demo-1");

        assertThat(pseudoCode.issueKey()).isEqualTo("demo-1");
        assertThat(pseudoCode.pseudoCode()).isEqualTo("if request is invalid:\n  return bad request");
    }

    private static class StubIssueGuidePort implements LoadIssueGuidePort {

        @Override
        public IssueGuide loadIssueGuide(String issueId) {
            return new IssueGuide(
                issueId,
                "Validate request fields",
                "if request is invalid:\n  return bad request",
                List.of("Clients must handle 400 responses")
            );
        }
    }
}
