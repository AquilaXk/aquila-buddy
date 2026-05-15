package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 추천 이슈 persistence adapter가 JPA entity를 도메인 모델로 변환하고 정렬하는지 검증합니다.
 */
@DataJpaTest
@Import(RecommendedIssuePersistenceAdapter.class)
class RecommendedIssuePersistenceAdapterTest {

    @Autowired
    private RecommendedIssuePersistenceAdapter adapter;

    @Autowired
    private RecommendedIssueJpaRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void loadsRecommendedIssuesOrderedByScoreDescending() {
        repository.save(new RecommendedIssueJpaEntity(
            "low-score",
            "spring-projects/spring-boot",
            2,
            "Refine validation error example",
            "Java/Spring 관심사와 맞는 validation 예시 개선 이슈입니다.",
            0.87,
            List.of("good first issue", "validation"),
            "OPEN",
            "현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다."
        ));
        repository.save(new RecommendedIssueJpaEntity(
            "high-score",
            "vercel/next.js",
            1,
            "Improve contributor-facing documentation",
            "문서 중심 첫 기여 흐름을 확인하기 좋은 이슈입니다.",
            0.91,
            List.of("good first issue", "documentation"),
            "OPEN",
            "프론트엔드와 문서 작업으로 첫 PR 흐름을 작게 검증할 수 있습니다."
        ));

        List<RecommendedIssue> issues = adapter.loadRecommendedIssues();

        assertThat(issues)
            .extracting(RecommendedIssue::id)
            .containsExactly("high-score", "low-score");
        assertThat(issues.getFirst().repoFullName()).isEqualTo("vercel/next.js");
        assertThat(issues.getFirst().issueNumber()).isEqualTo(1);
        assertThat(issues.getFirst().labels()).containsExactly("good first issue", "documentation");
        assertThat(issues.getFirst().score()).isEqualTo(0.91);
        assertThat(issues.getFirst().status()).isEqualTo("OPEN");
    }

    @Test
    void savesRecommendedIssuesForLaterFallback() {
        RecommendedIssue searched = new RecommendedIssue(
            "github-search",
            "spring-projects/spring-boot",
            101,
            "Improve contributor guide",
            "GitHub에서 수집한 Good First Issue 후보입니다.",
            1.0,
            List.of("good first issue", "documentation"),
            "OPEN",
            "GitHub Search 후보입니다."
        );

        adapter.saveRecommendedIssues(List.of(searched));

        List<RecommendedIssue> issues = adapter.loadRecommendedIssues();
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().id()).isEqualTo("github-search");
        assertThat(issues.getFirst().repoFullName()).isEqualTo("spring-projects/spring-boot");
        assertThat(issues.getFirst().issueNumber()).isEqualTo(101);
        assertThat(issues.getFirst().labels()).containsExactly("good first issue", "documentation");
        assertThat(issues.getFirst().reason()).isEqualTo("GitHub Search 후보입니다.");
    }
}
