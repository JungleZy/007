package com.nip.dto.vo.simulation.disturd;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author lht
 * @Date: 2023/3/4 9:19
 * @Description
 */

@Data
//@ApiModel(value = "仿真训练 快报/干扰报")
@RegisterForReflection
public class SimulationDisturdTrainVO {

    //@ApiModelProperty(value = "id")
    private String id;

    /**
     * 创建人
     */
    //@ApiModelProperty(value = "名称")
    private String userName;

    /**
     * 创建人头像
     */
    //@ApiModelProperty(value = "头像")
    private String userImg;

    /**
     * 创建人头像
     */
    //@ApiModelProperty(value = "路线")
    private Integer channel;

    //@ApiModelProperty(value = "训练结果")
    private String contentValue;

    //@ApiModelProperty(value = "用户状态 0 未完成 1完成")
    private Integer userStatus;

    //@ApiModelProperty(value = "已提交页码")
    private long existPageNumber;
}
