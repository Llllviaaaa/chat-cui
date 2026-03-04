package com.chatcui.skill.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class LocalImMessageGateway implements ImMessageGateway {

    private final Clock clock;

    public LocalImMessageGateway() {
        this(Clock.systemUTC());
    }

    LocalImMessageGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ImSendResult send(ImSendCommand command) {
        String message = command.messageText() == null ? "" : command.messageText();
        if (message.contains("[im-fail]")) {
            throw new ImSendException(
                    "IM_CHANNEL_UNAVAILABLE",
                    "IM channel is unavailable. Please retry."
            );
        }
        return new ImSendResult("im-" + UUID.randomUUID(), Instant.now(clock));
    }
}

