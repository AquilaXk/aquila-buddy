package com.aquilabuddy.application.service.issue;

import com.aquilabuddy.application.port.in.issue.RecommendIssuesUseCase;
import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 추천 이슈 조회 유스케이스를 수행합니다.
 *
 * <p>데이터 출처는 outbound port 뒤로 숨겨 application 계층이 영속성 구현에 의존하지 않게 합니다.
 */
@Service
public class RecommendedIssueService implements RecommendIssuesUseCase {

    private final LoadRecommendedIssuesPort loadRecommendedIssuesPort;

    public RecommendedIssueService(LoadRecommendedIssuesPort loadRecommendedIssuesPort) {
        this.loadRecommendedIssuesPort = loadRecommendedIssuesPort;
    }

    /**
     * outbound port에서 읽은 추천 이슈 목록을 유스케이스 결과로 전달합니다.
     */
    @Override
    public List<RecommendedIssue> recommend() {
        return loadRecommendedIssuesPort.loadRecommendedIssues();
    }
}
