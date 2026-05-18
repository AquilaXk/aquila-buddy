package com.aquilabuddy.application.service.analysis;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.application.port.in.analysis.GetIssuePseudoCodeUseCase;
import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.IssueGuideGenerationLimitPort;
import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.LoadRepositoryCodeContextPort;
import com.aquilabuddy.application.port.out.analysis.SaveIssueGuidePort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideWorkflow;
import com.aquilabuddy.domain.analysis.IssuePseudoCode;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 이슈 작업 가이드 조회 유스케이스를 수행합니다.
 *
 * <p>가이드 생성/저장 방식이 바뀌어도 incoming port 계약은 유지되도록 outbound port만 호출합니다.
 */
@Service
public class IssueGuideService implements GetIssueGuideUseCase, GetIssuePseudoCodeUseCase {

    private final LoadIssueGuidePort loadIssueGuidePort;
    private final GenerateIssueGuidePort generateIssueGuidePort;
    private final SaveIssueGuidePort saveIssueGuidePort;
    private final IssueGuideGenerationLimitPort issueGuideGenerationLimitPort;
    private final FindRecommendedIssuePort findRecommendedIssuePort;
    private final LoadRepositoryCodeContextPort loadRepositoryCodeContextPort;

    public IssueGuideService(
        LoadIssueGuidePort loadIssueGuidePort,
        GenerateIssueGuidePort generateIssueGuidePort,
        SaveIssueGuidePort saveIssueGuidePort,
        IssueGuideGenerationLimitPort issueGuideGenerationLimitPort,
        FindRecommendedIssuePort findRecommendedIssuePort
    ) {
        this(
            loadIssueGuidePort,
            generateIssueGuidePort,
            saveIssueGuidePort,
            issueGuideGenerationLimitPort,
            findRecommendedIssuePort,
            issue -> RepositoryCodeContext.empty()
        );
    }

    @Autowired
    public IssueGuideService(
        LoadIssueGuidePort loadIssueGuidePort,
        GenerateIssueGuidePort generateIssueGuidePort,
        SaveIssueGuidePort saveIssueGuidePort,
        IssueGuideGenerationLimitPort issueGuideGenerationLimitPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        ObjectProvider<LoadRepositoryCodeContextPort> loadRepositoryCodeContextPortProvider
    ) {
        this(
            loadIssueGuidePort,
            generateIssueGuidePort,
            saveIssueGuidePort,
            issueGuideGenerationLimitPort,
            findRecommendedIssuePort,
            loadRepositoryCodeContextPortProvider.getIfAvailable(() -> issue -> RepositoryCodeContext.empty())
        );
    }

    IssueGuideService(
        LoadIssueGuidePort loadIssueGuidePort,
        GenerateIssueGuidePort generateIssueGuidePort,
        SaveIssueGuidePort saveIssueGuidePort,
        IssueGuideGenerationLimitPort issueGuideGenerationLimitPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        LoadRepositoryCodeContextPort loadRepositoryCodeContextPort
    ) {
        this.loadIssueGuidePort = loadIssueGuidePort;
        this.generateIssueGuidePort = generateIssueGuidePort;
        this.saveIssueGuidePort = saveIssueGuidePort;
        this.issueGuideGenerationLimitPort = issueGuideGenerationLimitPort;
        this.findRecommendedIssuePort = findRecommendedIssuePort;
        this.loadRepositoryCodeContextPort = loadRepositoryCodeContextPort;
    }

    /**
     * 캐시된 guide를 우선 반환하고, 없으면 추천 이슈 기반 guide를 생성해 저장합니다.
     */
    @Override
    public IssueGuide findByIssueId(String issueId) {
        try {
            return loadIssueGuidePort.loadIssueGuide(issueId);
        } catch (NoSuchElementException ignored) {
            return generateAndCacheIssueGuide(issueId);
        }
    }

    private IssueGuide generateAndCacheIssueGuide(String issueId) {
        RecommendedIssue issue = findRecommendedIssuePort.findById(issueId)
            .orElseThrow(() -> new NoSuchElementException("Recommended issue not found: " + issueId));

        issueGuideGenerationLimitPort.acquireGenerationSlot(issueId);
        try {
            IssueGuide generatedGuide = generateIssueGuidePort.generateIssueGuide(issue, loadRepositoryCodeContext(issue));
            saveIssueGuidePort.saveIssueGuide(generatedGuide);
            issueGuideGenerationLimitPort.recordGenerationSucceeded(issueId);
            return generatedGuide;
        } catch (RuntimeException exception) {
            issueGuideGenerationLimitPort.recordGenerationFailed(issueId, exception.getMessage());
            throw exception;
        }
    }

    private RepositoryCodeContext loadRepositoryCodeContext(RecommendedIssue issue) {
        try {
            return loadRepositoryCodeContextPort.loadRepositoryCodeContext(issue);
        } catch (RuntimeException ignored) {
            return RepositoryCodeContext.empty();
        }
    }

    /**
     * 저장된 작업 가이드를 UI에서 바로 렌더링 가능한 workflow 단계로 변환합니다.
     */
    @Override
    public IssueGuideWorkflow findWorkflowByIssueId(String issueId) {
        return IssueGuideWorkflow.from(findByIssueId(issueId));
    }

    /**
     * 저장된 작업 가이드에서 의사코드만 분리해 반환합니다.
     */
    @Override
    public IssuePseudoCode findPseudoCodeByIssueId(String issueId) {
        return IssuePseudoCode.from(findByIssueId(issueId));
    }
}
