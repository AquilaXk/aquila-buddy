package com.aquilabuddy.adapter.in.web.prdraft;

import com.aquilabuddy.application.port.in.prdraft.CreatePullRequestDraftUseCase;
import com.aquilabuddy.application.port.in.prdraft.CreatePullRequestDraftTranslationUseCase;
import com.aquilabuddy.application.port.in.prdraft.DeletePullRequestDraftUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftContributionReportUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftTranslationReadinessUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftTranslationSubmissionUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftTranslationTimelineUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftReadinessUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftSubmissionUseCase;
import com.aquilabuddy.application.port.in.prdraft.GetPullRequestDraftTimelineUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftHistoryUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftsUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftTranslationActivitiesUseCase;
import com.aquilabuddy.application.port.in.prdraft.ListPullRequestDraftTranslationsUseCase;
import com.aquilabuddy.application.port.in.prdraft.TranslatePullRequestDraftUseCase;
import com.aquilabuddy.application.port.in.prdraft.UpdatePullRequestDraftUseCase;
import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryPage;
import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryQuery;
import com.aquilabuddy.domain.prdraft.PullRequestDraftReadiness;
import com.aquilabuddy.domain.prdraft.PullRequestDraftContributionReport;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftSubmission;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslateResult;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTimeline;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationActivity;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationReadiness;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationSubmission;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationTimeline;
import com.aquilabuddy.domain.prdraft.PullRequestDraftUpdateRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import com.aquilabuddy.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * [Incoming Web Adapter] PR 초안 API 요청을 처리하는 REST controller입니다.
 */
@RestController
@RequestMapping("/api/v1/pr")
public class PullRequestDraftController {

    private final CreatePullRequestDraftUseCase createPullRequestDraftUseCase;
    private final ListPullRequestDraftsUseCase listPullRequestDraftsUseCase;
    private final ListPullRequestDraftHistoryUseCase listPullRequestDraftHistoryUseCase;
    private final GetPullRequestDraftUseCase getPullRequestDraftUseCase;
    private final UpdatePullRequestDraftUseCase updatePullRequestDraftUseCase;
    private final DeletePullRequestDraftUseCase deletePullRequestDraftUseCase;
    private final GetPullRequestDraftReadinessUseCase getPullRequestDraftReadinessUseCase;
    private final GetPullRequestDraftTimelineUseCase getPullRequestDraftTimelineUseCase;
    private final GetPullRequestDraftSubmissionUseCase getPullRequestDraftSubmissionUseCase;
    private final GetPullRequestDraftContributionReportUseCase getPullRequestDraftContributionReportUseCase;
    private final CreatePullRequestDraftTranslationUseCase createPullRequestDraftTranslationUseCase;
    private final ListPullRequestDraftTranslationsUseCase listPullRequestDraftTranslationsUseCase;
    private final GetPullRequestDraftTranslationReadinessUseCase getPullRequestDraftTranslationReadinessUseCase;
    private final GetPullRequestDraftTranslationSubmissionUseCase getPullRequestDraftTranslationSubmissionUseCase;
    private final GetPullRequestDraftTranslationTimelineUseCase getPullRequestDraftTranslationTimelineUseCase;
    private final ListPullRequestDraftTranslationActivitiesUseCase listPullRequestDraftTranslationActivitiesUseCase;
    private final TranslatePullRequestDraftUseCase translatePullRequestDraftUseCase;

    public PullRequestDraftController(
        CreatePullRequestDraftUseCase createPullRequestDraftUseCase,
        ListPullRequestDraftsUseCase listPullRequestDraftsUseCase,
        ListPullRequestDraftHistoryUseCase listPullRequestDraftHistoryUseCase,
        GetPullRequestDraftUseCase getPullRequestDraftUseCase,
        UpdatePullRequestDraftUseCase updatePullRequestDraftUseCase,
        DeletePullRequestDraftUseCase deletePullRequestDraftUseCase,
        GetPullRequestDraftReadinessUseCase getPullRequestDraftReadinessUseCase,
        GetPullRequestDraftTimelineUseCase getPullRequestDraftTimelineUseCase,
        GetPullRequestDraftSubmissionUseCase getPullRequestDraftSubmissionUseCase,
        GetPullRequestDraftContributionReportUseCase getPullRequestDraftContributionReportUseCase,
        CreatePullRequestDraftTranslationUseCase createPullRequestDraftTranslationUseCase,
        ListPullRequestDraftTranslationsUseCase listPullRequestDraftTranslationsUseCase,
        GetPullRequestDraftTranslationReadinessUseCase getPullRequestDraftTranslationReadinessUseCase,
        GetPullRequestDraftTranslationSubmissionUseCase getPullRequestDraftTranslationSubmissionUseCase,
        GetPullRequestDraftTranslationTimelineUseCase getPullRequestDraftTranslationTimelineUseCase,
        ListPullRequestDraftTranslationActivitiesUseCase listPullRequestDraftTranslationActivitiesUseCase,
        TranslatePullRequestDraftUseCase translatePullRequestDraftUseCase
    ) {
        this.createPullRequestDraftUseCase = createPullRequestDraftUseCase;
        this.listPullRequestDraftsUseCase = listPullRequestDraftsUseCase;
        this.listPullRequestDraftHistoryUseCase = listPullRequestDraftHistoryUseCase;
        this.getPullRequestDraftUseCase = getPullRequestDraftUseCase;
        this.updatePullRequestDraftUseCase = updatePullRequestDraftUseCase;
        this.deletePullRequestDraftUseCase = deletePullRequestDraftUseCase;
        this.getPullRequestDraftReadinessUseCase = getPullRequestDraftReadinessUseCase;
        this.getPullRequestDraftTimelineUseCase = getPullRequestDraftTimelineUseCase;
        this.getPullRequestDraftSubmissionUseCase = getPullRequestDraftSubmissionUseCase;
        this.getPullRequestDraftContributionReportUseCase = getPullRequestDraftContributionReportUseCase;
        this.createPullRequestDraftTranslationUseCase = createPullRequestDraftTranslationUseCase;
        this.listPullRequestDraftTranslationsUseCase = listPullRequestDraftTranslationsUseCase;
        this.getPullRequestDraftTranslationReadinessUseCase = getPullRequestDraftTranslationReadinessUseCase;
        this.getPullRequestDraftTranslationSubmissionUseCase = getPullRequestDraftTranslationSubmissionUseCase;
        this.getPullRequestDraftTranslationTimelineUseCase = getPullRequestDraftTranslationTimelineUseCase;
        this.listPullRequestDraftTranslationActivitiesUseCase = listPullRequestDraftTranslationActivitiesUseCase;
        this.translatePullRequestDraftUseCase = translatePullRequestDraftUseCase;
    }

    /**
     * 저장소/이슈/브랜치/diff 입력값을 받아 저장된 PR 제목과 본문 초안을 반환합니다.
     */
    @PostMapping
    ApiResponse<StoredPullRequestDraft> create(@Valid @RequestBody CreatePullRequestDraftRequest request) {
        try {
            return ApiResponse.success(createPullRequestDraftUseCase.create(request.toDomain()));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "GitHub compare diff를 불러오지 못했습니다. base/head 브랜치와 저장소 접근 권한을 확인해 주세요.",
                exception
            );
        }
    }

    /**
     * 최근 생성된 PR 초안 목록을 반환합니다.
     */
    @GetMapping
    ApiResponse<List<StoredPullRequestDraft>> list() {
        return ApiResponse.success(listPullRequestDraftsUseCase.listRecentPullRequestDrafts());
    }

    /**
     * 저장된 PR 초안 이력을 페이지 단위로 반환합니다.
     */
    @GetMapping("/history")
    ApiResponse<PullRequestDraftHistoryPage> history(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(listPullRequestDraftHistoryUseCase.listPullRequestDraftHistory(
            new PullRequestDraftHistoryQuery(page, size)
        ));
    }

    /**
     * Dashboard 최근 활동에 표시할 PR 초안 번역 결과를 반환합니다.
     */
    @GetMapping("/translations/activity")
    ApiResponse<List<PullRequestDraftTranslationActivity>> translationActivities(
        @RequestParam(defaultValue = "4") int limit
    ) {
        return ApiResponse.success(listPullRequestDraftTranslationActivitiesUseCase
            .listRecentTranslationActivities(limit));
    }

    /**
     * 저장된 PR 초안 ID로 상세 내용을 조회합니다.
     */
    @GetMapping("/{id}")
    ApiResponse<StoredPullRequestDraft> detail(@PathVariable String id) {
        StoredPullRequestDraft draft = getPullRequestDraftUseCase.getPullRequestDraft(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다."));

        return ApiResponse.success(draft);
    }

    /**
     * 저장된 PR 초안의 제목과 본문을 사용자가 다듬은 값으로 갱신합니다.
     */
    @PatchMapping("/{id}")
    ApiResponse<StoredPullRequestDraft> update(
        @PathVariable String id,
        @Valid @RequestBody UpdatePullRequestDraftRequestBody request
    ) {
        StoredPullRequestDraft draft = updatePullRequestDraftUseCase
            .updatePullRequestDraft(id, request.toDomain())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다."));

        return ApiResponse.success(draft);
    }

    /**
     * 저장된 PR 초안과 연결된 번역 결과를 함께 삭제합니다.
     */
    @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable String id) {
        boolean deleted = deletePullRequestDraftUseCase.deletePullRequestDraft(id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다.");
        }

        return ApiResponse.success(null);
    }

    /**
     * 저장된 PR 초안의 upstream 제출 전 준비 상태를 checklist로 반환합니다.
     */
    @GetMapping("/{id}/readiness")
    ApiResponse<PullRequestDraftReadiness> readiness(@PathVariable String id) {
        PullRequestDraftReadiness readiness = getPullRequestDraftReadinessUseCase.getReadiness(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다."));

        return ApiResponse.success(readiness);
    }

    /**
     * 저장된 PR 초안의 제출 준비 흐름을 timeline으로 반환합니다.
     */
    @GetMapping("/{id}/timeline")
    ApiResponse<PullRequestDraftTimeline> timeline(@PathVariable String id) {
        PullRequestDraftTimeline timeline = getPullRequestDraftTimelineUseCase.getTimeline(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다."));

        return ApiResponse.success(timeline);
    }

    /**
     * 저장된 PR 초안의 제출 전 상태를 report로 반환합니다.
     */
    @GetMapping("/{id}/report")
    ApiResponse<PullRequestDraftContributionReport> report(@PathVariable String id) {
        PullRequestDraftContributionReport report = getPullRequestDraftContributionReportUseCase
            .getContributionReport(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다."));

        return ApiResponse.success(report);
    }

    /**
     * 저장된 PR 초안을 제출 직전에 복사할 수 있는 bundle로 반환합니다.
     */
    @GetMapping("/{id}/submission")
    ApiResponse<PullRequestDraftSubmission> submission(@PathVariable String id) {
        PullRequestDraftSubmission submission = getPullRequestDraftSubmissionUseCase.getSubmission(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다."));

        return ApiResponse.success(submission);
    }

    /**
     * 저장된 PR 초안을 대상 언어의 제출용 초안으로 변환해 저장합니다.
     */
    @PostMapping("/{id}/translate")
    ApiResponse<PullRequestDraftTranslateResult> translate(@PathVariable String id) {
        PullRequestDraftTranslateResult result = translatePullRequestDraftUseCase.translate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다."));

        return ApiResponse.success(result);
    }

    /**
     * 저장된 PR 초안을 대상 언어의 제출용 초안으로 변환해 저장합니다.
     */
    @PostMapping("/{id}/translations")
    ApiResponse<PullRequestDraftTranslation> createTranslation(
        @PathVariable String id,
        @Valid @RequestBody CreatePullRequestDraftTranslationRequest request
    ) {
        PullRequestDraftTranslation translation = createPullRequestDraftTranslationUseCase
            .createTranslation(id, request.targetLanguage())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다."));

        return ApiResponse.success(translation);
    }

    /**
     * 저장된 PR 초안의 번역 결과 목록을 반환합니다.
     */
    @GetMapping("/{id}/translations")
    ApiResponse<List<PullRequestDraftTranslation>> translations(@PathVariable String id) {
        getPullRequestDraftUseCase.getPullRequestDraft(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안을 찾을 수 없습니다."));

        return ApiResponse.success(listPullRequestDraftTranslationsUseCase.listTranslations(id));
    }

    /**
     * 저장된 PR 초안 번역 결과를 제출 직전에 복사할 수 있는 bundle로 반환합니다.
     */
    @GetMapping("/{id}/translations/{translationId}/submission")
    ApiResponse<PullRequestDraftTranslationSubmission> translationSubmission(
        @PathVariable String id,
        @PathVariable String translationId
    ) {
        PullRequestDraftTranslationSubmission submission = getPullRequestDraftTranslationSubmissionUseCase
            .getTranslationSubmission(id, translationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안 번역을 찾을 수 없습니다."));

        return ApiResponse.success(submission);
    }

    /**
     * 저장된 PR 초안 번역 결과의 upstream 제출 전 준비 상태를 checklist로 반환합니다.
     */
    @GetMapping("/{id}/translations/{translationId}/readiness")
    ApiResponse<PullRequestDraftTranslationReadiness> translationReadiness(
        @PathVariable String id,
        @PathVariable String translationId
    ) {
        PullRequestDraftTranslationReadiness readiness = getPullRequestDraftTranslationReadinessUseCase
            .getTranslationReadiness(id, translationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안 번역을 찾을 수 없습니다."));

        return ApiResponse.success(readiness);
    }

    /**
     * 저장된 PR 초안 번역 결과의 제출 준비 흐름을 timeline으로 반환합니다.
     */
    @GetMapping("/{id}/translations/{translationId}/timeline")
    ApiResponse<PullRequestDraftTranslationTimeline> translationTimeline(
        @PathVariable String id,
        @PathVariable String translationId
    ) {
        PullRequestDraftTranslationTimeline timeline = getPullRequestDraftTranslationTimelineUseCase
            .getTranslationTimeline(id, translationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PR 초안 번역을 찾을 수 없습니다."));

        return ApiResponse.success(timeline);
    }

    /**
     * web 요청 body를 domain 요청 모델로 변환하기 전 필수 식별자만 검증하는 DTO입니다.
     */
    record CreatePullRequestDraftRequest(
        @NotBlank(message = "upstreamRepo는 비어 있을 수 없습니다.")
        String upstreamRepo,
        @Positive(message = "githubIssueNumber는 1 이상이어야 합니다.")
        long githubIssueNumber,
        @NotBlank(message = "baseBranch는 비어 있을 수 없습니다.")
        String baseBranch,
        @NotBlank(message = "headBranch는 비어 있을 수 없습니다.")
        String headBranch,
        String diffContent,
        String sourceIssueId
    ) {

        PullRequestDraftRequest toDomain() {
            return new PullRequestDraftRequest(
                upstreamRepo,
                githubIssueNumber,
                baseBranch,
                headBranch,
                diffContent,
                sourceIssueId
            );
        }
    }

    /**
     * web 요청 body에서 현재 지원하는 번역 대상 언어를 검증합니다.
     */
    record CreatePullRequestDraftTranslationRequest(
        @NotBlank(message = "targetLanguage는 비어 있을 수 없습니다.")
        @Pattern(regexp = "en", message = "targetLanguage는 en만 지원합니다.")
        String targetLanguage
    ) {
    }

    /**
     * web 요청 body를 사용자가 수정한 PR 초안 domain 요청 모델로 변환합니다.
     */
    record UpdatePullRequestDraftRequestBody(
        @NotBlank(message = "title은 비어 있을 수 없습니다.")
        String title,
        @NotBlank(message = "body는 비어 있을 수 없습니다.")
        String body
    ) {

        PullRequestDraftUpdateRequest toDomain() {
            return new PullRequestDraftUpdateRequest(title, body);
        }
    }
}
