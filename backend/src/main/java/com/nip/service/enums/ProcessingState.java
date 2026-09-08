package com.nip.service.enums;

/**
 * 处理状态枚举
 * 定义报文对比过程中的处理状态
 * 
 * @author system
 * @date 2024-12-19
 */
public enum ProcessingState {

  /** 正常处理状态 */
  NORMAL("正常", "按照标准流程进行对比"),

  /** 多行少行模式 */
  MORE_OR_LACK_LINE_MODE("多行少行模式", "检测到多行或少行后的特殊处理模式"),

  /** 错误恢复模式 */
  ERROR_RECOVERY("错误恢复", "检测到错误后的恢复处理模式");

  private final String displayName;
  private final String description;

  ProcessingState(String displayName, String description) {
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