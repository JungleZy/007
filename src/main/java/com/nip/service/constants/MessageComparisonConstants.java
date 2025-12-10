package com.nip.service.constants;

/**
 * 报文对比服务常量类
 * 定义报文对比过程中使用的所有常量值，消除魔法数字
 * 
 * @author system
 * @date 2024-12-19
 */
public final class MessageComparisonConstants {

  /**
   * 私有构造函数，防止实例化
   */
  private MessageComparisonConstants() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // ==================== 基础常量 ====================

  /** 空JSON数组字符串 */
  public static final String EMPTY_JSON_ARRAY = "[]";

  /** 每行最大组数 */
  public static final int GROUPS_PER_LINE = 10;

  /** 标准报文组长度 */
  public static final int STANDARD_GROUP_LENGTH = 4;

  /** 最大sSign值，超过此值使用最后一个源报文 */
  public static final int MAX_SIGN_VALUE = 99;

  // ==================== 检测范围常量 ====================

  /** 最小匹配组数，用于判断多行少行 */
  public static final int MIN_MATCHING_GROUPS = 5;

  /** 标准搜索范围，用于多组少组检测 */
  public static final int STANDARD_SEARCH_RANGE = 4;

  /** 扩展搜索范围，在多行少行模式下使用 */
  public static final int EXTENDED_SEARCH_RANGE = 10;

  /** 多行检测时的组数范围 */
  public static final int MORE_LINE_GROUP_RANGE = 9;

  /** 少行检测时的最大行数 */
  public static final int MAX_LESS_LINE_ROWS = 2;

  // ==================== 索引计算常量 ====================

  /** 用于计算上一行索引的偏移量 */
  public static final int PREVIOUS_LINE_OFFSET = 10;

  /** 用于计算下一行索引的偏移量 */
  public static final int NEXT_LINE_OFFSET = 10;

  /** 行首位置判断的除数 */
  public static final int LINE_START_DIVISOR = 10;

  // ==================== 正则表达式常量 ====================

  /** 清理报文内容的正则表达式 */
  public static final String CLEANUP_REGEX = "[\",\\]]";

  /** 字间隔标识符 */
  public static final String WORD_GAP_MARKER = "#";

  // ==================== 性能优化常量 ====================

  /** JSON缓存的最大大小 */
  public static final int JSON_CACHE_MAX_SIZE = 1000;

  /** 批处理的批次大小 */
  public static final int BATCH_PROCESSING_SIZE = 100;
}