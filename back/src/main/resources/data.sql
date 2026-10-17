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
    '첫 기여자가 문서를 따라가며 막히는 부분을 줄이도록 기여 안내 문장과 예시를 더 명확하게 정리하는 이슈입니다.',
    0.91,
    'OPEN',
    '프론트엔드와 문서 작업으로 첫 PR 흐름을 작게 검증할 수 있습니다.'
),
(
    'demo-2',
    'spring-projects/spring-boot',
    2,
    'Refine validation error example',
    '검증 실패 응답과 예시 코드가 실제 동작을 잘 설명하도록 Java/Spring validation 흐름을 보완하는 이슈입니다.',
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
    pseudo_code,
    output_version
) VALUES
(
    'demo-1',
    'packages/next/src/client/app-next.tsx와 문서 안내 흐름에서 첫 기여자가 따라가는 예제와 실제 진입점 조건이 어긋나는 부분을 확인하고, 누락된 안내를 작은 문단 단위로 보완합니다.',
    'GIVEN contributor documentation references packages/next/src/client/app-next.tsx
WHEN the current guide omits the client entry verification step
CALL update_guidance_copy_with_matching_terms()
ASSERT documentation and packages/next/src/client/app-next.tsx entry flow use the same wording',
    'issue-guide-output-v2026-05-21'
),
(
    'demo-2',
    'WebMvcAutoConfiguration.java 관련 요청 검증 실패 조건을 테스트로 고정한 뒤, 실패 응답 메시지와 예시 코드가 같은 용어를 쓰도록 정리합니다.',
    'GIVEN WebMvcAutoConfiguration validation flow receives invalid input
WHEN validation returns the failure response
CALL add_regression_test_for_error_message()
CALL align_example_code_with_response_terms()
ASSERT valid and invalid inputs are both verified',
    'issue-guide-output-v2026-05-21'
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

INSERT INTO issue_guide_file_paths (
    issue_guide_id,
    path_order,
    file_path
) VALUES
('demo-1', 0, 'packages/next/src/client/app-next.tsx'),
('demo-1', 1, 'docs/01-app/01-getting-started/01-installation.mdx'),
('demo-2', 0, 'spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/servlet/WebMvcAutoConfiguration.java');
