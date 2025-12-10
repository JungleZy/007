package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * 分页模型
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2018-09-27 15:31
 */
@Data
@RegisterForReflection
public class Page {
    /**
     * 当前页
     */
    private int page =0;
    /**
     * 当前页条数
     */
    private int rows = 20;
    /**
     * 是否倒序，默认true
     */
    private Boolean desc = true;
    /**
     * 排序字段，默认id
     */
    private String sortBy = "id";
}
