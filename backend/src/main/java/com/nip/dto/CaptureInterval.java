package com.nip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictLongDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CaptureInterval(
    @JsonProperty(value = "startedMs", required = true)
    @JsonDeserialize(using = StrictLongDeserializer.class) long startedMs,
    @JsonProperty(value = "endedMs", required = true)
    @JsonDeserialize(using = StrictLongDeserializer.class) long endedMs) {}
