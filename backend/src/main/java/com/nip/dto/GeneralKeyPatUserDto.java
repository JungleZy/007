package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(title = "获取GeneralKeyPatUser信息Dto")
@RegisterForReflection
public class GeneralKeyPatUserDto {
  private Integer id;

  /**
   * 正确率
   */
  private String accuracy;

  /**
   * 扣分详情
   */
  private String deductInfo;

  /**
   * 速率
   */
  private String speed;

  /**
   * 错误个数
   */
  private Integer errorNumber;

  /**
   * 统计信息
   */
  private String statisticInfo;

  /**
   * 得分
   */
  private BigDecimal score;

  /**
   * 0 未完成 1 已完成
   */
  private Integer isFinish;

  /**
   * 训练创建时间
   */
  private LocalDateTime createTime;

  /**
   * 训练结束时间
   */
  private LocalDateTime finishTime;

  /**
   * 时长
   */
  private String duration;

  /**
   * 训练ID
   */
  private Integer trainId;

  /**
   * 用户ID
   */
  private String userId;

  /**
   * 角色 0 参训人 1 组训人
   */
  private Integer role;

  private String content;
  private String userName;
  private String userImg;
}
