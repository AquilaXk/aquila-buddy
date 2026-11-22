package com.aquilabuddy.domain.issue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class IssueFingerprintSimHashTest {

    @Test
    void suppressesSameRepositoryAndIssueNumberDuplicatesRegardlessOfCase() {
        RecommendedIssue first = new RecommendedIssue(
            "id-1",
            "Spring-Projects/Spring-Boot",
            42,
            "Short title",
            "Short summary",
            0.95,
            List.of("good first issue"),
            "OPEN",
            "원본 후보입니다."
        );
        RecommendedIssue duplicate = new RecommendedIssue(
            "id-2",
            " spring-projects/spring-boot ",
            42,
            "Short title",
            "Short summary",
            0.80,
            List.of("bug"),
            "OPEN",
            "중복 후보입니다."
        );

        assertThat(IssueFingerprintSimHash.deduplicateCandidates(List.of(first, duplicate)))
            .containsExactly(first);
    }

    @Test
    void suppressesLongNearDuplicateIssuesAcrossMirrorsUsingSimHash() {
        RecommendedIssue original = new RecommendedIssue(
            "orig",
            "org-a/spring-toolkit",
            101,
            "Fix NullPointerException when parsing configuration properties in YAML loader",
            "Configuration properties parser throws unexpected NullPointerException on empty nested YAML keys during startup validation.",
            0.92,
            List.of("bug", "good first issue"),
            "OPEN",
            "설정 파서 이슈입니다."
        );
        RecommendedIssue mirrored = new RecommendedIssue(
            "mirror",
            "org-b/spring-toolkit-mirror",
            88,
            "Fix NullPointerException when parsing configuration properties in YAML loader!",
            "Configuration properties parser throws unexpected NullPointerException on empty nested YAML keys during startup checks.",
            0.89,
            List.of("good first issue"),
            "OPEN",
            "미러 저장소 중복 이슈입니다."
        );
        RecommendedIssue distinct = new RecommendedIssue(
            "distinct",
            "org-c/react-router",
            55,
            "Improve TypeScript inference for nested route loader parameters and search params",
            "Type inference drops query string parameter narrowing when route loaders compose multiple asynchronous middlewares.",
            0.85,
            List.of("typescript"),
            "OPEN",
            "다른 도메인 이슈입니다."
        );

        long fpOriginal = IssueFingerprintSimHash.computeFingerprint(original.title(), original.summary());
        long fpMirrored = IssueFingerprintSimHash.computeFingerprint(mirrored.title(), mirrored.summary());
        long fpDistinct = IssueFingerprintSimHash.computeFingerprint(distinct.title(), distinct.summary());

        assertThat(IssueFingerprintSimHash.hammingDistance(fpOriginal, fpMirrored)).isLessThanOrEqualTo(4);
        assertThat(IssueFingerprintSimHash.hammingDistance(fpOriginal, fpDistinct)).isGreaterThan(4);
        assertThat(IssueFingerprintSimHash.deduplicateCandidates(List.of(original, mirrored, distinct)))
            .containsExactly(original, distinct);
    }

    @Test
    void preservesShortTemplateIssuesAcrossDistinctRepositories() {
        RecommendedIssue first = new RecommendedIssue(
            "low",
            "spring-projects/spring-boot",
            101,
            "Improve contributor guide",
            "Good first issue 후보입니다.",
            0.9,
            List.of("good first issue"),
            "OPEN",
            "GitHub Search 후보입니다."
        );
        RecommendedIssue second = new RecommendedIssue(
            "high",
            "vercel/next.js",
            2,
            "Improve contributor guide",
            "Good first issue 후보입니다.",
            0.9,
            List.of("good first issue"),
            "OPEN",
            "GitHub Search 후보입니다."
        );

        assertThat(IssueFingerprintSimHash.deduplicateCandidates(List.of(first, second)))
            .containsExactly(first, second);
    }
}
