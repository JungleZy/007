package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @description: 批量保存军语密语
 * @author: zc
 * @create: 2023-08-02 16:30
 */
@Data
@Schema(name = "批量保存军语密语MilitaryTermDto")
@RegisterForReflection
public class MilitaryTermDto {

    @Schema(name = "parentName",title = "第一级目录")
    private String parentName;

    @Schema(name = "childName",title = "第二级目录")
    private String childName;

    @Schema(name = "content",title = "内容")
    private String content;

}
