package com.aquilabuddy.application.service.issue;

import com.aquilabuddy.application.port.in.issue.RecommendIssuesUseCase;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecommendedIssueService implements RecommendIssuesUseCase {

    @Override
    public List<RecommendedIssue> recommend() {
        return List.of(
            new RecommendedIssue(
                "demo-1",
                "vercel/next.js",
                1,
                "Improve contributor-facing documentation",
                "문서 중심 첫 기여 흐름을 확인하기 좋은 이슈입니다.",
                0.91,
                List.of("good first issue", "documentation"),
                "OPEN",
                "프론트엔드와 문서 작업으로 첫 PR 흐름을 작게 검증할 수 있습니다."
            ),
            new RecommendedIssue(
                "demo-2",
                "spring-projects/spring-boot",
                2,
                "Refine validation error example",
                "Java/Spring 관심사와 맞는 validation 예시 개선 이슈입니다.",
                0.87,
                List.of("good first issue", "validation"),
                "OPEN",
                "현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다."
            )
        );
    }
}
