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
@Schema(name = "训练页面对象")
public class TickerTapeTrainVo {

  @Schema(name = "id",title = "id")
  private String id;

  @Schema(name = "name",title = "训练名称")
  private String name;

  /**
   * '基础练习配置表Id
   */
  @Schema(name = "trainId",title = "基础练习配置Id")
  private String trainId;

  /**
   * 0:数码报 1 字码报 2 混合报
   */
  @Schema(name = "type",title = "'0': '字码报','1': '数码报','2': '混合报','11': '点报','12': '划报','13': '点划报','14': '点划连接报")
  private Integer type;



  /**
   * 0：短码 1:长码
   */
  @Schema(name = "codeShort",title = "0：短码 1:长码")
  private Integer codeShort;



  /**
   * 创建时间
   */
  @Schema(name = "createTime",title = "创建时间")
  @JsonFormat(pattern = "yyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime createTime;

  /**
   * 开始时间
   */
  @Schema(name = "startTime",title = "开始时间")
  @JsonFormat(pattern = "yyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  @Schema(name = "endTime",title = "结束时间")
  @JsonFormat(pattern = "yyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime endTime;

  /**
   * 有效时长
   */
  @Schema(name = "validTime",title = "有效时长")
  private String validTime;

  /**
   * 0:未开始 1:进行中 2：暂停 3：结束
   */
  @Schema(name = "status",title = "0:未开始 1:进行中 2：暂停 3：结束")
  private Integer status;

  @Schema(name = "rate",title = "速率")
  private Integer rate;

  @Schema(name = "codeMessageBody",title = "报文内容")
  private List<Map<String,Object>> codeMessageBody;

  @Schema(name = "mark",title = "上次播报位置")
  private String mark;

  @Schema(name = "schedule",title = "进度")
  private Integer schedule;

  /**
   * 是否低速训练 0是 1否
   */
  @Schema(name = "isLowRate",title = "是否低速训练 0是 1否")
  private Integer isLowRate;
}
