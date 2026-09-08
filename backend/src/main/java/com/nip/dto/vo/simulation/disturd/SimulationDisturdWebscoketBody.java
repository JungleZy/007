package com.nip.dto.vo.simulation.disturd;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author lht
 * @Date: 2023/3/4 10:23
 * @Description
 */
@Data
//@ApiModel(value = "仿真训练 快报/干扰报")
@RegisterForReflection
public class SimulationDisturdWebscoketBody {
    private String userName;
    private String userImg;
    private String id;
    private String road;
    private String setting;
    private Integer channel;
}
