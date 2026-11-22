package com.aquilabuddy.application.port.out.analysis;

import java.util.Collection;
import java.util.Set;

/**
 * 저장된 이슈 목록 중 분석 가이드가 이미 생성된 이슈 ID 집합을 일괄 조회하는 outbound port입니다.
 */
public interface LoadAnalyzedIssueIdsPort {

    Set<String> findAnalyzedIssueIds(Collection<String> issueIds);
}
