package com.nip.dto.vo.simulation.report;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
//@ApiModel(value = "人员信息")
@RegisterForReflection
public class SimulationReportRoomUserVO {

    private String id;
    /**
     * 用户头像
     */
    private String userImg;
    /**
     * 用户姓名
     */
    private String userName;


    //@ApiModelProperty(value = "0 离线 1在线")
    private Integer status = 0;

    //@ApiModelProperty(value = "训练结果")
    private String contentValue;

    //@ApiModelProperty(value = "是否以填报 0 未完成 1完成")
    private Integer userStatus;

    //@ApiModelProperty(value = "提交页码数")
    private long existPageNumber;
}
