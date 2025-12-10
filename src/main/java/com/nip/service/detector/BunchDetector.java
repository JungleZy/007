package com.nip.service.detector;

import com.nip.service.context.ComparisonContext;
import com.nip.service.enums.DetectionResult;
import com.nip.service.enums.DetectionType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

import static com.nip.service.constants.MessageComparisonConstants.*;

/**
 * 串组检测器
 * 专门处理报文对比中的串组检测逻辑
 * 串组是指拍发报文与上下行报文相同的错误
 * 
 * @author system
 * @date 2024-12-19
 */
@Slf4j
public class BunchDetector {

  /**
   * 检测串组
   * 与源报文上下行报文检查是否相等，如果相等则是串组
   * 
   * @param context 对比上下文
   * @param patKey  当前拍发报文
   * @return 检测结果
   */
  public DetectionResult detectBunch(ComparisonContext context, String patKey) {

    // 检查是否应该跳过串组检测
    if (shouldSkipBunchDetection(context)) {
      return DetectionResult.FAILED;
    }

    List<String> sources = context.getSources();
    int sourceIndex = context.getSourceIndex();

    // 检查上一行串组
    if (detectUpperLineBunch(sources, sourceIndex, patKey)) {
      handleBunchDetected(context, DetectionType.BUNCH_GROUP, "上一行串组");
      return DetectionResult.SUCCESS;
    }

    // 检查下一行串组
    if (detectLowerLineBunch(sources, sourceIndex, patKey)) {
      handleBunchDetected(context, DetectionType.BUNCH_GROUP, "下一行串组");
      return DetectionResult.SUCCESS;
    }

    return DetectionResult.FAILED;
  }

  /**
   * 检测上一行串组
   * 检查当前拍发报文是否与上一行对应位置的报文相同
   * 
   * @param sources     源报文列表
   * @param sourceIndex 当前源报文索引
   * @param patKey      当前拍发报文
   * @return 如果检测到上一行串组返回true
   */
  private boolean detectUpperLineBunch(List<String> sources, int sourceIndex, String patKey) {
    // 计算上一行对应位置的索引
    int upperLineIndex = sourceIndex - GROUPS_PER_LINE;

    // 检查索引是否有效
    if (!isValidIndex(sources, upperLineIndex)) {
      return false;
    }

    String upperLineGroup = sources.get(upperLineIndex);
    boolean isMatch = Objects.equals(patKey, upperLineGroup);

    if (isMatch) {
      log.debug("检测到上一行串组，当前索引: {}, 上一行索引: {}, 报文: {}",
          sourceIndex, upperLineIndex, patKey);
    }

    return isMatch;
  }

  /**
   * 检测下一行串组
   * 检查当前拍发报文是否与下一行对应位置的报文相同
   * 
   * @param sources     源报文列表
   * @param sourceIndex 当前源报文索引
   * @param patKey      当前拍发报文
   * @return 如果检测到下一行串组返回true
   */
  private boolean detectLowerLineBunch(List<String> sources, int sourceIndex, String patKey) {
    // 计算下一行对应位置的索引
    int lowerLineIndex = sourceIndex + GROUPS_PER_LINE;

    // 检查索引是否有效
    if (!isValidIndex(sources, lowerLineIndex)) {
      return false;
    }

    String lowerLineGroup = sources.get(lowerLineIndex);
    boolean isMatch = Objects.equals(patKey, lowerLineGroup);

    if (isMatch) {
      log.debug("检测到下一行串组，当前索引: {}, 下一行索引: {}, 报文: {}",
          sourceIndex, lowerLineIndex, patKey);
    }

    return isMatch;
  }

  /**
   * 检测任意位置串组
   * 检查拍发报文是否与指定范围内的源报文相同
   * 
   * @param context     对比上下文
   * @param patKey      当前拍发报文
   * @param searchRange 搜索范围
   * @return 检测结果
   */
  public DetectionResult detectBunchInRange(ComparisonContext context, String patKey, int searchRange) {

    List<String> sources = context.getSources();
    int sourceIndex = context.getSourceIndex();

    // 向前搜索整行
    for (int i = 1; i <= searchRange; i++) {
      int lineStartIndex = sourceIndex - i * GROUPS_PER_LINE;
      // 检查整行
      for (int j = 0; j < GROUPS_PER_LINE; j++) {
        int checkIndex = lineStartIndex + j;
        if (isValidIndex(sources, checkIndex) &&
            Objects.equals(patKey, sources.get(checkIndex))) {

          handleBunchDetected(context, DetectionType.BUNCH_GROUP,
              String.format("向前%d行串组", i));
          return DetectionResult.SUCCESS;
        }
      }
    }

    // 向后搜索整行
    for (int i = 1; i <= searchRange; i++) {
      int lineStartIndex = sourceIndex + i * GROUPS_PER_LINE;
      // 检查整行
      for (int j = 0; j < GROUPS_PER_LINE; j++) {
        int checkIndex = lineStartIndex + j;
        if (isValidIndex(sources, checkIndex) &&
            Objects.equals(patKey, sources.get(checkIndex))) {

          handleBunchDetected(context, DetectionType.BUNCH_GROUP,
              String.format("向后%d行串组", i));
          return DetectionResult.SUCCESS;
        }
      }
    }

    return DetectionResult.FAILED;
  }

  /**
   * 检测同行串组
   * 检查拍发报文是否与同一行其他位置的报文相同
   * 
   * @param context 对比上下文
   * @param patKey  当前拍发报文
   * @return 检测结果
   */
  public DetectionResult detectSameLineBunch(ComparisonContext context, String patKey) {

    List<String> sources = context.getSources();
    int sourceIndex = context.getSourceIndex();

    // 计算当前行的起始和结束索引
    int lineStartIndex = (sourceIndex / GROUPS_PER_LINE) * GROUPS_PER_LINE;
    int lineEndIndex = Math.min(lineStartIndex + GROUPS_PER_LINE - 1, sources.size() - 1);

    // 检查同一行的其他位置
    for (int i = lineStartIndex; i <= lineEndIndex; i++) {
      if (i != sourceIndex && isValidIndex(sources, i) &&
          Objects.equals(patKey, sources.get(i))) {

        handleBunchDetected(context, DetectionType.BUNCH_GROUP,
            String.format("同行串组，位置: %d", i - lineStartIndex));
        return DetectionResult.SUCCESS;
      }
    }

    return DetectionResult.FAILED;
  }

  /**
   * 处理检测到串组的情况
   * 
   * @param context       对比上下文
   * @param detectionType 检测类型
   * @param description   描述信息
   */
  private void handleBunchDetected(ComparisonContext context, DetectionType detectionType, String description) {

    // 更新串组计数
    context.getScoreVO().setBunchGroup(context.getScoreVO().getBunchGroup() + 1);

    log.info("检测到串组错误: {}, 位置: {}, 描述: {}",
        detectionType.getDisplayName(), context.getSourceIndex(), description);
  }

  /**
   * 检查索引是否有效
   * 
   * @param sources 源报文列表
   * @param index   要检查的索引
   * @return 如果索引有效返回true
   */
  private boolean isValidIndex(List<String> sources, int index) {
    return sources != null && index >= 0 && index < sources.size();
  }

  /**
   * 获取指定位置的行号
   * 
   * @param index 索引位置
   * @return 行号（从0开始）
   */
  public int getLineNumber(int index) {
    return index / GROUPS_PER_LINE;
  }

  /**
   * 获取指定位置在行内的列号
   * 
   * @param index 索引位置
   * @return 列号（从0开始）
   */
  public int getColumnNumber(int index) {
    return index % GROUPS_PER_LINE;
  }

  /**
   * 计算两个位置之间的行数差
   * 
   * @param index1 第一个位置
   * @param index2 第二个位置
   * @return 行数差的绝对值
   */
  public int getLineDifference(int index1, int index2) {
    return Math.abs(getLineNumber(index1) - getLineNumber(index2));
  }

  /**
   * 检查是否应该跳过串组检测
   * 
   * @param context 对比上下文
   * @return 如果应该跳过返回true
   */
  public boolean shouldSkipBunchDetection(ComparisonContext context) {
    // 如果源报文列表为空或只有一行，跳过串组检测
    List<String> sources = context.getSources();
    if (sources == null || sources.size() <= GROUPS_PER_LINE) {
      return true;
    }

    // 如果当前位置无效，跳过检测
    int sourceIndex = context.getSourceIndex();
    if (!isValidIndex(sources, sourceIndex)) {
      return true;
    }

    return false;
  }
}