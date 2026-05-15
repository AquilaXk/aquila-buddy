INSERT INTO recommended_issues (
    issue_id,
    repo_full_name,
    issue_number,
    title,
    summary,
    score,
    status,
    reason
) VALUES
(
    'demo-1',
    'vercel/next.js',
    1,
    'Improve contributor-facing documentation',
    '문서 중심 첫 기여 흐름을 확인하기 좋은 이슈입니다.',
    0.91,
    'OPEN',
    '프론트엔드와 문서 작업으로 첫 PR 흐름을 작게 검증할 수 있습니다.'
),
(
    'demo-2',
    'spring-projects/spring-boot',
    2,
    'Refine validation error example',
    'Java/Spring 관심사와 맞는 validation 예시 개선 이슈입니다.',
    0.87,
    'OPEN',
    '현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다.'
);

INSERT INTO recommended_issue_labels (
    recommended_issue_id,
    label_order,
    label
) VALUES
('demo-1', 0, 'good first issue'),
('demo-1', 1, 'documentation'),
('demo-2', 0, 'good first issue'),
('demo-2', 1, 'validation');
