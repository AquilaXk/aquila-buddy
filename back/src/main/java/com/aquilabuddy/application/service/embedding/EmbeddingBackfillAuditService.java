package com.aquilabuddy.application.service.embedding;

import com.aquilabuddy.application.port.in.embedding.RecordEmbeddingBackfillAuditUseCase;
import com.aquilabuddy.application.port.out.embedding.RecordEmbeddingBackfillAuditPort;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillAuditEvent;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingBackfillAuditService implements RecordEmbeddingBackfillAuditUseCase {

    private final RecordEmbeddingBackfillAuditPort recordEmbeddingBackfillAuditPort;

    public EmbeddingBackfillAuditService(RecordEmbeddingBackfillAuditPort recordEmbeddingBackfillAuditPort) {
        this.recordEmbeddingBackfillAuditPort = recordEmbeddingBackfillAuditPort;
    }

    @Override
    public void record(EmbeddingBackfillAuditEvent event) {
        recordEmbeddingBackfillAuditPort.record(event);
    }
}
