package com.nip.dto.vo.simulation.router;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: lht
 * @Data: 2023-03-03 8:38
 * @Description: 查看详情
 */

@Data
//@ApiModel(value = "仿真训练 快报/干扰报")
@RegisterForReflection
public class SimulationRouterRoomContentOneVO {



    /**
     * 报文内容
     */
    //@ApiModelProperty(value = "报文内容")
    private String content;

    /**
     * 主信号
     */
    //@ApiModelProperty(value = "主信号")
    private String mainSignal;

    /**
     * 干扰信号
     */
    //@ApiModelProperty(value = "干扰信号")
    private String interferenceSignal;


}
