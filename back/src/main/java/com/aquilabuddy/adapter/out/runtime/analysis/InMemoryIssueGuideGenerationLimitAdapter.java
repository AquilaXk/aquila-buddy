package com.aquilabuddy.adapter.out.runtime.analysis;

import com.aquilabuddy.application.port.out.analysis.IssueGuideGenerationLimitPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 단일 애플리케이션 인스턴스에서 같은 이슈 guide 생성 요청을 짧은 시간 동안 제한합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.analysis.issue-guide", name = "limit-store", havingValue = "memory")
public class InMemoryIssueGuideGenerationLimitAdapter implements IssueGuideGenerationLimitPort {

    private final Map<String, Instant> lastGeneratedAtByIssueId = new ConcurrentHashMap<>();
    private final Duration cooldown;

    public InMemoryIssueGuideGenerationLimitAdapter(
        @Value("${app.analysis.issue-guide.generation-cooldown:PT1M}") Duration cooldown
    ) {
        this.cooldown = cooldown;
    }

    @Override
    public void acquireGenerationSlot(String issueId) {
        Instant now = Instant.now();
        Instant previous = lastGeneratedAtByIssueId.put(issueId, now);
        if (previous != null && previous.plus(cooldown).isAfter(now)) {
            // 제한 실패 시 다음 성공 가능 시각이 앞당겨지지 않도록 기존 시간을 되돌립니다.
            lastGeneratedAtByIssueId.put(issueId, previous);
            throw new IllegalStateException("Issue guide generation is rate limited: " + issueId);
        }
    }
}
