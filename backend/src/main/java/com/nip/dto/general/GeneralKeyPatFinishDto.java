package com.nip.dto.general;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
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

    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer attempt;


}
