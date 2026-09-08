package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@RegisterForReflection
public class GeneralKeyPatSyncDto {

    private Integer id;

    /**
     * 房间名称
     */
    private String title;

    /**
     * 报底数量
     */
    private Integer totalNumber;

    /**
     * 评分规则ID
     */
    private String ruleId;

    /**
     * 评分规则内容
     */
    private String ruleContent;

    /**
     * 创建人UID
     */
    private String createUser;

    /**
     * 创建训练时间
     */
    private LocalDateTime createTime;

    /**
     * 开始训练时间
     */
    private LocalDateTime startTime;

    /**
     * 结束训练时间
     */
    private LocalDateTime endTime;

    /**
     * 训练状态
     *  0未开始，1，进行中，2已完成
     */
    private Integer status;

    /**
     * 有效时长
     */
    private Long validTime;

    private Integer messageType;

    private Integer isAverage;

    private Integer isRandom;
}
