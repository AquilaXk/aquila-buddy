package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/**
 * 추천 이슈 목록을 저장하는 JPA 전용 모델입니다.
 *
 * <p>도메인 record와 분리해서 테이블명, 컬럼 길이, 라벨 컬렉션 테이블 같은 저장소 세부사항이
 * application/domain 계층으로 새지 않도록 둡니다.
 */
@Entity
@Table(name = "recommended_issues")
public class RecommendedIssueJpaEntity {

    @Id
    @Column(name = "issue_id", nullable = false, length = 100)
    private String id;

    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    @Column(name = "issue_number", nullable = false)
    private long issueNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "summary", nullable = false, length = 1_000)
    private String summary;

    @Column(name = "score", nullable = false)
    private double score;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "recommended_issue_labels",
        joinColumns = @JoinColumn(name = "recommended_issue_id")
    )
    @OrderColumn(name = "label_order")
    @Column(name = "label", nullable = false)
    @BatchSize(size = 50)
    private List<String> labels = new ArrayList<>();

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "reason", nullable = false, length = 1_000)
    private String reason;

    @Column(name = "copy_output_version", nullable = false, length = 120)
    private String copyOutputVersion = RecommendedIssue.CURRENT_COPY_OUTPUT_VERSION;

    @Column(name = "issue_embedding", length = 12_000, insertable = false, updatable = false)
    private String issueEmbedding;

    protected RecommendedIssueJpaEntity() {
    }

    RecommendedIssueJpaEntity(
        String id,
        String repoFullName,
        long issueNumber,
        String title,
        String summary,
        double score,
        List<String> labels,
        String status,
        String reason
    ) {
        this(
            id,
            repoFullName,
            issueNumber,
            title,
            summary,
            score,
            labels,
            status,
            reason,
            RecommendedIssue.CURRENT_COPY_OUTPUT_VERSION,
            null
        );
    }

    RecommendedIssueJpaEntity(
        String id,
        String repoFullName,
        long issueNumber,
        String title,
        String summary,
        double score,
        List<String> labels,
        String status,
        String reason,
        String copyOutputVersion
    ) {
        this(id, repoFullName, issueNumber, title, summary, score, labels, status, reason, copyOutputVersion, null);
    }

    RecommendedIssueJpaEntity(
        String id,
        String repoFullName,
        long issueNumber,
        String title,
        String summary,
        double score,
        List<String> labels,
        String status,
        String reason,
        String copyOutputVersion,
        String issueEmbedding
    ) {
        this.id = id;
        this.repoFullName = repoFullName;
        this.issueNumber = issueNumber;
        this.title = title;
        this.summary = summary;
        this.score = score;
        this.labels = new ArrayList<>(labels);
        this.status = status;
        this.reason = reason;
        this.copyOutputVersion = copyOutputVersion == null || copyOutputVersion.isBlank()
            ? "legacy"
            : copyOutputVersion.trim();
        this.issueEmbedding = issueEmbedding;
    }

    String getId() {
        return id;
    }

    String getRepoFullName() {
        return repoFullName;
    }

    long getIssueNumber() {
        return issueNumber;
    }

    String getTitle() {
        return title;
    }

    String getSummary() {
        return summary;
    }

    double getScore() {
        return score;
    }

    List<String> getLabels() {
        return List.copyOf(labels);
    }

    String getStatus() {
        return status;
    }

    String getReason() {
        return reason;
    }

    String getCopyOutputVersion() {
        return copyOutputVersion;
    }

    String getIssueEmbedding() {
        return issueEmbedding;
    }

    void updateIssueEmbedding(String issueEmbedding) {
        this.issueEmbedding = issueEmbedding;
    }
}
