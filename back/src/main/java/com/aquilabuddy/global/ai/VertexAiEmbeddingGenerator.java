package com.aquilabuddy.global.ai;

import java.util.List;

/**
 * Vertex AI embedding 호출부를 테스트 가능한 경계로 분리합니다.
 */
public interface VertexAiEmbeddingGenerator {

    List<Double> embed(String input);
}
