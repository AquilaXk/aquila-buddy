package com.aquilabuddy.application.service.issue;

import com.aquilabuddy.application.port.in.issue.RefreshRecommendedIssueCopyUseCase;
import com.aquilabuddy.application.port.out.issue.LoadCollectedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SaveRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SummarizeRecommendedIssuePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueCopyQuality;
import com.aquilabuddy.domain.issue.RecommendedIssueCopyRefreshResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 저장된 추천 이슈 설명/추천 이유를 현재 품질 기준으로 점검하고 재요약합니다.
 */
@Service
public class RecommendedIssueCopyRefreshService implements RefreshRecommendedIssueCopyUseCase {

    private static final String TARGET = "recommended-issue-copy";
    private static final int DEFAULT_LIMIT = 50;
    private static final int SAMPLE_LIMIT = 10;

    private final LoadCollectedIssuesPort loadCollectedIssuesPort;
    private final SaveRecommendedIssuesPort saveRecommendedIssuesPort;
    private final SummarizeRecommendedIssuePort summarizeRecommendedIssuePort;

    public RecommendedIssueCopyRefreshService(
        LoadCollectedIssuesPort loadCollectedIssuesPort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SummarizeRecommendedIssuePort summarizeRecommendedIssuePort
    ) {
        this.loadCollectedIssuesPort = loadCollectedIssuesPort;
        this.saveRecommendedIssuesPort = saveRecommendedIssuesPort;
        this.summarizeRecommendedIssuePort = summarizeRecommendedIssuePort;
    }

    @Override
    public RecommendedIssueCopyRefreshResult refreshRecommendedIssueCopy(int limit, boolean dryRun) {
        int requestedLimit = normalizeLimit(limit);
        List<RecommendedIssue> storedIssues = List.copyOf(loadCollectedIssuesPort.loadCollectedIssues());
        List<RecommendedIssue> allCandidates = storedIssues.stream()
            .filter(RecommendedIssueCopyQuality::needsRefresh)
            .toList();
        List<RecommendedIssue> candidates = allCandidates.stream()
            .limit(requestedLimit)
            .toList();
        List<RecommendedIssueCopyRefreshResult.Sample> samples = samples(candidates);
        int skippedCurrent = storedIssues.size() - allCandidates.size();

        if (dryRun || candidates.isEmpty()) {
            return result(requestedLimit, candidates.size(), 0, skippedCurrent, 0, dryRun, samples, List.of());
        }

        try {
            return refreshCandidates(requestedLimit, candidates, skippedCurrent, samples);
        } catch (RuntimeException error) {
            return result(
                requestedLimit,
                candidates.size(),
                0,
                skippedCurrent,
                candidates.size(),
                false,
                samples,
                candidates.stream()
                    .map(issue -> issue.id() + ": " + failureReason(error))
                    .toList()
            );
        }
    }

    private RecommendedIssueCopyRefreshResult refreshCandidates(
        int requestedLimit,
        List<RecommendedIssue> candidates,
        int skippedCurrent,
        List<RecommendedIssueCopyRefreshResult.Sample> samples
    ) {
        List<RecommendedIssue> summarized = List.copyOf(
            summarizeRecommendedIssuePort.summarizeRecommendedIssues(candidates)
        );
        List<RecommendedIssue> updated = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            RecommendedIssue candidate = candidates.get(index);
            if (index >= summarized.size()) {
                failures.add(candidate.id() + ": summary generator returned no result");
                continue;
            }
            RecommendedIssue refreshed = summarized.get(index).withCurrentCopyOutputVersion();
            if (RecommendedIssueCopyQuality.hasStaleText(refreshed)) {
                failures.add(candidate.id() + ": refreshed copy still fails quality gate");
                continue;
            }
            updated.add(refreshed);
        }
        if (!updated.isEmpty()) {
            saveRecommendedIssuesPort.saveRecommendedIssues(updated);
        }
        return result(
            requestedLimit,
            candidates.size(),
            updated.size(),
            skippedCurrent,
            failures.size(),
            false,
            samples,
            failures
        );
    }

    private static List<RecommendedIssueCopyRefreshResult.Sample> samples(List<RecommendedIssue> candidates) {
        return candidates.stream()
            .limit(SAMPLE_LIMIT)
            .map(issue -> new RecommendedIssueCopyRefreshResult.Sample(
                issue.id(),
                issue.repoFullName(),
                issue.issueNumber(),
                RecommendedIssueCopyQuality.truncateReason(RecommendedIssueCopyQuality.refreshReason(issue))
            ))
            .toList();
    }

    private static RecommendedIssueCopyRefreshResult result(
        int requestedLimit,
        int candidates,
        int updated,
        int skippedCurrent,
        int failed,
        boolean dryRun,
        List<RecommendedIssueCopyRefreshResult.Sample> samples,
        List<String> failureReasons
    ) {
        return new RecommendedIssueCopyRefreshResult(
            TARGET,
            requestedLimit,
            candidates,
            updated,
            skippedCurrent,
            failed,
            dryRun,
            samples,
            failureReasons
        );
    }

    private static int normalizeLimit(int limit) {
        return limit < 1 ? DEFAULT_LIMIT : limit;
    }

    private static String failureReason(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + message.trim();
    }
}
