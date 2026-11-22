package com.aquilabuddy.application.port.in.embedding;

import com.aquilabuddy.domain.embedding.EmbeddingBackfillAuditEvent;

public interface RecordEmbeddingBackfillAuditUseCase {

    void record(EmbeddingBackfillAuditEvent event);
}
