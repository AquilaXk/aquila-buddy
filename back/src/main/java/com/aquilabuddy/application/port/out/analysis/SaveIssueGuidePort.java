package com.aquilabuddy.application.port.out.analysis;

import com.aquilabuddy.domain.analysis.IssueGuide;

/**
 * 생성된 issue guide를 캐시에 저장하는 outbound port입니다.
 */
public interface SaveIssueGuidePort {

    /**
     * 생성된 guide를 이후 조회에 사용할 수 있게 저장합니다.
     */
    void saveIssueGuide(IssueGuide guide);
}
