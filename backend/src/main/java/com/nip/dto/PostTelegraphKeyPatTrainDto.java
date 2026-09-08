package com.nip.dto;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 8:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(title = "岗位训练-电子键拍发Dto")
@RegisterForReflection
public class PostTelegraphKeyPatTrainDto {

  @Schema(title  = "id")
  private String id;

  /**
   * 标题
   */
  @Schema(title  = "标题")
  private String title;

  /**
   * 是否是固定报 0 固定报 1 随机报
   */
  @Schema(title = "是否是固定报 0 随机报 1 固定报")
  private Integer isCable = 0;

  /**
   * 是否是固定报 0 固定报 1 随机报
   */
  @Schema(title = "如果是固定报，该值为固定报编号，否则为空")
  private String cableId;

  @Schema(title = "如果是固定报，可以选择起始页，默认第一页")
  private Integer startPage;

  /**
   *  0 未开始 1 进行中 2 暂停 3 完成
   */
  @Schema(title  = "0 未开始 1 进行中 2 完成")
  private Integer status;

  /**
   * 时长
   */
  @Schema(title  = "时长")
  private String duration;

  /**
   * // 全报文数量
   */
  @Schema(title  = "全报文数量")
  private Integer totalNumber;

  @Schema(title = "训练报文 0数码 1字码 2混合码 ")
  private Integer messageType;


  /**
   * // 错误数量
   */
  @Schema(title  = "错误数量")
  private Integer errorNumber;

  /**
   *正确率
   */
  @Schema(title  = "正确率")
  private Integer accuracy;

  /**
   *速率
   */
  @Schema(title  = "速率")
  private String speed;


  /**
   * 报文内容
   */
  @Schema(title  = "报文内容")
  private String content;


  @Schema(title  = "评分规则Id")
  private String ruleId;
}
