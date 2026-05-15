package com.aquilabuddy.application.service.issue;

import com.aquilabuddy.application.port.in.issue.RecommendIssuesUseCase;
import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SaveRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SearchRecommendedIssuesPort;
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
    private final SaveRecommendedIssuesPort saveRecommendedIssuesPort;
    private final SearchRecommendedIssuesPort searchRecommendedIssuesPort;

    public RecommendedIssueService(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SearchRecommendedIssuesPort searchRecommendedIssuesPort
    ) {
        this.loadRecommendedIssuesPort = loadRecommendedIssuesPort;
        this.saveRecommendedIssuesPort = saveRecommendedIssuesPort;
        this.searchRecommendedIssuesPort = searchRecommendedIssuesPort;
    }

    /**
     * 최신 검색 후보를 우선 반환하고, 검색이 비어 있거나 실패하면 저장된 추천 이슈로 대체합니다.
     */
    @Override
    public List<RecommendedIssue> recommend() {
        try {
            List<RecommendedIssue> searchedIssues = searchRecommendedIssuesPort.searchRecommendedIssues();
            if (!searchedIssues.isEmpty()) {
                saveRecommendedIssuesPort.saveRecommendedIssues(searchedIssues);
                return searchedIssues;
            }
        } catch (RuntimeException ignored) {
            // 외부 검색 장애가 추천 API 전체 실패로 번지지 않도록 저장 데이터 fallback을 유지합니다.
        }
        return loadRecommendedIssuesPort.loadRecommendedIssues();
    }
}
