package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;


/**
 * 结束训练
 */
@Data
@RegisterForReflection
public class GeneralTelexPatFinishDto {

    /**
     * 训练ID
     */
    String trainId;

    private String userId;

}
