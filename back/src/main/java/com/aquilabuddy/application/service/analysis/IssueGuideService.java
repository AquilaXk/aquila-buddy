package com.aquilabuddy.application.service.analysis;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideWorkflow;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 이슈 작업 가이드 조회 유스케이스를 수행합니다.
 *
 * <p>가이드 생성/저장 방식이 바뀌어도 incoming port 계약은 유지되도록 outbound port만 호출합니다.
 */
@Service
public class IssueGuideService implements GetIssueGuideUseCase {

    private final LoadIssueGuidePort loadIssueGuidePort;

    public IssueGuideService(LoadIssueGuidePort loadIssueGuidePort) {
        this.loadIssueGuidePort = loadIssueGuidePort;
    }

    /**
     * 이슈 식별자를 outbound port에 위임해 작업 가이드를 조회합니다.
     */
    @Override
    public IssueGuide findByIssueId(String issueId) {
        return loadIssueGuidePort.loadIssueGuide(issueId);
    }

    /**
     * 저장된 작업 가이드를 UI에서 바로 렌더링 가능한 workflow 단계로 변환합니다.
     */
    @Override
    public IssueGuideWorkflow findWorkflowByIssueId(String issueId) {
        return IssueGuideWorkflow.from(findByIssueId(issueId));
    }
}
