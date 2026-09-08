package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class GeneralKeyPatPageDetailDto {

    /**
     * 报底ID
     */
    private Integer id;

    /**
     * 页码
     */
    private Integer pageNumber;

    /**
     * 排序字段
     */
    private Integer sort;

    /**
     * 拍发的时间
     */
    private String time;

    /**
     * 生成的key
     */
    private String key;

    /**
     * 用户拍发的
     */
    private String value;

}
