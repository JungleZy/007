package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 提交拍发报底
 */
@Data
@RegisterForReflection
public class GeneralTelexPatPageSubmitDto {

    /**
     * 训练ID
     */
    String trainId;

    /**
     * 页码
     */
    private Integer pageNumber;

    /**
     * 拍发内容
     */
    @Schema(title = "拍发内容")
    private String patValue;

    @Schema(title = "有效时长")
    private Integer validTime;

    @Schema(title = "速率")
    private String speed;
}
