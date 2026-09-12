package com.nip.dto.general;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import com.nip.dto.CaptureInterval;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;

/**
 * 提交拍发报底
 */
@Data
@RegisterForReflection
public class GeneralKeyPatPageSubmitDto {
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer attempt;
    private List<CaptureInterval> captureIntervals;

    /**
     * 训练ID
     */
    Integer trainId;

    /**
     * 页码
     */
    private Integer pageNumber;


    /**
     * 用户拍发的内容
     */
    private List<GeneralKeyPatPageDetailDto> pageValue;
}
