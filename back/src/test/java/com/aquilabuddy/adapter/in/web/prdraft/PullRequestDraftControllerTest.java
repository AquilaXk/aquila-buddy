package com.aquilabuddy.adapter.in.web.prdraft;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftDiffPort;
import com.jayway.jsonpath.JsonPath;
import com.aquilabuddy.adapter.out.persistence.prdraft.PullRequestDraftJpaRepository;
import com.aquilabuddy.adapter.out.persistence.prdraft.PullRequestDraftTranslationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * PR 초안 API가 저장된 초안을 생성하고 목록/상세로 재조회하는지 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PullRequestDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PullRequestDraftJpaRepository repository;

    @Autowired
    private PullRequestDraftTranslationJpaRepository translationRepository;

    @BeforeEach
    void setUp() {
        translationRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void createsPullRequestDraft() throws Exception {
        mockMvc.perform(post("/api/v1/pr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "upstreamRepo": "spring-projects/spring-boot",
                      "githubIssueNumber": 2,
                      "baseBranch": "main",
                      "headBranch": "feat/validation-docs",
                      "diffContent": "diff --git a/docs.adoc b/docs.adoc\\n+Add validation example"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.upstreamRepo").value("spring-projects/spring-boot"))
            .andExpect(jsonPath("$.data.githubIssueNumber").value(2))
            .andExpect(jsonPath("$.data.title").value("docs: spring-projects/spring-boot#2 변경 초안"))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("spring-projects/spring-boot #2")))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("main -> feat/validation-docs")))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("Add validation example")));
    }

    @Test
    void createsPullRequestDraftWithoutClientProvidedDiffContent() throws Exception {
        mockMvc.perform(post("/api/v1/pr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "upstreamRepo": "spring-projects/spring-boot",
                      "githubIssueNumber": 2,
                      "baseBranch": "main",
                      "headBranch": "feat/validation-docs"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.upstreamRepo").value("spring-projects/spring-boot"))
            .andExpect(jsonPath("$.data.title").value("docs: spring-projects/spring-boot#2 변경 초안"))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("Loaded from GitHub compare")));
    }

    @Test
    void listsPullRequestDrafts() throws Exception {
        String id = createDraft();

        mockMvc.perform(get("/api/v1/pr"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(id))
            .andExpect(jsonPath("$.data[0].title").value("docs: spring-projects/spring-boot#2 변경 초안"));
    }

    @Test
    void returnsPullRequestDraftHistoryPage() throws Exception {
        String oldestId = createDraft(2, "feat/oldest");
        String middleId = createDraft(3, "feat/middle");
        String newestId = createDraft(4, "feat/newest");

        mockMvc.perform(get("/api/v1/pr/history")
                .param("page", "0")
                .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].id").value(newestId))
            .andExpect(jsonPath("$.data.content[1].id").value(middleId))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.first").value(true))
            .andExpect(jsonPath("$.data.last").value(false));

        mockMvc.perform(get("/api/v1/pr/history")
                .param("page", "1")
                .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(oldestId))
            .andExpect(jsonPath("$.data.first").value(false))
            .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void returnsPullRequestDraftDetail() throws Exception {
        String id = createDraft();

        mockMvc.perform(get("/api/v1/pr/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(id))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("main -> feat/validation-docs")));
    }

    @Test
    void updatesPullRequestDraft() throws Exception {
        String id = createDraft();

        mockMvc.perform(patch("/api/v1/pr/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "docs: update contributor guide",
                      "body": "## Summary\\n- Update contributor guide"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(id))
            .andExpect(jsonPath("$.data.title").value("docs: update contributor guide"))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("Update contributor guide")));

        mockMvc.perform(get("/api/v1/pr/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("docs: update contributor guide"));
    }

    @Test
    void deletesPullRequestDraft() throws Exception {
        String id = createDraft();

        mockMvc.perform(delete("/api/v1/pr/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/pr/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsPullRequestDraftReadiness() throws Exception {
        String id = createDraft();

        mockMvc.perform(get("/api/v1/pr/{id}/readiness", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.draftId").value(id))
            .andExpect(jsonPath("$.data.readyCount").value(4))
            .andExpect(jsonPath("$.data.items[0].key").value("metadata"))
            .andExpect(jsonPath("$.data.items[0].status").value("READY"))
            .andExpect(jsonPath("$.data.items[3].key").value("test-plan"));
    }

    @Test
    void returnsPullRequestDraftTimeline() throws Exception {
        String id = createDraft();

        mockMvc.perform(get("/api/v1/pr/{id}/timeline", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.draftId").value(id))
            .andExpect(jsonPath("$.data.items[0].key").value("context-captured"))
            .andExpect(jsonPath("$.data.items[0].status").value("DONE"))
            .andExpect(jsonPath("$.data.items[2].key").value("readiness-reviewed"))
            .andExpect(jsonPath("$.data.items[2].detail").value("4/4 readiness items ready"))
            .andExpect(jsonPath("$.data.items[3].key").value("submission-prepared"))
            .andExpect(jsonPath("$.data.items[3].status").value("READY"));
    }

    @Test
    void returnsPullRequestDraftContributionReport() throws Exception {
        String id = createDraft();
        String translationId = createTranslation(id);

        mockMvc.perform(get("/api/v1/pr/{id}/report", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.draftId").value(id))
            .andExpect(jsonPath("$.data.upstreamRepo").value("spring-projects/spring-boot"))
            .andExpect(jsonPath("$.data.githubIssueNumber").value(2))
            .andExpect(jsonPath("$.data.readinessReadyCount").value(4))
            .andExpect(jsonPath("$.data.readinessTotalCount").value(4))
            .andExpect(jsonPath("$.data.translationCount").value(1))
            .andExpect(jsonPath("$.data.latestTranslation.translationId").value(translationId))
            .andExpect(jsonPath("$.data.latestTranslation.targetLanguage").value("en"));
    }

    @Test
    void returnsPullRequestDraftSubmissionBundle() throws Exception {
        String id = createDraft();

        mockMvc.perform(get("/api/v1/pr/{id}/submission", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.draftId").value(id))
            .andExpect(jsonPath("$.data.upstreamRepo").value("spring-projects/spring-boot"))
            .andExpect(jsonPath("$.data.githubIssueNumber").value(2))
            .andExpect(jsonPath("$.data.items[0].key").value("title"))
            .andExpect(jsonPath("$.data.items[0].value").value("docs: spring-projects/spring-boot#2 변경 초안"))
            .andExpect(jsonPath("$.data.items[2].value").value("base: main\nhead: feat/validation-docs"))
            .andExpect(jsonPath("$.data.items[3].key").value("test-plan"));
    }

    @Test
    void returnsNotFoundForMissingDraft() throws Exception {
        mockMvc.perform(get("/api/v1/pr/{id}", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingDraft() throws Exception {
        mockMvc.perform(patch("/api/v1/pr/{id}", "missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "docs: update contributor guide",
                      "body": "## Summary\\n- Update contributor guide"
                    }
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenDeletingMissingDraft() throws Exception {
        mockMvc.perform(delete("/api/v1/pr/{id}", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForMissingDraftReadiness() throws Exception {
        mockMvc.perform(get("/api/v1/pr/{id}/readiness", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForMissingDraftTimeline() throws Exception {
        mockMvc.perform(get("/api/v1/pr/{id}/timeline", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForMissingDraftContributionReport() throws Exception {
        mockMvc.perform(get("/api/v1/pr/{id}/report", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForMissingDraftSubmissionBundle() throws Exception {
        mockMvc.perform(get("/api/v1/pr/{id}/submission", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createsPullRequestDraftTranslation() throws Exception {
        String id = createDraft();

        mockMvc.perform(post("/api/v1/pr/{id}/translations", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetLanguage": "en"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.draftId").value(id))
            .andExpect(jsonPath("$.data.targetLanguage").value("en"))
            .andExpect(jsonPath("$.data.title").value("docs: Prepare spring-projects/spring-boot#2 update"))
            .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("## Summary")));
    }

    @Test
    void translatesPullRequestDraftWithCompatibilityAction() throws Exception {
        String id = createDraft();

        mockMvc.perform(post("/api/v1/pr/{id}/translate", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.titleEn").value("docs: Prepare spring-projects/spring-boot#2 update"))
            .andExpect(jsonPath("$.data.bodyEn").value(org.hamcrest.Matchers.containsString("## Summary")))
            .andExpect(jsonPath("$.data.githubUrl").value(org.hamcrest.Matchers.containsString(
                "https://github.com/spring-projects/spring-boot/compare/main...aquila-dev:feat/validation-docs"
            )))
            .andExpect(jsonPath("$.data.githubUrl").value(org.hamcrest.Matchers.containsString("quick_pull=1")));

        mockMvc.perform(get("/api/v1/pr/{id}/translations", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].draftId").value(id))
            .andExpect(jsonPath("$.data[0].targetLanguage").value("en"));
    }

    @Test
    void listsPullRequestDraftTranslations() throws Exception {
        String id = createDraft();
        createTranslation(id);

        mockMvc.perform(get("/api/v1/pr/{id}/translations", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].draftId").value(id))
            .andExpect(jsonPath("$.data[0].targetLanguage").value("en"));
    }

    @Test
    void returnsPullRequestDraftTranslationSubmissionBundle() throws Exception {
        String id = createDraft();
        String translationId = createTranslation(id);

        mockMvc.perform(get("/api/v1/pr/{id}/translations/{translationId}/submission", id, translationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.draftId").value(id))
            .andExpect(jsonPath("$.data.translationId").value(translationId))
            .andExpect(jsonPath("$.data.targetLanguage").value("en"))
            .andExpect(jsonPath("$.data.items[0].key").value("title"))
            .andExpect(jsonPath("$.data.items[0].value").value("docs: Prepare spring-projects/spring-boot#2 update"))
            .andExpect(jsonPath("$.data.items[2].value").value("base: main\nhead: feat/validation-docs"))
            .andExpect(jsonPath("$.data.items[3].key").value("test-plan"));
    }

    @Test
    void returnsPullRequestDraftTranslationReadiness() throws Exception {
        String id = createDraft();
        String translationId = createTranslation(id);

        mockMvc.perform(get("/api/v1/pr/{id}/translations/{translationId}/readiness", id, translationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.draftId").value(id))
            .andExpect(jsonPath("$.data.translationId").value(translationId))
            .andExpect(jsonPath("$.data.targetLanguage").value("en"))
            .andExpect(jsonPath("$.data.readyCount").value(5))
            .andExpect(jsonPath("$.data.items[0].key").value("target-language"))
            .andExpect(jsonPath("$.data.items[0].status").value("READY"))
            .andExpect(jsonPath("$.data.items[4].key").value("test-plan"));
    }

    @Test
    void returnsPullRequestDraftTranslationTimeline() throws Exception {
        String id = createDraft();
        String translationId = createTranslation(id);

        mockMvc.perform(get("/api/v1/pr/{id}/translations/{translationId}/timeline", id, translationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.draftId").value(id))
            .andExpect(jsonPath("$.data.translationId").value(translationId))
            .andExpect(jsonPath("$.data.targetLanguage").value("en"))
            .andExpect(jsonPath("$.data.items[0].key").value("translation-created"))
            .andExpect(jsonPath("$.data.items[0].status").value("DONE"))
            .andExpect(jsonPath("$.data.items[2].key").value("readiness-reviewed"))
            .andExpect(jsonPath("$.data.items[2].detail").value("5/5 translation readiness items ready"))
            .andExpect(jsonPath("$.data.items[3].key").value("submission-prepared"))
            .andExpect(jsonPath("$.data.items[3].status").value("READY"));
    }

    @Test
    void returnsPullRequestDraftTranslationActivities() throws Exception {
        String id = createDraft();
        String translationId = createTranslation(id);

        mockMvc.perform(get("/api/v1/pr/translations/activity")
                .param("limit", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].draftId").value(id))
            .andExpect(jsonPath("$.data[0].translationId").value(translationId))
            .andExpect(jsonPath("$.data[0].upstreamRepo").value("spring-projects/spring-boot"))
            .andExpect(jsonPath("$.data[0].githubIssueNumber").value(2))
            .andExpect(jsonPath("$.data[0].targetLanguage").value("en"))
            .andExpect(jsonPath("$.data[0].title").value("docs: Prepare spring-projects/spring-boot#2 update"))
            .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty());
    }

    @Test
    void returnsNotFoundForTranslationSubmissionWhenTranslationBelongsToAnotherDraft() throws Exception {
        String sourceDraftId = createDraft();
        String translationId = createTranslation(sourceDraftId);
        String otherDraftId = createDraft();

        mockMvc.perform(get("/api/v1/pr/{id}/translations/{translationId}/submission", otherDraftId, translationId))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForTranslationReadinessWhenTranslationBelongsToAnotherDraft() throws Exception {
        String sourceDraftId = createDraft();
        String translationId = createTranslation(sourceDraftId);
        String otherDraftId = createDraft();

        mockMvc.perform(get("/api/v1/pr/{id}/translations/{translationId}/readiness", otherDraftId, translationId))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForTranslationTimelineWhenTranslationBelongsToAnotherDraft() throws Exception {
        String sourceDraftId = createDraft();
        String translationId = createTranslation(sourceDraftId);
        String otherDraftId = createDraft();

        mockMvc.perform(get("/api/v1/pr/{id}/translations/{translationId}/timeline", otherDraftId, translationId))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenCreatingTranslationForMissingDraft() throws Exception {
        mockMvc.perform(post("/api/v1/pr/{id}/translations", "missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetLanguage": "en"
                    }
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenTranslatingMissingDraftWithCompatibilityAction() throws Exception {
        mockMvc.perform(post("/api/v1/pr/{id}/translate", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsupportedTranslationLanguage() throws Exception {
        String id = createDraft();

        mockMvc.perform(post("/api/v1/pr/{id}/translations", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetLanguage": "ko"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("targetLanguage")));
    }

    @Test
    void rejectsBlankRepository() throws Exception {
        mockMvc.perform(post("/api/v1/pr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "upstreamRepo": "",
                      "githubIssueNumber": 2,
                      "baseBranch": "main",
                      "headBranch": "feat/validation-docs",
                      "diffContent": "diff --git a/docs.adoc b/docs.adoc"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("upstreamRepo")));
    }

    @Test
    void rejectsBlankDraftUpdateTitle() throws Exception {
        String id = createDraft();

        mockMvc.perform(patch("/api/v1/pr/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "",
                      "body": "## Summary\\n- Update contributor guide"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("title")));
    }

    private String createDraft() throws Exception {
        return createDraft(2, "feat/validation-docs");
    }

    private String createDraft(long githubIssueNumber, String headBranch) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/pr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "upstreamRepo": "spring-projects/spring-boot",
                      "githubIssueNumber": %d,
                      "baseBranch": "main",
                      "headBranch": "%s",
                      "diffContent": "diff --git a/docs.adoc b/docs.adoc\\n+Add validation example"
                    }
                    """, githubIssueNumber, headBranch)))
            .andExpect(status().isOk())
            .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createTranslation(String id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/pr/{id}/translations", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetLanguage": "en"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    @TestConfiguration
    static class PullRequestDraftDiffTestConfiguration {

        @Bean
        @Primary
        LoadPullRequestDraftDiffPort loadPullRequestDraftDiffPort() {
            return request -> "diff --git a/docs.adoc b/docs.adoc\n+Loaded from GitHub compare";
        }
    }
}
