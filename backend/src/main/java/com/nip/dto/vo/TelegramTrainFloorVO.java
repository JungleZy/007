package com.nip.dto.vo;

import lombok.Data;

/**
 * TelegramTrainFloorEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:02
 */
@Data
public class TelegramTrainFloorVO {
  private String id;
  private String trainId;
  private Integer sort;
  // 0 字码报 1数码报 2混合报  11电报 12 划报 13点划报(已弃用) 14 电划连接报
  private Integer type = 0;
  private Integer numberType = 0;
  private Integer contentNumber = 0;
  private Integer errorNumber = 0;
}
