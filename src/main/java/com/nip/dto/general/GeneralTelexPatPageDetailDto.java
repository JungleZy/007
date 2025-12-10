package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class GeneralTelexPatPageDetailDto {

    /**
     * 报底ID
     */
    private String id;

    /**
     * 页码
     */
    private Integer pageNumber;

    /**
     * 排序字段
     */
    private Integer sort;

    /**
     * 生成的key
     */
    private String key;

    /**
     * 用户拍发的
     */
    private String value;

}
