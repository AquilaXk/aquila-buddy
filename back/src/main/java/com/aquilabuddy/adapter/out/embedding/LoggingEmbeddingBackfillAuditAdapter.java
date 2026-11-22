package com.aquilabuddy.adapter.out.embedding;

import com.aquilabuddy.application.port.out.embedding.RecordEmbeddingBackfillAuditPort;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillAuditEvent;
import org.springframework.stereotype.Component;

@Component
public class LoggingEmbeddingBackfillAuditAdapter implements RecordEmbeddingBackfillAuditPort {

    private static final System.Logger log = System.getLogger(LoggingEmbeddingBackfillAuditAdapter.class.getName());

    @Override
    public void record(EmbeddingBackfillAuditEvent event) {
        log.log(
            System.Logger.Level.INFO,
            "embedding_backfill_audit outcome={} operator={} requestedLimit={} effectiveLimit={} profileUpdated={} "
                + "issueUpdated={} failed={} reason={}",
            event.outcome(),
            event.operator(),
            event.requestedLimit(),
            event.effectiveLimit(),
            event.profileUpdated(),
            event.issueUpdated(),
            event.failed(),
            event.reason()
        );
    }
}
