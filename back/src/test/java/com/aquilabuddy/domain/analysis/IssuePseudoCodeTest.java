package com.aquilabuddy.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 이슈 의사코드 projection이 전체 guide에서 필요한 필드만 분리하는지 검증합니다.
 */
class IssuePseudoCodeTest {

    @Test
    void createsPseudoCodeProjectionFromIssueGuide() {
        IssueGuide guide = new IssueGuide(
            "demo-1",
            "Add validation",
            "if input is blank:\n  return bad request",
            List.of("Validation error response changes")
        );

        IssuePseudoCode pseudoCode = IssuePseudoCode.from(guide);

        assertThat(pseudoCode.issueKey()).isEqualTo("demo-1");
        assertThat(pseudoCode.pseudoCode()).isEqualTo("if input is blank:\n  return bad request");
    }
}
