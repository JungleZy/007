package com.nip.dto.vo;

import com.nip.entity.UserEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-03-31 12:00
 * @Description:
 */
@Data
public class ComprehensiveVO {

  private UserEntity userEntity;

  //@ApiModelProperty(value = "参加考试次数",position = 1 )
  private Integer examNum;

  //@ApiModelProperty(value = "及格次数",position = 2)
  private Integer passNum;

  //@ApiModelProperty(value = "已学习课件",position = 3)
  private Integer swfNum;

  //@ApiModelProperty(value = "学习时长(小时)",position = 4)
  private BigDecimal studyTime;

  //@ApiModelProperty(value = "易错题",position = 5)
  private List<String> errorTopic;


  //@ApiModelProperty(value = "理论学习时长",position = 6)
  private BigDecimal theoryTime;

  //@ApiModelProperty(value = "已得总学分",position = 7)
  private BigDecimal totalCredit;

  //@ApiModelProperty(value = "理论测试最高得分",position = 8)
  private BigDecimal theoryTestMaxCredit;

  //@ApiModelProperty(value = "理论测试最低得分",position = 9)
  private BigDecimal theoryTestMinCredit;

  //@ApiModelProperty(value = "理论测试平均得分",position = 10)
  private BigDecimal theoryTestAvgCredit;

}
