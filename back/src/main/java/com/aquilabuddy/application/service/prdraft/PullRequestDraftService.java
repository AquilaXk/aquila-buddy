package com.aquilabuddy.application.service.prdraft;

import com.aquilabuddy.application.port.in.prdraft.CreatePullRequestDraftUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftsUseCase;
import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * [Application Service] PR 초안 생성 유스케이스를 수행합니다.
 *
 * <p>초안 생성 방식은 outbound port 뒤에 두어, 이후 AI adapter로 바뀌어도 유스케이스 경계는 유지합니다.
 */
@Service
public class PullRequestDraftService implements
    CreatePullRequestDraftUseCase,
    ListPullRequestDraftsUseCase,
    GetPullRequestDraftUseCase {

    private final GeneratePullRequestDraftPort generatePullRequestDraftPort;
    private final SavePullRequestDraftPort savePullRequestDraftPort;
    private final LoadPullRequestDraftPort loadPullRequestDraftPort;

    public PullRequestDraftService(
        GeneratePullRequestDraftPort generatePullRequestDraftPort,
        SavePullRequestDraftPort savePullRequestDraftPort,
        LoadPullRequestDraftPort loadPullRequestDraftPort
    ) {
        this.generatePullRequestDraftPort = generatePullRequestDraftPort;
        this.savePullRequestDraftPort = savePullRequestDraftPort;
        this.loadPullRequestDraftPort = loadPullRequestDraftPort;
    }

    /**
     * 입력 맥락으로 PR 초안을 생성한 뒤 저장된 초안 식별자를 포함해 반환합니다.
     */
    @Override
    public StoredPullRequestDraft create(PullRequestDraftRequest request) {
        PullRequestDraft generated = generatePullRequestDraftPort.generate(request);
        return savePullRequestDraftPort.savePullRequestDraft(request, generated);
    }

    @Override
    public List<StoredPullRequestDraft> listRecentPullRequestDrafts() {
        return loadPullRequestDraftPort.loadRecentPullRequestDrafts();
    }

    @Override
    public Optional<StoredPullRequestDraft> getPullRequestDraft(String id) {
        return loadPullRequestDraftPort.loadPullRequestDraft(id);
    }
}
