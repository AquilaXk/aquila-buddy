package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.adapter.out.persistence.PostgresRlsSessionBinder;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.UserIssueState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({UserRecommendedIssuePersistenceAdapter.class, PostgresRlsSessionBinder.class})
class UserRecommendedIssuePersistenceAdapterTest {

    @Autowired
    private UserRecommendedIssuePersistenceAdapter adapter;

    @Autowired
    private UserRecommendedIssueJpaRepository userIssueRepository;

    @Autowired
    private RecommendedIssueJpaRepository issueRepository;

    @BeforeEach
    void setUp() {
        userIssueRepository.deleteAll();
        issueRepository.deleteAll();
        issueRepository.save(new RecommendedIssueJpaEntity(
            "issue-a",
            "spring-projects/spring-boot",
            10,
            "Issue A",
            "Summary A",
            0.9,
            List.of("good first issue"),
            "OPEN",
            "Reason A"
        ));
        issueRepository.save(new RecommendedIssueJpaEntity(
            "issue-b",
            "vercel/next.js",
            20,
            "Issue B",
            "Summary B",
            0.8,
            List.of("good first issue"),
            "OPEN",
            "Reason B"
        ));
    }

    @Test
    void isolatesBookmarksAndAnalysisStatePerUserAndOrdersBookmarksNewestFirst() {
        Instant t1 = Instant.parse("2026-11-21T01:00:00Z");
        Instant t2 = Instant.parse("2026-11-21T02:00:00Z");

        adapter.setBookmarked(10001L, "issue-a", true, t1);
        adapter.setBookmarked(10001L, "issue-b", true, t2);
        adapter.markAnalyzed(10001L, "issue-a", t2);
        adapter.setBookmarked(20002L, "issue-a", true, t1);

        List<RecommendedIssue> user1Bookmarks = adapter.loadBookmarkedIssues(10001L);
        assertThat(user1Bookmarks)
            .extracting(RecommendedIssue::id)
            .containsExactly("issue-b", "issue-a");
        assertThat(user1Bookmarks).allMatch(RecommendedIssue::bookmarked);

        // Unbookmarking preserves analyzed state for user 10001
        adapter.setBookmarked(10001L, "issue-a", false, t2.plusSeconds(10));
        Map<String, UserIssueState> user1States = adapter.findStatesByUserAndIssueIds(10001L, List.of("issue-a", "issue-b"));
        assertThat(user1States.get("issue-a").bookmarked()).isFalse();
        assertThat(user1States.get("issue-a").analyzed()).isTrue();
        assertThat(user1States.get("issue-b").bookmarked()).isTrue();
        assertThat(user1States.get("issue-b").analyzed()).isFalse();

        // User 20002 state remains isolated
        Map<String, UserIssueState> user2States = adapter.findStatesByUserAndIssueIds(20002L, List.of("issue-a", "issue-b"));
        assertThat(user2States.get("issue-a").bookmarked()).isTrue();
        assertThat(user2States.get("issue-a").analyzed()).isFalse();
        assertThat(user2States).doesNotContainKey("issue-b");
    }
}
