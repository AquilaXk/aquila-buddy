package com.aquilabuddy.adapter.out.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideStreamChunk;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.analysis.RepositoryCodeContextFile;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AiIssueGuideGenerationAdapterTest {

    @Test
    void generatesIssueGuideFromAiResponseMarkers() {
        CapturingAiClient aiClient = new CapturingAiClient(String.join("\n",
            "GUIDELINE: Validate the request contract before changing implementation.",
            "PSEUDO_CODE:",
            "GIVEN issue = \"Refine validation error example\"",
            "WHEN invalid input reaches validation entry point",
            "CALL addRegressionTest(issue)",
            "ASSERT response message explains invalid input",
            "SIDE_EFFECTS:",
            "- API error wording can change",
            "- Documentation examples may need the same term"
        ));
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(aiClient);

        IssueGuide guide = adapter.generateIssueGuide(issue());

        assertThat(guide.issueKey()).isEqualTo("demo-2");
        assertThat(guide.filePaths()).isEmpty();
        assertThat(guide.guideline()).contains("Validate the request contract");
        assertThat(guide.pseudoCode()).contains("GIVEN").contains("ASSERT");
        assertThat(guide.sideEffects()).containsExactly(
            "API error wording can change",
            "Documentation examples may need the same term"
        );
        assertThat(aiClient.systemPrompt).contains("issue guide");
        assertThat(aiClient.userPrompt)
            .contains("spring-projects/spring-boot")
            .contains("Refine validation error example")
            .contains("good first issue, validation");
    }

    @Test
    void includesRepositoryCodeContextInPromptWhenAvailable() {
        CapturingAiClient aiClient = new CapturingAiClient(String.join("\n",
            "GUIDELINE: ValidationController에서 invalid input 실패 조건을 재현 테스트로 고정한 뒤 API 응답 메시지를 수정하고 controller 테스트로 검증합니다.",
            "PSEUDO_CODE:",
            "GIVEN controller = ValidationController",
            "WHEN invalid input reaches controller",
            "CALL updateValidationMessage(controller)",
            "ASSERT response uses clearer detail",
            "SIDE_EFFECTS:",
            "- Controller response wording can change"
        ));
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(aiClient);
        RepositoryCodeContext codeContext = RepositoryCodeContext.fromFiles(
            "spring-projects/spring-boot",
            "main",
            List.of(new RepositoryCodeContextFile("src/main/java/ValidationController.java", "class ValidationController {}")),
            3,
            200
        );

        IssueGuide guide = adapter.generateIssueGuide(issue(), codeContext);

        assertThat(aiClient.systemPrompt)
            .contains("Base GUIDELINE, PSEUDO_CODE, and SIDE_EFFECTS on repository code context when it is present.");
        assertThat(aiClient.userPrompt)
            .contains("Repository code context: spring-projects/spring-boot@main")
            .contains("File: src/main/java/ValidationController.java")
            .contains("class ValidationController {}");
        assertThat(guide.filePaths()).containsExactly("src/main/java/ValidationController.java");
    }

    @Test
    void failsClosedWhenPseudoCodeIsNumberedChecklistInsteadOfCodeLikeFlow() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient(String.join("\n",
            "GUIDELINE: IssueGuideController에서 Refine validation error example 실패 조건을 재현 테스트로 고정한 뒤 응답 예시 문구를 수정하고 controller 테스트로 검증합니다.",
            "PSEUDO_CODE:",
            "1. IssueGuideController를 연다.",
            "2. guide 흐름을 확인한다.",
            "3. 테스트를 추가한다.",
            "SIDE_EFFECTS:",
            "- IssueGuideController 응답이 바뀔 수 있습니다."
        )));

        assertThatThrownBy(() -> adapter.generateIssueGuide(issue(), issueGuideControllerContext()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PSEUDO_CODE");
    }

    @Test
    void failsClosedWhenGuidelineOmitsIssueSpecificFailureAndVerification() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient(String.join("\n",
            "GUIDELINE: IssueGuideController의 guide, workflow, pseudoCode 흐름을 확인합니다.",
            "PSEUDO_CODE:",
            "GIVEN controller = IssueGuideController",
            "CALL controller.guide(\"demo-1\")",
            "ASSERT response.success == true",
            "SIDE_EFFECTS:",
            "- IssueGuideController 응답이 바뀔 수 있습니다."
        )));

        assertThatThrownBy(() -> adapter.generateIssueGuide(issue(), issueGuideControllerContext()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GUIDELINE");
    }

    @Test
    void failsClosedWhenGuidelineRepeatsSymbolsAndGenericControllerWarnings() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient(String.join("\n",
            "GUIDELINE: ProductListTest.java의 ProductListTest와 shouldRenderProductList(), of(), setVariable(), assertMatchesSnapshot(), shouldRenderEmptyProductList() 흐름에서 \"Template/inlineTemplate validation uses isEmpty() instead of isBlank()\" 실패 조건을 먼저 재현 테스트로 고정합니다.",
            "PSEUDO_CODE:",
            "GIVEN target_file = \"sample/src/test/java/com/github/suzumiyaaoba/thymeleaf/snapshot/sample/ProductListTest.java\"",
            "WHEN issue_failure = \"Template/inlineTemplate validation uses isEmpty() instead of isBlank()\" is reproduced",
            "CALL preserve_existing_flow(target_symbol, \"shouldRenderProductList()\")",
            "ASSERT normal_input and failing_input pass",
            "SIDE_EFFECTS:",
            "- ProductListTest.java의 응답 계약이 바뀌면 관련 controller/service 테스트 기대값도 함께 바뀔 수 있습니다."
        )));

        assertThatThrownBy(() -> adapter.generateIssueGuide(thymeleafIssue(), thymeleafSnapshotContext()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsClosedWhenPseudoCodeUsesAbstractPlaceholders() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient(String.join("\n",
            "GUIDELINE: 원인: 공백 문자열이 isEmpty() 검증을 통과해 isBlank() 기준이 필요한 입력이 뒤늦게 실패합니다. 수정 방향: TemplateSpec의 검증 분기를 isBlank() 기준으로 바꾸고 정상 입력은 유지합니다. 첫 검증: ProductListTest에서 공백 입력과 정상 템플릿 입력을 함께 확인합니다.",
            "PSEUDO_CODE:",
            "GIVEN target_file = \"src/main/java/com/github/suzumiyaaoba/thymeleaf/snapshot/TemplateSpec.java\"",
            "WHEN issue_failure = \"Template/inlineTemplate validation uses isEmpty() instead of isBlank()\" is reproduced",
            "CALL preserve_existing_flow(target_symbol, \"of(), setVariable(), assertMatchesSnapshot()\")",
            "CALL apply_smallest_change(target_file, issue_failure, label=\"bug\")",
            "ASSERT \"of(), setVariable(), assertMatchesSnapshot()\" handles normal_input and failing_input",
            "SIDE_EFFECTS:",
            "- 공백 문자열을 실패 입력으로 바꾸면 기존 빈 문자열 처리 테스트가 영향을 받을 수 있습니다."
        )));

        assertThatThrownBy(() -> adapter.generateIssueGuide(thymeleafIssue(), thymeleafSnapshotContext()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("abstract placeholder");
    }

    @Test
    void failsClosedWhenAiResponseWithoutRequiredMarkers() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient("plain text"));

        assertThatThrownBy(() -> adapter.generateIssueGuide(issue(), RepositoryCodeContext.fromFiles(
            "spring-projects/spring-boot",
            "main",
            List.of(new RepositoryCodeContextFile("src/main/java/ValidationController.java", "class ValidationController {}")),
            3,
            200
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("markers");
    }

    @Test
    void failsClosedWhenAiResponseDoesNotReferenceRepositoryCodeContext() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient(String.join("\n",
            "GUIDELINE: 입력 검증 실패 조건을 테스트로 고정한 뒤 오류 메시지를 수정하고 회귀 테스트로 검증합니다.",
            "PSEUDO_CODE:",
            "GIVEN validation = \"error example\"",
            "CALL verifyValidation()",
            "ASSERT error message matches contract",
            "SIDE_EFFECTS:",
            "- 검증 메시지 문구가 바뀌면 관련 문서와 테스트 기대값도 함께 바뀔 수 있습니다."
        )));

        assertThatThrownBy(() -> adapter.generateIssueGuide(issue(), issueGuideControllerContext()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("repository code context");
    }

    @Test
    void failsClosedWhenAiClientThrows() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter((systemPrompt, userPrompt) -> {
            throw new IllegalStateException("provider timeout");
        });

        assertThatThrownBy(() -> adapter.generateIssueGuide(issue(), issueGuideControllerContext()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("provider timeout");
    }

    @Test
    void streamsSectionDeltasAcrossSplitMarkersWithoutLeakingMarkerTokens() {
        List<String> tokenDeltas = List.of(
            "GUI",
            "DELINE: Validation",
            "Controller에서 validation error example 실패 조건을 수정하고 regression ",
            "test로 검증합니다.\nPSEU",
            "DO_CODE:\nGIVEN controller = Validation",
            "Controller\nWHEN invalid input arrives\nCALL validate()\nASSERT 400",
            "\nSIDE_",
            "EFFECTS:\n- Existing API clients may see updated error text.\n- Docs should match."
        );
        AiClient streamingAiClient = new AiClient() {
            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new AssertionError("complete should not be called during stream");
            }

            @Override
            public String stream(AiCompletionUseCase useCase, String systemPrompt, String userPrompt, Consumer<String> onDelta) {
                tokenDeltas.forEach(onDelta);
                return String.join("", tokenDeltas);
            }
        };
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(streamingAiClient);
        RepositoryCodeContext codeContext = RepositoryCodeContext.fromFiles(
            "spring-projects/spring-boot",
            "main",
            List.of(new RepositoryCodeContextFile("src/main/java/ValidationController.java", "class ValidationController {}")),
            5,
            3000
        );
        List<IssueGuideStreamChunk> emitted = new ArrayList<>();

        IssueGuide guide = adapter.streamIssueGuide(issue(), codeContext, emitted::add);

        String streamedGuideline = emitted.stream()
            .filter(chunk -> chunk.step() == IssueGuideStreamChunk.Step.GUIDELINE)
            .map(IssueGuideStreamChunk::chunk)
            .collect(java.util.stream.Collectors.joining());
        String streamedPseudoCode = emitted.stream()
            .filter(chunk -> chunk.step() == IssueGuideStreamChunk.Step.PSEUDO_CODE)
            .map(IssueGuideStreamChunk::chunk)
            .collect(java.util.stream.Collectors.joining());
        String streamedSideEffects = emitted.stream()
            .filter(chunk -> chunk.step() == IssueGuideStreamChunk.Step.SIDE_EFFECTS)
            .map(IssueGuideStreamChunk::chunk)
            .collect(java.util.stream.Collectors.joining());

        assertThat(streamedGuideline)
            .isEqualTo("ValidationController에서 validation error example 실패 조건을 수정하고 regression test로 검증합니다.")
            .doesNotContain("GUIDELINE:")
            .doesNotContain("PSEUDO_CODE:");
        assertThat(streamedPseudoCode)
            .contains("GIVEN controller = ValidationController")
            .doesNotContain("PSEUDO_CODE:")
            .doesNotContain("SIDE_EFFECTS:");
        assertThat(streamedSideEffects)
            .contains("Existing API clients may see updated error text.")
            .doesNotContain("SIDE_EFFECTS:");
        assertThat(guide.guideline()).isEqualTo(streamedGuideline);
        assertThat(guide.sideEffects()).hasSize(2);
    }

    private static RecommendedIssue issue() {
        return new RecommendedIssue(
            "demo-2",
            "spring-projects/spring-boot",
            2L,
            "Refine validation error example",
            "Java/Spring validation 예시 개선 이슈입니다.",
            0.87,
            List.of("good first issue", "validation"),
            "OPEN",
            "현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다."
        );
    }

    private static RecommendedIssue thymeleafIssue() {
        return new RecommendedIssue(
            "thymeleaf-6",
            "suzumiyaaoba/thymeleaf-snapshot",
            6L,
            "Template/inlineTemplate validation uses isEmpty() instead of isBlank()",
            "공백 문자열이 isEmpty() 검증을 통과해 Thymeleaf 처리 중 뒤늦게 실패하는 문제입니다.",
            0.9,
            List.of("bug", "good first issue"),
            "OPEN",
            "bug 라벨과 good first issue 범위가 맞아 검증 조건을 작게 재현하고 수정하기 좋습니다."
        );
    }

    private static RepositoryCodeContext thymeleafSnapshotContext() {
        return RepositoryCodeContext.fromFiles(
            "suzumiyaaoba/thymeleaf-snapshot",
            "main",
            List.of(
                new RepositoryCodeContextFile(
                    "sample/src/test/java/com/github/suzumiyaaoba/thymeleaf/snapshot/sample/ProductListTest.java",
                    """
                        class ProductListTest {
                            void shouldRenderProductList() {}
                            void shouldRenderEmptyProductList() {}
                        }
                        """
                ),
                new RepositoryCodeContextFile(
                    "src/main/java/com/github/suzumiyaaoba/thymeleaf/snapshot/TemplateSpec.java",
                    """
                        class TemplateSpec {
                            static TemplateSpec of(String template, String inlineTemplate) {
                                if (template.isEmpty() && inlineTemplate.isEmpty()) {
                                    throw new IllegalArgumentException("template required");
                                }
                                return new TemplateSpec();
                            }
                        }
                        """
                )
            ),
            3,
            2_000
        );
    }

    private static RepositoryCodeContext issueGuideControllerContext() {
        return RepositoryCodeContext.fromFiles(
            "AquilaXk/aquila-buddy",
            "main",
            List.of(new RepositoryCodeContextFile(
                "back/src/main/java/com/aquilabuddy/adapter/in/web/analysis/IssueGuideController.java",
                """
                    public class IssueGuideController {
                        ApiResponse<IssueGuide> guide(String issueId) {
                            return ApiResponse.success(getIssueGuideUseCase.findByIssueId(issueId));
                        }

                        ApiResponse<IssueGuideWorkflow> workflow(String issueId) {
                            return ApiResponse.success(getIssueGuideUseCase.findWorkflowByIssueId(issueId));
                        }

                        ApiResponse<IssuePseudoCode> pseudoCode(String issueId) {
                            return ApiResponse.success(getIssuePseudoCodeUseCase.findPseudoCodeByIssueId(issueId));
                        }
                    }
                    """
            )),
            3,
            2_000
        );
    }

    private static class CapturingAiClient implements AiClient {

        private final String response;
        private String systemPrompt;
        private String userPrompt;

        private CapturingAiClient(String response) {
            this.response = response;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return response;
        }
    }
}
