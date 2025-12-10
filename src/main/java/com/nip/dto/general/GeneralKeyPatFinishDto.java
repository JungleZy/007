package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;


/**
 * 结束训练
 */
@Data
@RegisterForReflection
public class GeneralKeyPatFinishDto {

    /**
     * 训练ID
     */
    Integer trainId;

    private String userId;

}
