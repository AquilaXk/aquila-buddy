package com.aquilabuddy.global.ai;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.domain.ai.AiCallObservation;
import java.util.ArrayList;
import java.util.List;

class RecordingAiCallObserver implements AiCallObserver {

    private final List<AiCallObservation> observations = new ArrayList<>();

    @Override
    public void record(AiCallObservation observation) {
        observations.add(observation);
    }

    List<AiCallObservation> observations() {
        return observations;
    }

    AiCallObservation firstObservation() {
        return observations.getFirst();
    }
}
