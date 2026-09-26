package com.aquilabuddy.adapter.out.github.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.prdraft.PullRequestTemplateSection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PR 템플릿 마크다운 섹션(Description, Test Plan, Checklist 등) 시맨틱 구조화 파서를 검증합니다.
 */
class GitHubPullRequestDraftRepositoryGuideContextAdapterTest {

    @Test
    void parsesStructuredSectionsFromMarkdownTemplate() {
        String template = """
            <!-- Header comment -->
            ## Description
            <!-- Please include a summary of the change. -->
            Fixes #123

            ## Test Plan
            <!-- How should reviewers test this change? -->
            - [ ] Unit tests added
            - [x] Integration tests passed

            ## Checklist
            - [ ] My code follows the style guidelines
            - [x] I have performed a self-review

            ## Breaking Changes
            - 하위 호환성을 유지합니다.
            """;

        List<PullRequestTemplateSection> sections =
            GitHubPullRequestDraftRepositoryGuideContextAdapter.parseTemplateSections(template);

        assertThat(sections).hasSize(4);

        PullRequestTemplateSection descriptionSection = sections.get(0);
        assertThat(descriptionSection.heading()).isEqualTo("Description");
        assertThat(descriptionSection.rawHeading()).isEqualTo("## Description");
        assertThat(descriptionSection.level()).isEqualTo(2);
        assertThat(descriptionSection.type()).isEqualTo(PullRequestTemplateSection.SectionType.DESCRIPTION);
        assertThat(descriptionSection.content()).contains("Fixes #123");

        PullRequestTemplateSection testPlanSection = sections.get(1);
        assertThat(testPlanSection.heading()).isEqualTo("Test Plan");
        assertThat(testPlanSection.type()).isEqualTo(PullRequestTemplateSection.SectionType.TEST_PLAN);
        assertThat(testPlanSection.hasCheckboxes()).isTrue();
        assertThat(testPlanSection.checkboxes()).containsExactly(
            "- [ ] Unit tests added",
            "- [x] Integration tests passed"
        );

        PullRequestTemplateSection checklistSection = sections.get(2);
        assertThat(checklistSection.heading()).isEqualTo("Checklist");
        assertThat(checklistSection.type()).isEqualTo(PullRequestTemplateSection.SectionType.CHECKLIST);
        assertThat(checklistSection.checkboxes()).hasSize(2);

        PullRequestTemplateSection breakingChangeSection = sections.get(3);
        assertThat(breakingChangeSection.heading()).isEqualTo("Breaking Changes");
        assertThat(breakingChangeSection.type()).isEqualTo(PullRequestTemplateSection.SectionType.BREAKING_CHANGES);
        assertThat(breakingChangeSection.content()).contains("하위 호환성을 유지합니다.");
    }

    @Test
    void findsSectionBySemanticType() {
        String template = """
            ## Summary
            Changes summary

            ## How to Test
            Run test suite
            """;

        List<PullRequestTemplateSection> sections =
            GitHubPullRequestDraftRepositoryGuideContextAdapter.parseTemplateSections(template);

        assertThat(GitHubPullRequestDraftRepositoryGuideContextAdapter.findSection(
            sections, PullRequestTemplateSection.SectionType.TEST_PLAN
        )).isPresent();

        assertThat(GitHubPullRequestDraftRepositoryGuideContextAdapter.findSection(
            sections, PullRequestTemplateSection.SectionType.BREAKING_CHANGES
        )).isEmpty();
    }

    @Test
    void handlesNullAndBlankTemplateGracefully() {
        assertThat(GitHubPullRequestDraftRepositoryGuideContextAdapter.parseTemplateSections(null)).isEmpty();
        assertThat(GitHubPullRequestDraftRepositoryGuideContextAdapter.parseTemplateSections("   ")).isEmpty();
    }

    @Test
    void preservesCodeBlockCommentsWithoutBreakingSections() {
        String template = """
            ## How to test
            ```bash
            # Setup environment
            export APP_ENV=test
            # Execute test suite
            ./gradlew test
            ```

            ## Checklist
            - [x] Tested locally
            """;

        List<PullRequestTemplateSection> sections =
            GitHubPullRequestDraftRepositoryGuideContextAdapter.parseTemplateSections(template);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isEqualTo("How to test");
        assertThat(sections.get(0).type()).isEqualTo(PullRequestTemplateSection.SectionType.TEST_PLAN);
        assertThat(sections.get(0).content())
            .contains("# Setup environment")
            .contains("# Execute test suite");
        assertThat(sections.get(1).heading()).isEqualTo("Checklist");
    }

    @Test
    void ignoresHeadingsInsideMultiLineHtmlComments() {
        String template = """
            <!--
            ## Deprecated Section
            - Do not use
            -->
            ## Motivation
            Explain reason
            """;

        List<PullRequestTemplateSection> sections =
            GitHubPullRequestDraftRepositoryGuideContextAdapter.parseTemplateSections(template);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).heading()).isEqualTo("Motivation");
        assertThat(sections.get(0).type()).isEqualTo(PullRequestTemplateSection.SectionType.DESCRIPTION);
    }

    @Test
    void classifiesHotfixAndBugfixHeadingsAsDescription() {
        String template = """
            ## Hotfix details
            Urgent fix info

            ## Bugfix summary
            Resolved memory leak
            """;

        List<PullRequestTemplateSection> sections =
            GitHubPullRequestDraftRepositoryGuideContextAdapter.parseTemplateSections(template);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).type()).isEqualTo(PullRequestTemplateSection.SectionType.DESCRIPTION);
        assertThat(sections.get(1).type()).isEqualTo(PullRequestTemplateSection.SectionType.DESCRIPTION);
    }
}
