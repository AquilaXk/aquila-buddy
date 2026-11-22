package com.aquilabuddy.application.port.out.analysis;

import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;

/**
 * 추천 이슈와 관련된 저장소 코드 맥락을 수집하는 outbound port입니다.
 */
public interface LoadRepositoryCodeContextPort {

    /**
     * 이슈 guide 생성 전에 사용할 제한된 코드 맥락을 반환합니다.
     */
    RepositoryCodeContext loadRepositoryCodeContext(RecommendedIssue issue);
}
