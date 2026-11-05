package com.nip.dto.vo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

/** 单页内的一次报文比较，金额为各项封顶前的规则扣分。 */
@RegisterForReflection
public record HandKeyDeductionEvidence(int referenceGroup, int capturedGroup, int capturedThrough,
    String reference, String captured, Map<String, Integer> deductions, Integer partnerReferenceGroup) {}
