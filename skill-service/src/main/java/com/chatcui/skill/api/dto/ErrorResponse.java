package com.chatcui.skill.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
        @JsonProperty("error") ErrorBody error
) {
    public record ErrorBody(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message
    ) {
    }
}
