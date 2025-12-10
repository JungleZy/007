package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.Date;

/**
 * @description:
 * @author: zc
 * @create: 2023-08-01 08:28
 */
@Data
@RegisterForReflection
public class AllExamDto {
    private Integer userState;
    private String teacherName;


    private String id;
    /**
     * 考核名称
     */
    private String title;
    private String start_time;
    private String end_time;
    /**
     * 时长
     */
    private String duration;
    /**
     * 创建人
     */
    private String create_user_id;
    /**
     * 创建时间
     */
    private String create_time = new Date().getTime() + "";
    /**
     * 监考人
     */
    private String teacher; // 当前正在编辑的Id
    /**
     * 状态（1:未开始，2：已开始，3：已结束，4：已阅卷）
     */
    private Integer state;
}
