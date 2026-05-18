package com.aquilabuddy.application.port.out.embedding;

import com.aquilabuddy.domain.embedding.EmbeddingBackfillAuditEvent;

public interface RecordEmbeddingBackfillAuditPort {

    void record(EmbeddingBackfillAuditEvent event);
}
