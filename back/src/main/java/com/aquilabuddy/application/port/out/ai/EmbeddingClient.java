package com.aquilabuddy.application.port.out.ai;

import com.aquilabuddy.domain.embedding.EmbeddingVector;

/**
 * 텍스트를 추천용 embedding vector로 변환하는 outbound client 계약입니다.
 */
public interface EmbeddingClient {

    /**
     * 입력 텍스트를 provider embedding vector로 변환합니다.
     */
    EmbeddingVector embed(String input);
}
