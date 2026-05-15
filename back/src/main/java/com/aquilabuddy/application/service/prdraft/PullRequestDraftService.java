package com.aquilabuddy.application.service.prdraft;

import com.aquilabuddy.application.port.in.prdraft.CreatePullRequestDraftUseCase;
import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import org.springframework.stereotype.Service;

/**
 * [Application Service] PR 초안 생성 유스케이스를 수행합니다.
 *
 * <p>초안 생성 방식은 outbound port 뒤에 두어, 이후 AI adapter로 바뀌어도 유스케이스 경계는 유지합니다.
 */
@Service
public class PullRequestDraftService implements CreatePullRequestDraftUseCase {

    private final GeneratePullRequestDraftPort generatePullRequestDraftPort;

    public PullRequestDraftService(GeneratePullRequestDraftPort generatePullRequestDraftPort) {
        this.generatePullRequestDraftPort = generatePullRequestDraftPort;
    }

    /**
     * 입력 맥락을 outbound port에 위임해 PR 초안을 생성합니다.
     */
    @Override
    public PullRequestDraft create(PullRequestDraftRequest request) {
        return generatePullRequestDraftPort.generate(request);
    }
}
