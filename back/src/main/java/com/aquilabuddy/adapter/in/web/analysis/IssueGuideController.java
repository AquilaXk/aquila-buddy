package com.aquilabuddy.adapter.in.web.analysis;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.application.port.in.analysis.GetIssuePseudoCodeUseCase;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideStreamChunk;
import com.aquilabuddy.domain.analysis.IssueGuideWorkflow;
import com.aquilabuddy.domain.analysis.IssuePseudoCode;
import com.aquilabuddy.global.response.ApiResponse;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * [Incoming Web Adapter] 특정 이슈의 작업 가이드 조회 및 실시간 SSE 스트리밍 API를 처리하는 REST controller입니다.
 */
@RestController
@RequestMapping("/api/v1/issues")
public class IssueGuideController {

    private final GetIssueGuideUseCase getIssueGuideUseCase;
    private final GetIssuePseudoCodeUseCase getIssuePseudoCodeUseCase;
    private final ExecutorService sseExecutor;

    @Autowired
    public IssueGuideController(
        GetIssueGuideUseCase getIssueGuideUseCase,
        GetIssuePseudoCodeUseCase getIssuePseudoCodeUseCase
    ) {
        this(getIssueGuideUseCase, getIssuePseudoCodeUseCase, Executors.newVirtualThreadPerTaskExecutor());
    }

    IssueGuideController(
        GetIssueGuideUseCase getIssueGuideUseCase,
        GetIssuePseudoCodeUseCase getIssuePseudoCodeUseCase,
        ExecutorService sseExecutor
    ) {
        this.getIssueGuideUseCase = getIssueGuideUseCase;
        this.getIssuePseudoCodeUseCase = getIssuePseudoCodeUseCase;
        this.sseExecutor = sseExecutor == null ? Executors.newVirtualThreadPerTaskExecutor() : sseExecutor;
    }

    @PreDestroy
    void shutdownStreamExecutor() {
        sseExecutor.close();
    }

    /**
     * 이슈 식별자에 해당하는 작업 가이드를 공통 응답 포맷으로 반환합니다.
     */
    @GetMapping("/{issueId}/guide")
    ApiResponse<IssueGuide> guide(@PathVariable String issueId) {
        return ApiResponse.success(getIssueGuideUseCase.findByIssueId(issueId));
    }

    /**
     * 이슈 가이드 생성 과정을 SSE(Server-Sent Events) 청크 단위로 실시간 스트리밍합니다.
     */
    @GetMapping(value = "/{issueId}/guide/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGuide(@PathVariable String issueId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        SecurityContext securityContext = SecurityContextHolder.getContext();

        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> emitter.complete());

        sseExecutor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                getIssueGuideUseCase.streamByIssueId(issueId, chunk -> sendChunk(emitter, chunk));
            } catch (Exception exception) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of(
                            "message", exception.getMessage() != null
                                ? exception.getMessage()
                                : "가이드 스트리밍 중 오류가 발생했습니다."
                        )));
                    emitter.completeWithError(exception);
                } catch (Exception ignored) {
                    // 클라이언트 연결이 이미 끊어진 경우 조용히 정리합니다.
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    /**
     * 이슈 가이드를 실행 workflow 단계로 변환해 반환합니다.
     */
    @GetMapping("/{issueId}/guide/workflow")
    ApiResponse<IssueGuideWorkflow> workflow(@PathVariable String issueId) {
        return ApiResponse.success(getIssueGuideUseCase.findWorkflowByIssueId(issueId));
    }

    /**
     * 이슈 가이드에서 의사코드만 분리한 projection을 반환합니다.
     */
    @GetMapping("/{issueId}/pseudo")
    ApiResponse<IssuePseudoCode> pseudoCode(@PathVariable String issueId) {
        return ApiResponse.success(getIssuePseudoCodeUseCase.findPseudoCodeByIssueId(issueId));
    }

    private static void sendChunk(SseEmitter emitter, IssueGuideStreamChunk chunk) {
        if (chunk == null) {
            return;
        }
        try {
            switch (chunk.step()) {
                case ANALYZING -> emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(Map.of(
                        "step", "ANALYZING",
                        "message", chunk.message() == null ? "" : chunk.message()
                    )));
                case FILES -> emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(Map.of(
                        "step", "FILES",
                        "filePaths", chunk.filePaths() == null ? List.of() : chunk.filePaths()
                    )));
                case GUIDELINE -> emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(Map.of(
                        "step", "GUIDELINE",
                        "chunk", chunk.chunk() == null ? "" : chunk.chunk()
                    )));
                case PSEUDO_CODE -> emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(Map.of(
                        "step", "PSEUDO_CODE",
                        "chunk", chunk.chunk() == null ? "" : chunk.chunk()
                    )));
                case SIDE_EFFECTS -> {
                    if (chunk.sideEffects() != null) {
                        emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data(Map.of(
                                "step", "SIDE_EFFECTS",
                                "sideEffects", chunk.sideEffects()
                            )));
                    } else if (chunk.chunk() != null) {
                        emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data(Map.of(
                                "step", "SIDE_EFFECTS",
                                "chunk", chunk.chunk()
                            )));
                    }
                }
                case COMPLETE -> {
                    if (chunk.completedGuide() != null) {
                        emitter.send(SseEmitter.event()
                            .name("complete")
                            .data(chunk.completedGuide()));
                        emitter.complete();
                    }
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
