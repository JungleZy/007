package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 17:13
 * @Description:
 */
@Data
//@ApiModel("岗位训练-训练页面对象")
public class PostTickerTapeTrainVo {

  //@ApiModelProperty(value = "id",position = 1)
  private String id;

  //@ApiModelProperty(value = "训练名称",position = 2)
  private String name;

  private Integer isCable;

  /**
   * '基础练习配置表Id
   */
  //ApiModelProperty(value = "基础练习配置Id",position = 3)
  private String trainId;

  /**
   * 0:数码报 1 字码报 2 混合报
   */
  //@ApiModelProperty(value = "0 数码报 1 字码报 2 混合报",position = 4)
  private Integer type;

  /**
   * 0：短码 1:长码
   */
  //@ApiModelProperty(value = "0：短码 1:长码",position = 7)
  private Integer codeShort;

  /**
   * 创建时间
   */
  //@ApiModelProperty(value = "创建时间",position = 8)
  @JsonFormat(pattern = "yyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime createTime;

  /**
   * 开始时间
   */
  //@ApiModelProperty(value = "开始时间",position = 9)
  @JsonFormat(pattern = "yyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  //@ApiModelProperty(value = "结束时间",position = 10)
  @JsonFormat(pattern = "yyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime endTime;

  /**
   * 有效时长
   */
  //@ApiModelProperty(value = "有效时长",position = 11)
  private String validTime;

  /**
   * 0:未开始 1:进行中 2：暂停 3：结束
   */
  //@ApiModelProperty(value = "0:未开始 1:进行中 2：完成 3：已提交结果",position = 12)
  private Integer status;

  //@ApiModelProperty(value = "速率",position = 13)
  private Integer rate;

  private Integer messageNumber;

  //@ApiModelProperty(value = "报文内容", position = 14)
  private List<Map<String, Object>> codeMessageBody;

  //@ApiModelProperty(value = "分数",position = 15)
  private String score;

  //@ApiModelProperty(value = "结果",position = 16)
  private String result;

  //@ApiModelProperty(value = "多码",position = 17)
  private Integer moreCode;

  /**
   * 少码
   */
  //@ApiModelProperty(value = "少码",position = 18)
  private Integer lackCode;

  /**
   * 错码
   */
  //Schema(title = "错码",position = 19)
  private Integer errorCode;

  /**
   * 多组
   */
  //@Schema(title = "多组",position = 20)
  private Integer moreGroup;

  /**
   * 少组
   */
  //@Schema(title = "少组",position = 21)
  private Integer lackGroup;

  //@Schema(title = "图片base64",position = 22)
  private List<String> images;

  //@Schema(title = "是否低速训练 0是 1否",position = 23)
  private Integer isLowRate;
  //  @Schema(title = "播报干扰",position = 24)
  private String disturb;

  //  @Schema(title = "总组数",position = 25)
  private Integer totalNumber;
  //  @Schema(title = "比例",position = 26)
  private Integer ratio;
  private Integer pageNumber;

  @Schema(title = "页开始标识，0 否 1 是")
  private Integer isStartSign;
}
