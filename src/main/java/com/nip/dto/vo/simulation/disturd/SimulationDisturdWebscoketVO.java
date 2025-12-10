package com.nip.dto.vo.simulation.disturd;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author lht
 * @Date: 2023/3/4 10:22
 * @Description
 */
@Data
//@ApiModel(value = "仿真训练 快报/干扰报")
@RegisterForReflection
public class SimulationDisturdWebscoketVO {
    private String topic;
    private SimulationDisturdWebscoketBody body;

}
