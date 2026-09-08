package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class GeneralKeyPatPageSyncDto {

    private Integer id;

    /**
     * 生成的key
     */
    private String key;

    /**
     * 页码
     */
    private Integer pageNumber;

    /**
     * 排序字段
     */
    private Integer sort;

    private String time;

    /**
     * 训练ID
     */
    private Integer trainId;

    private String value;
}
