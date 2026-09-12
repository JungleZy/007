package com.nip.dto.general;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import com.nip.dto.CaptureInterval;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * 提交拍发报底。
 *
 * <p>速率与逐页用时一律由服务端从 {@link #captureIntervals} 重算，请求体不再携带 {@code speed}/{@code validTime}。
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
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer pageNumber;

    /**
     * 拍发内容
     */
    @Schema(title = "拍发内容")
    private String patValue;

    @Schema(title = "采集协议版本，必须与训练一致")
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer protocolVersion;

    @Schema(title = "训练轮次")
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer attempt;

    @Schema(title = "相对本成员采集起点的有效采集区间（毫秒）")
    private List<CaptureInterval> captureIntervals;
}
