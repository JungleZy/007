package com.nip.service.enums;

/**
 * 检测结果枚举
 * 定义检测操作的结果状态
 * 
 * @author system
 * @date 2024-12-19
 */
public enum DetectionResult {

  /** 检测成功，找到匹配 */
  SUCCESS("成功", "检测操作成功完成"),

  /** 检测失败，未找到匹配 */
  FAILED("失败", "检测操作未找到匹配项"),

  /** 跳过检测 */
  SKIPPED("跳过", "由于条件不满足跳过检测"),

  /** 需要继续检测 */
  CONTINUE("继续", "需要进行后续检测"),

  /** 检测中断 */
  INTERRUPTED("中断", "检测过程被中断");

  private final String displayName;
  private final String description;

  DetectionResult(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }
}