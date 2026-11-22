package com.aquilabuddy.domain.analysis;

/**
 * 사용자별 이슈 분석 생성 일일 quota를 넘었을 때 web layer에서 429로 변환할 예외입니다.
 */
public class IssueGuideGenerationDailyLimitExceededException extends RuntimeException {

    public static final String MESSAGE = "이슈 분석 일일 생성 제한을 초과했습니다. 잠시 후 다시 시도해 주세요.";

    private final long userGithubId;

    public IssueGuideGenerationDailyLimitExceededException(long userGithubId) {
        super(MESSAGE);
        this.userGithubId = userGithubId;
    }

    public long userGithubId() {
        return userGithubId;
    }
}
