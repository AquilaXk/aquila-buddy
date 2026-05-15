package com.aquilabuddy.adapter.out.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.analysis.IssueGuide;
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
            List.of(
                "문구 변경은 화면 스냅샷이나 문서 테스트 기대값에 영향을 줄 수 있습니다.",
                "기존 안내와 중복되는 설명이 생기지 않도록 주변 문맥을 확인해야 합니다."
            )
        ));

        IssueGuide guide = adapter.loadIssueGuide("demo-1");

        assertThat(guide.issueKey()).isEqualTo("demo-1");
        assertThat(guide.guideline()).contains("변경 범위를 작게 유지");
        assertThat(guide.pseudoCode()).contains("상세 화면");
        assertThat(guide.sideEffects()).containsExactly(
            "문구 변경은 화면 스냅샷이나 문서 테스트 기대값에 영향을 줄 수 있습니다.",
            "기존 안내와 중복되는 설명이 생기지 않도록 주변 문맥을 확인해야 합니다."
        );
    }
}
