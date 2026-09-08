package com.nip.dto.general;

import com.nip.dto.vo.PostTelegraphKeyPatTrainPageAnalyzeVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageMessageVO;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@RegisterForReflection
public class GeneralKeyPatUserInfoVO {

  private Integer isCable;
  private String userId;
  private String userName;
  private String userImg;

  /**
   * 0 离线 1在线 2准备
   */
  private Integer userStatus;

  /**
   * 角色 0 参训人 1 组训人
   */
  private Integer role;

  /**
   * 得分
   */
  private BigDecimal score;

  /**
   * 扣分详情
   */
  private String deductInfo;

  /**
   * 错误个数
   */
  private Integer errorNumber;

  /**
   * 正确率
   */
  private String accuracy;

  /**
   * 速率
   */
  private String speed;

  /**
   * 已填报的页数
   */
  private Integer existPageNumber;

  /**
   * 已上传的页码
   */
  private List<Integer> existNumber;

  /**
   * 0未完成 1完成
   */
  private Integer isFinish;

  /**
   * 有效时长
   */
  @Schema(title = "有效时长")
  private Long duration;

  private Integer totalNumber;
  private Integer pageCount;

  /**
   * 评分规则
   */
  private String ruleContent;

  /**
   * 结束时间
   */
  private LocalDateTime finishTime;

  /**
   * 用户拍发的内容
   */
  List<GeneralKeyPatTrainUserValueVO> patValue;

  @Schema(title = "每页统计信息")
  private List<PostTelegraphKeyPatTrainPageAnalyzeVO> pageAnalyzeVOS;

  @Schema(title = "已存在的页码数")
  private List<Integer> existPage;

  /**
   * 报文内容
   */
  @Schema(title = "报文内容")
  private List<PostTelegraphKeyPatTrainPageMessageVO> content;

}
