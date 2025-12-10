package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class KeyPatStatisticalDto {
  private int pat = 0;              // 拍发码数
  private int patGroup = 0;         // 拍发组数
  private int patTime = 0;          // 拍发时长（毫秒）
  private int lack = 0;             // 少码
  private int more = 0;             // 多码
  private int lackGroup = 0;        // 少组
  private int moreGroup = 0;        // 多组
  private int lackLine = 0;         // 少行
  private int moreLine = 0;         // 多行
  private int alterError = 0;       // 改错
  private int error = 0;            // 错码
  private int bunchGroup = 0;       // 串组
  private int lackGap = 0;          // 少间隔
  private int errorTotal = 0;       // 错误数
}
