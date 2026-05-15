DROP TABLE IF EXISTS recommended_issue_labels;
DROP TABLE IF EXISTS recommended_issues;

CREATE TABLE recommended_issues (
    issue_id VARCHAR(100) PRIMARY KEY,
    repo_full_name VARCHAR(255) NOT NULL,
    issue_number BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL,
    reason VARCHAR(1000) NOT NULL
);

CREATE TABLE recommended_issue_labels (
    recommended_issue_id VARCHAR(100) NOT NULL,
    label_order INTEGER NOT NULL,
    label VARCHAR(255) NOT NULL,
    PRIMARY KEY (recommended_issue_id, label_order),
    CONSTRAINT fk_recommended_issue_labels_issue
        FOREIGN KEY (recommended_issue_id)
        REFERENCES recommended_issues(issue_id)
        ON DELETE CASCADE
);
