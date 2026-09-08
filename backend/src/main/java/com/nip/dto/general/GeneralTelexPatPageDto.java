package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;

@Data
@RegisterForReflection
public class GeneralTelexPatPageDto {

    /**
     * 报底
     */
    private List<GeneralTelexPatPageDetailDto> messageContent;
}
