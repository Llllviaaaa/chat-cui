package com.chatcui.gateway.persistence;

import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DeliveryStatusReporter implements SkillPersistenceForwarder.DeliveryStatusSink {
    private final Map<String, String> statuses = new ConcurrentHashMap<>();

    @Override
    public void pending(SkillTurnForwardEvent event) {
        statuses.put(tupleKey(event), "pending");
    }

    @Override
    public void saved(SkillTurnForwardEvent event) {
        statuses.put(tupleKey(event), "saved");
    }

    @Override
    public void failed(SkillTurnForwardEvent event, Exception error) {
        statuses.put(tupleKey(event), "failed");
    }

    public Optional<String> currentStatus(SkillTurnForwardEvent event) {
        return Optional.ofNullable(statuses.get(tupleKey(event)));
    }

    private String tupleKey(SkillTurnForwardEvent event) {
        return event.sessionId() + "|" + event.turnId() + "|" + event.seq() + "|" + event.topic();
    }
}
