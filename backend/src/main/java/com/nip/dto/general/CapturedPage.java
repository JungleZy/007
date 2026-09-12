package com.nip.dto.general;

import com.nip.dto.CaptureInterval;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record CapturedPage(int attempt, List<CaptureInterval> intervals, long receivedAt) {}
