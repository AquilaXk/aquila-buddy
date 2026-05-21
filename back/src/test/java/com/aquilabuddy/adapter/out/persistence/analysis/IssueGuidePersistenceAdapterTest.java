package com.aquilabuddy.adapter.out.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.analysis.CachedIssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideOutputContract;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 이슈 가이드 persistence adapter가 JPA 저장 데이터를 도메인 모델로 변환하는지 검증합니다.
 */
@DataJpaTest
@Import(IssueGuidePersistenceAdapter.class)
class IssueGuidePersistenceAdapterTest {

    @Autowired
    private IssueGuidePersistenceAdapter adapter;

    @Autowired
    private IssueGuideJpaRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void loadsIssueGuideByIssueId() {
        repository.save(new IssueGuideJpaEntity(
            "demo-1",
            "변경 범위를 작게 유지하면서 현재 문서 흐름과 실제 첫 기여 절차가 어긋나는 지점을 보완합니다.",
            String.join("\n",
                "1. 상세 화면에서 제공하는 이슈 요약과 라벨을 확인한다.",
                "2. 누락된 안내 문장을 테스트 가능한 단위로 나눈다."
            ),
            List.of("front/src/app/issues/[id]/page.tsx", "front/src/lib/api.ts"),
            List.of(
                "문구 변경은 화면 스냅샷이나 문서 테스트 기대값에 영향을 줄 수 있습니다.",
                "기존 안내와 중복되는 설명이 생기지 않도록 주변 문맥을 확인해야 합니다."
            )
        ));

        IssueGuide guide = adapter.loadIssueGuide("demo-1");

        assertThat(guide.issueKey()).isEqualTo("demo-1");
        assertThat(guide.filePaths()).containsExactly("front/src/app/issues/[id]/page.tsx", "front/src/lib/api.ts");
        assertThat(guide.guideline()).contains("변경 범위를 작게 유지");
        assertThat(guide.pseudoCode()).contains("상세 화면");
        assertThat(guide.sideEffects()).containsExactly(
            "문구 변경은 화면 스냅샷이나 문서 테스트 기대값에 영향을 줄 수 있습니다.",
            "기존 안내와 중복되는 설명이 생기지 않도록 주변 문맥을 확인해야 합니다."
        );
    }

    @Test
    void loadsEmptyFilePathsForLegacyIssueGuideRows() {
        repository.save(new IssueGuideJpaEntity(
            "legacy-1",
            "기존 저장 데이터는 파일 경로가 없어도 조회되어야 합니다.",
            "1. 기존 행을 읽는다.\n2. 파일 경로를 빈 배열로 응답한다.",
            List.of("기존 데이터 마이그레이션 전에도 API 응답이 깨지면 안 됩니다.")
        ));

        IssueGuide guide = adapter.loadIssueGuide("legacy-1");

        assertThat(guide.filePaths()).isEmpty();
        assertThat(guide.guideline()).contains("기존 저장 데이터");
    }

    @Test
    void loadsCurrentOutputVersionForSavedIssueGuideCache() {
        IssueGuide guide = new IssueGuide(
            "demo-current",
            "검증 흐름의 실패 조건을 먼저 테스트로 고정합니다.",
            "GIVEN validation controller\nWHEN blank input is submitted\nCALL validation endpoint\nASSERT bad request",
            List.of("응답 메시지 기대값이 바뀔 수 있습니다.")
        );

        adapter.saveIssueGuide(guide);

        CachedIssueGuide cachedGuide = adapter.loadCachedIssueGuide("demo-current");
        assertThat(cachedGuide.outputVersion()).isEqualTo(IssueGuideOutputContract.CURRENT_VERSION);
    }

    @Test
    void loadsLegacyOutputVersionForOldIssueGuideCache() {
        repository.save(new IssueGuideJpaEntity(
            "legacy-version",
            "기존 저장 데이터는 낮은 품질일 수 있습니다.",
            "1. 기존 설명을 읽는다.\n2. 그대로 표시한다.",
            List.of(),
            List.of(),
            IssueGuideOutputContract.LEGACY_VERSION
        ));

        CachedIssueGuide cachedGuide = adapter.loadCachedIssueGuide("legacy-version");

        assertThat(cachedGuide.outputVersion()).isEqualTo(IssueGuideOutputContract.LEGACY_VERSION);
    }
}
