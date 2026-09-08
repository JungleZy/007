package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class GeneralTelexPatPageParamDto {

    /**
     * 训练ID
     */
    String trainId;

    /**
     * 页码
     */
    Integer pageNumber;


    private String userId;
}
