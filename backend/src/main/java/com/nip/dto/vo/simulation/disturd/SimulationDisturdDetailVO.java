package com.nip.dto.vo.simulation.disturd;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author lht
 * @Date: 2023/3/3 11:38
 * @Description
 */
@Data
//@ApiModel(value = "仿真训练 快报/干扰报")
@RegisterForReflection
public class SimulationDisturdDetailVO {
    //@ApiModelProperty(value = "创建人id")
    private String id;

    private Integer isCable;
    /**
     * 房间名称
     */
    //@ApiModelProperty(value = "房间名称")
    private String name;

    /**
     * 创建人
     */
    //@ApiModelProperty(value = "创建人")
    private String userName;

//    /**
//     * 创建人
//     */
//    //@ApiModelProperty(value = "创建人Id")
//    private String userId;

    /**
     * 创建人头像
     */
    //@ApiModelProperty(value = "创建头像")
    private String userImg;

    /**
     * 房间状态 0 开启 1关闭
     */
    //@ApiModelProperty(value = "房间状态 0 开启 1关闭")
    private Integer stats;

    private LocalDateTime createTime;

    /**
     *报底类型 1=平均保底 2=乱码报底
     */
    //@ApiModelProperty(value = "报底类型 1=平均保底 2=乱码报底")
    private Integer bdType;

    /**
     * 报文类型 1=数字短码 2=数字长码 3=字码 4=混合报
     */
    //@ApiModelProperty(value = " 报文类型 1=数字短码 2=数字长码 3=字码 4=混合报")
    private Integer bwType;

    /**
     * 报文组数
     */
    //@ApiModelProperty(value = "报文组数")
    private Integer bwCount;
    /**
     * 报文页数
     */
    private Integer pageCount;

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

    //@ApiModelProperty(value = "报文结果")
    private String contentValue;

    //@ApiModelProperty(value = "训练耗时")
    private Integer totalTime;

    //@ApiModelProperty(value = "房间配置")
    private String setting;

    //@ApiModelProperty(value = "已填报页码数")
    private long existPageNumber;

}
