package com.nip.service.enums;

/**
 * 检测类型枚举
 * 定义报文对比过程中的各种检测类型，替代布尔标志提高代码可读性
 * 
 * @author system
 * @date 2024-12-19
 */
public enum DetectionType {

  /** 多行检测 */
  MORE_LINE("多行", "拍发的行数超过标准报文"),

  /** 少行检测 */
  LESS_LINE("少行", "拍发的行数少于标准报文"),

  /** 多组检测 */
  MORE_GROUP("多组", "拍发的组数超过标准报文"),

  /** 少组检测 */
  LESS_GROUP("少组", "拍发的组数少于标准报文"),

  /** 串组检测 */
  BUNCH_GROUP("串组", "拍发报文与上下行报文相同"),

  /** 错码检测 */
  ERROR_CODE("错码", "拍发报文与标准报文不匹配"),

  /** 字间隔过小 */
  WORD_GAP_SMALL("字间隔过小", "报文中包含字间隔标识符"),

  /** 字间隔过大 */
  WORD_GAP_LARGE("字间隔过大", "报文被拆分成多个部分"),

  /** 多少码 */
  MORE_OR_LACK_WORD("多少码", "报文长度不符合标准"),

  /** 正确 */
  CORRECT("正确", "报文匹配标准");

  private final String displayName;
  private final String description;

  DetectionType(String displayName, String description) {
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
