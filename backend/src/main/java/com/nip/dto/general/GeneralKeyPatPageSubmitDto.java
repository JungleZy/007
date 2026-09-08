package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;

/**
 * 提交拍发报底
 */
@Data
@RegisterForReflection
public class GeneralKeyPatPageSubmitDto {

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
