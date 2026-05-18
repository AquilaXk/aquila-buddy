package com.aquilabuddy.adapter.out.ai;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.domain.ai.AiCallObservation;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * AI 호출 관측 이벤트를 로컬 저장소와 외부 관측성 adapter에 함께 전달합니다.
 */
@Primary
@Component
public class CompositeAiCallObserver implements AiCallObserver {

    private final List<ObjectProvider<AiCallObserver>> observerProviders;

    public CompositeAiCallObserver(
        @Qualifier("persistentAiCallObserver") ObjectProvider<AiCallObserver> persistentObserverProvider,
        @Qualifier("langfuseAiTraceAdapter") ObjectProvider<AiCallObserver> langfuseObserverProvider
    ) {
        this.observerProviders = List.of(persistentObserverProvider, langfuseObserverProvider);
    }

    @Override
    public void record(AiCallObservation observation) {
        AiCallObservation normalized = Objects.requireNonNull(observation, "observation must not be null.");
        observerProviders.forEach(provider -> provider.ifAvailable(observer -> recordSafely(observer, normalized)));
    }

    private static void recordSafely(AiCallObserver observer, AiCallObservation observation) {
        try {
            observer.record(observation);
        } catch (RuntimeException exception) {
            // 한 observer 실패가 다른 관측 경로나 실제 AI 호출을 막지 않도록 한다.
        }
    }
}
