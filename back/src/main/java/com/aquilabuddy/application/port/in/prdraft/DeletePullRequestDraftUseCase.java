package com.aquilabuddy.application.port.in.prdraft;

/**
 * [Incoming Port] 저장된 PR 초안을 삭제하는 use case 계약입니다.
 */
public interface DeletePullRequestDraftUseCase {

    /**
     * 저장된 PR 초안을 삭제하고 실제 삭제 여부를 반환합니다.
     */
    boolean deletePullRequestDraft(String id);
}
