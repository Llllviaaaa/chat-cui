package com.chatcui.gateway.runtime;

import com.chatcui.gateway.persistence.DeliveryStatusReporter;
import com.chatcui.gateway.persistence.SkillPersistenceForwarder;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import java.util.Set;

public class BridgePersistencePublisher {
    private static final Set<String> PERSISTENCE_TOPICS = Set.of(
            "skill.turn.delta",
            "skill.turn.final",
            "skill.turn.completed",
            "skill.turn.error");

    private final SkillPersistenceForwarder forwarder;
    private final DeliveryStatusReporter statusReporter;

    public BridgePersistencePublisher(SkillPersistenceForwarder forwarder, DeliveryStatusReporter statusReporter) {
        this.forwarder = forwarder;
        this.statusReporter = statusReporter;
    }

    public void publish(String topic, SkillTurnForwardEvent event) {
        if (!PERSISTENCE_TOPICS.contains(topic)) {
            return;
        }
        statusReporter.pending(event);
        forwarder.forward(event);
    }
}
