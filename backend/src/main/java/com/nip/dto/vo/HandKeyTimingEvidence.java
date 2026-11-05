package com.nip.dto.vo;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record HandKeyTimingEvidence(int capturedGroup, int character, int signal,
    String item, int actualMillis, int minimumMillis, int maximumMillis, int deduction) {}
