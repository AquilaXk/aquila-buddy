package com.aquilabuddy.domain.analysis;

/**
 * 저장된 IssueGuide가 현재 화면/프롬프트 출력 계약을 만족하는지 구분하는 버전 값입니다.
 */
public final class IssueGuideOutputContract {

    public static final String CURRENT_VERSION = "issue-guide-output-v2026-05-21";
    public static final String LEGACY_VERSION = "legacy";

    private IssueGuideOutputContract() {
    }

    public static boolean isCurrent(String outputVersion) {
        return CURRENT_VERSION.equals(outputVersion);
    }
}
