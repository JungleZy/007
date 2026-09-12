package com.nip.dto.general;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * 结束训练。
 *
 * <p>结算对象一律取自 token，不再接受请求体里的 {@code userId}：那等于让任何参训人指定结算谁。
 */
@Data
@RegisterForReflection
public class GeneralTelexPatFinishDto {

    /**
     * 训练ID
     */
    String trainId;

    @Schema(title = "训练轮次")
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer attempt;

}
