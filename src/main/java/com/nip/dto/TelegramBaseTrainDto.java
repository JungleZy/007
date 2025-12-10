package com.nip.dto;


import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-06-07 09:46
 * @Description:
 */
@Data
@Schema(title = "手键拍发基础练习Dto对象")
public class TelegramBaseTrainDto {

  @Schema(title = "id")
  private String id;

  @Schema(title = "3已完成")
  private Integer status;

  @Schema(title = "0.数码报，1.字码报，2.混合报, 11.点报，12.划报，13.点划报，14.点划连接报 21基础训练")
  private Integer type;

  @Schema(title = "总敲击次数")
  private Integer totalKnockNumber;

  @Schema(title = "时长")
  private String sustainTime;

}
