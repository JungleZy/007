package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class GeneralKeyPatPageParamDto {

    /**
     * 训练ID
     */
    Integer trainId;

    /**
     * 页码
     */
    Integer pageNumber;


    private String userId;
}
