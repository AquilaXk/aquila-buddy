INSERT INTO user_profiles (
    github_id,
    login,
    name,
    email,
    profile_ready,
    vector_updated_at
) VALUES (
    10001,
    'aquila-dev',
    'Aquila Developer',
    'aquila@example.com',
    TRUE,
    '2026-05-15T09:00:00Z'
);

INSERT INTO user_profile_primary_languages (
    user_github_id,
    language_order,
    language
) VALUES
(10001, 0, 'Java'),
(10001, 1, 'TypeScript'),
(10001, 2, 'SQL');

INSERT INTO user_profile_topics (
    user_github_id,
    topic_order,
    topic
) VALUES
(10001, 0, 'spring'),
(10001, 1, 'nextjs'),
(10001, 2, 'open-source');

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

INSERT INTO issue_guides (
    issue_id,
    guideline,
    pseudo_code
) VALUES
(
    'demo-1',
    '변경 범위를 작게 유지하면서 현재 문서 흐름과 실제 첫 기여 절차가 어긋나는 지점을 보완합니다.',
    '1. 상세 화면에서 제공하는 이슈 요약과 라벨을 확인한다.
2. 누락된 안내 문장을 테스트 가능한 단위로 나눈다.
3. 한 번에 하나의 문단 또는 예시만 수정한다.
4. 변경 후 화면과 테스트에서 동일한 용어가 쓰이는지 확인한다.'
),
(
    'demo-2',
    '입력 검증 흐름을 따라가며 실패 응답 메시지와 예시 코드를 함께 정리합니다.',
    '1. 검증 대상 입력과 실패 조건을 확인한다.
2. 현재 실패 응답 형식을 테스트로 고정한다.
3. 예시 코드와 설명을 같은 용어로 맞춘다.
4. 정상 입력과 실패 입력을 모두 재검증한다.'
);

INSERT INTO issue_guide_side_effects (
    issue_guide_id,
    effect_order,
    side_effect
) VALUES
('demo-1', 0, '문구 변경은 화면 스냅샷이나 문서 테스트 기대값에 영향을 줄 수 있습니다.'),
('demo-1', 1, '기존 안내와 중복되는 설명이 생기지 않도록 주변 문맥을 확인해야 합니다.'),
('demo-2', 0, '검증 메시지 문구가 바뀌면 관련 문서와 테스트 기대값도 함께 바뀔 수 있습니다.'),
('demo-2', 1, '동일 응답 형식을 사용하는 화면의 에러 표시 문구를 확인해야 합니다.');
