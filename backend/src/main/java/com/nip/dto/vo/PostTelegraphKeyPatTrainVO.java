package com.nip.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 8:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(title = "岗位训练-电子键拍发VO")
public class PostTelegraphKeyPatTrainVO {

  @Schema(title = "id")
  private String id;

  /**
   * 标题
   */
  @Schema(title = "标题")
  private String title;

  /**
   * 是否是固定报 0 固定报 1 随机报
   */
  @Schema(title = "是否是固定报 0 随机报 1 固定报")
  private Integer isCable = 0;

  /**
   * 训练类型 0个人训练 1考核训练
   */
  @Schema(title = "训练类型 0个人训练 1考核训练")
  private Integer trainType;

  /**
   *  0 未开始 1 进行中 2 暂停 3 完成
   */
  @Schema(title = "0 未开始 1 进行中 2 完成")
  private Integer status;

  /**
   * 时长
   */
  @Schema(title = "时长(秒)")
  private String duration;

  /**
   * // 全报文数量
   */
  @Schema(title = "全报文数量")
  private Integer totalNumber;

  @Schema(title = "页数")
  private Integer pageNumber;


  @Schema(title = "训练报文 0数码 1字码 2混合码 ")
  private Integer messageType;

  /**
   * // 错误数量
   */
  @Schema(title = "错误数量")
  private Integer errorNumber;

  /**
   *正确率
   */
  @Schema(title = "正确率")
  private double accuracy;

  /**
   *速率
   */
  @Schema(title = "速率")
  private String speed;


  /**
   * 报文内容
   */
  @Schema(title = "报文内容")
  private List<PostTelegraphKeyPatTrainPageMessageVO> content;

  @Schema(title = "解析后拍发内容")
  private List<String> resolverMessage;


  @Schema(title = "分数")
  private BigDecimal score;


  @Schema(title = "评分规则")
  private String ruleContent;

  @Schema(title = "扣分详情")
  private String deductInfo;

  @Schema(title = "已存在的页码数")
  private List<Integer> existPage;

  @Schema(title = "多组")
  private List<PostTelegraphTrainResolverVO> moreGroupLine;


  @Schema(title = "每页统计信息")
  private List<PostTelegraphKeyPatTrainPageAnalyzeVO> pageAnalyzeVOS;
}
