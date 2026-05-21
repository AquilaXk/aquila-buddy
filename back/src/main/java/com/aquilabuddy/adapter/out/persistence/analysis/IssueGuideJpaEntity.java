package com.aquilabuddy.adapter.out.persistence.analysis;

import com.aquilabuddy.domain.analysis.IssueGuideOutputContract;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 이슈 가이드 조회 결과를 저장하는 JPA 전용 모델입니다.
 *
 * <p>도메인 record와 분리해서 영속성 컬럼명, 길이, 컬렉션 테이블 같은 저장소 세부사항이
 * application/domain 계층으로 새지 않도록 둡니다.
 */
@Entity
@Table(name = "issue_guides")
public class IssueGuideJpaEntity {

    @Id
    @Column(name = "issue_id", nullable = false, length = 100)
    private String issueId;

    @Column(name = "guideline", nullable = false, length = 4_000)
    private String guideline;

    @Column(name = "pseudo_code", nullable = false, length = 4_000)
    private String pseudoCode;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "output_version", nullable = false, length = 120)
    private String outputVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "issue_guide_file_paths",
        joinColumns = @JoinColumn(name = "issue_guide_id")
    )
    @OrderColumn(name = "path_order")
    @Column(name = "file_path", nullable = false, length = 500)
    private List<String> filePaths = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "issue_guide_side_effects",
        joinColumns = @JoinColumn(name = "issue_guide_id")
    )
    @OrderColumn(name = "effect_order")
    @Column(name = "side_effect", nullable = false, length = 1_000)
    private List<String> sideEffects = new ArrayList<>();

    protected IssueGuideJpaEntity() {
    }

    IssueGuideJpaEntity(
        String issueId,
        String guideline,
        String pseudoCode,
        List<String> sideEffects
    ) {
        this(issueId, guideline, pseudoCode, List.of(), sideEffects, Instant.now());
    }

    IssueGuideJpaEntity(
        String issueId,
        String guideline,
        String pseudoCode,
        List<String> filePaths,
        List<String> sideEffects
    ) {
        this(issueId, guideline, pseudoCode, filePaths, sideEffects, Instant.now());
    }

    IssueGuideJpaEntity(
        String issueId,
        String guideline,
        String pseudoCode,
        List<String> filePaths,
        List<String> sideEffects,
        Instant generatedAt
    ) {
        this(
            issueId,
            guideline,
            pseudoCode,
            filePaths,
            sideEffects,
            generatedAt,
            IssueGuideOutputContract.CURRENT_VERSION
        );
    }

    IssueGuideJpaEntity(
        String issueId,
        String guideline,
        String pseudoCode,
        List<String> filePaths,
        List<String> sideEffects,
        String outputVersion
    ) {
        this(issueId, guideline, pseudoCode, filePaths, sideEffects, Instant.now(), outputVersion);
    }

    IssueGuideJpaEntity(
        String issueId,
        String guideline,
        String pseudoCode,
        List<String> filePaths,
        List<String> sideEffects,
        Instant generatedAt,
        String outputVersion
    ) {
        this.issueId = issueId;
        this.guideline = guideline;
        this.pseudoCode = pseudoCode;
        this.filePaths = new ArrayList<>(filePaths == null ? List.of() : filePaths);
        this.sideEffects = new ArrayList<>(sideEffects == null ? List.of() : sideEffects);
        this.generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        this.outputVersion = normalizeOutputVersion(outputVersion);
    }

    @PrePersist
    void prePersist() {
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
        outputVersion = normalizeOutputVersion(outputVersion);
    }

    String getIssueId() {
        return issueId;
    }

    String getGuideline() {
        return guideline;
    }

    String getPseudoCode() {
        return pseudoCode;
    }

    Instant getGeneratedAt() {
        return generatedAt;
    }

    String getOutputVersion() {
        return outputVersion;
    }

    List<String> getFilePaths() {
        return List.copyOf(filePaths);
    }

    List<String> getSideEffects() {
        return List.copyOf(sideEffects);
    }

    private static String normalizeOutputVersion(String outputVersion) {
        return outputVersion == null || outputVersion.isBlank()
            ? IssueGuideOutputContract.CURRENT_VERSION
            : outputVersion.trim();
    }
}
