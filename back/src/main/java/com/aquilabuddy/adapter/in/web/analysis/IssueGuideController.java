package com.aquilabuddy.adapter.in.web.analysis;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.application.port.in.analysis.GetIssuePseudoCodeUseCase;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssuePseudoCode;
import com.aquilabuddy.domain.analysis.IssueGuideWorkflow;
import com.aquilabuddy.global.response.ApiResponse;
import java.io.IOException;
import java.util.ArrayList;
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
        // 비동기 가상 스레드에서도 인증 주체 정보를 조회할 수 있도록 현재 SecurityContext를 전파합니다.
        SecurityContext securityContext = SecurityContextHolder.getContext();

        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> emitter.complete());

        sseExecutor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                // 1. 분석 개시 상태 전송
                emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(Map.of(
                        "step", "ANALYZING",
                        "message", "저장소 코드 맥락 분석 중..."
                    )));

                // 2. 작업 가이드 조회/생성
                IssueGuide guide = getIssueGuideUseCase.findByIssueId(issueId);

                // 3. 수정 대상 파일 목록 전송
                emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(Map.of(
                        "step", "FILES",
                        "filePaths", guide.filePaths() != null ? guide.filePaths() : List.of()
                    )));

                // 4. 가이드라인 텍스트 청크 스트리밍
                if (guide.guideline() != null && !guide.guideline().isBlank()) {
                    List<String> guidelineChunks = splitTextIntoChunks(guide.guideline(), 40);
                    for (String chunk : guidelineChunks) {
                        emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data(Map.of(
                                "step", "GUIDELINE",
                                "chunk", chunk
                            )));
                    }
                }

                // 5. 의사 코드 청크 스트리밍
                if (guide.pseudoCode() != null && !guide.pseudoCode().isBlank()) {
                    List<String> pseudoChunks = splitLinesIntoChunks(guide.pseudoCode());
                    for (String chunk : pseudoChunks) {
                        emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data(Map.of(
                                "step", "PSEUDO_CODE",
                                "chunk", chunk
                            )));
                    }
                }

                // 6. 주의 사항 및 부작용 전송
                emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(Map.of(
                        "step", "SIDE_EFFECTS",
                        "sideEffects", guide.sideEffects() != null ? guide.sideEffects() : List.of()
                    )));

                // 7. 스트리밍 완료 이벤트 (최종 정규화 객체 전송)
                emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(guide));

                emitter.complete();
            } catch (Exception exception) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of(
                            "message", exception.getMessage() != null ? exception.getMessage() : "가이드 스트리밍 중 오류가 발생했습니다."
                        )));
                    emitter.completeWithError(exception);
                } catch (Exception ignored) {
                    // 클라이언트 연결이 이미 끊어졌거나 완료된 경우 조용히 흡수합니다.
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

    private static List<String> splitTextIntoChunks(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int i = 0;
        while (i < length) {
            int end = Math.min(length, i + maxChunkSize);
            // UTF-16 surrogate pair가 청크 경계에서 잘리지 않도록 보정합니다.
            if (end < length && Character.isHighSurrogate(text.charAt(end - 1))) {
                end++;
            }
            chunks.add(text.substring(i, end));
            i = end;
        }
        return chunks;
    }

    private static List<String> splitLinesIntoChunks(String text) {
        String[] lines = text.split("\\R");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            current.append(line).append("\n");
            if (current.length() >= 60) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
