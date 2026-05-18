package com.aquilabuddy.application.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.embedding.BackfillRecommendedIssueEmbeddingPort;
import com.aquilabuddy.application.port.out.embedding.BackfillUserProfileEmbeddingPort;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillResult;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillSummary;
import org.junit.jupiter.api.Test;

class EmbeddingBackfillServiceTest {

    @Test
    void backfillsProfilesAndIssuesWithSameLimit() {
        RecordingBackfillPort port = new RecordingBackfillPort();
        EmbeddingBackfillService service = new EmbeddingBackfillService(port, port);

        EmbeddingBackfillSummary summary = service.backfillEmbeddings(25);

        assertThat(port.profileLimit).isEqualTo(25);
        assertThat(port.issueLimit).isEqualTo(25);
        assertThat(summary.profiles().target()).isEqualTo("profiles");
        assertThat(summary.issues().target()).isEqualTo("issues");
        assertThat(summary.profiles().updated()).isEqualTo(1);
        assertThat(summary.issues().failed()).isEqualTo(1);
    }

    private static class RecordingBackfillPort implements
        BackfillUserProfileEmbeddingPort,
        BackfillRecommendedIssueEmbeddingPort {

        private int profileLimit;
        private int issueLimit;

        @Override
        public EmbeddingBackfillResult backfillMissingProfileEmbeddings(int limit) {
            this.profileLimit = limit;
            return new EmbeddingBackfillResult("profiles", limit, 1, 1, 0, 0, 0, 0, 0);
        }

        @Override
        public EmbeddingBackfillResult backfillMissingIssueEmbeddings(int limit) {
            this.issueLimit = limit;
            return new EmbeddingBackfillResult("issues", limit, 1, 0, 0, 0, 0, 0, 1);
        }
    }
}
