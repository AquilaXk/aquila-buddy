package com.aquilabuddy.adapter.out.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.analysis.RepositoryCodeContextFile;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
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
    void fallsBackWhenPseudoCodeIsNumberedChecklistInsteadOfCodeLikeFlow() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient(String.join("\n",
            "GUIDELINE: IssueGuideController에서 Refine validation error example 실패 조건을 재현 테스트로 고정한 뒤 응답 예시 문구를 수정하고 controller 테스트로 검증합니다.",
            "PSEUDO_CODE:",
            "1. IssueGuideController를 연다.",
            "2. guide 흐름을 확인한다.",
            "3. 테스트를 추가한다.",
            "SIDE_EFFECTS:",
            "- IssueGuideController 응답이 바뀔 수 있습니다."
        )));

        IssueGuide guide = adapter.generateIssueGuide(issue(), issueGuideControllerContext());

        assertThat(guide.pseudoCode())
            .contains("GIVEN")
            .contains("CALL")
            .contains("ASSERT")
            .doesNotContain("1. IssueGuideController를 연다.");
    }

    @Test
    void fallsBackWhenGuidelineOmitsIssueSpecificFailureAndVerification() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient(String.join("\n",
            "GUIDELINE: IssueGuideController의 guide, workflow, pseudoCode 흐름을 확인합니다.",
            "PSEUDO_CODE:",
            "1. IssueGuideController를 연다.",
            "2. guide 흐름을 확인한다.",
            "SIDE_EFFECTS:",
            "- IssueGuideController 응답이 바뀔 수 있습니다."
        )));

        IssueGuide guide = adapter.generateIssueGuide(issue(), issueGuideControllerContext());

        assertThat(guide.guideline())
            .contains("IssueGuideController")
            .contains("Refine validation error example")
            .contains("재현 테스트")
            .doesNotContain("guide, workflow, pseudoCode 흐름을 확인합니다");
    }

    @Test
    void fallsBackToRuleBasedGuideWhenAiResponseWithoutRequiredMarkers() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient("plain text"));

        IssueGuide guide = adapter.generateIssueGuide(issue(), RepositoryCodeContext.fromFiles(
            "spring-projects/spring-boot",
            "main",
            List.of(new RepositoryCodeContextFile("src/main/java/ValidationController.java", "class ValidationController {}")),
            3,
            200
        ));

        assertThat(guide.issueKey()).isEqualTo("demo-2");
        assertThat(guide.filePaths()).containsExactly("src/main/java/ValidationController.java");
        assertThat(guide.guideline()).isNotBlank();
        assertThat(guide.pseudoCode()).isNotBlank();
        assertThat(guide.sideEffects()).isNotEmpty();
    }

    @Test
    void fallsBackWhenAiResponseDoesNotReferenceRepositoryCodeContext() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient(String.join("\n",
            "GUIDELINE: 입력 검증 흐름을 따라가며 실패 응답 메시지와 예시 코드를 함께 정리합니다.",
            "PSEUDO_CODE:",
            "1. 검증 대상 입력과 실패 조건을 확인한다.",
            "2. 현재 실패 응답 형식을 테스트로 고정한다.",
            "3. 예시 코드와 설명을 같은 용어로 맞춘다.",
            "4. 정상 입력과 실패 입력을 모두 재검증한다.",
            "SIDE_EFFECTS:",
            "- 검증 메시지 문구가 바뀌면 관련 문서와 테스트 기대값도 함께 바뀔 수 있습니다.",
            "- 동일 응답 형식을 사용하는 화면의 에러 표시 문구를 확인해야 합니다."
        )));

        IssueGuide guide = adapter.generateIssueGuide(issue(), issueGuideControllerContext());

        assertThat(guide.filePaths())
            .containsExactly("back/src/main/java/com/aquilabuddy/adapter/in/web/analysis/IssueGuideController.java");
        assertThat(guide.guideline()).contains("IssueGuideController");
        assertThat(guide.pseudoCode()).contains("guide").contains("workflow").contains("pseudoCode");
        assertThat(guide.sideEffects()).anySatisfy(sideEffect -> assertThat(sideEffect).contains("IssueGuideController"));
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
