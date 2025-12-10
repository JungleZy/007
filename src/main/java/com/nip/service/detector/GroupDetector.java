package com.nip.service.detector;

import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainResolverDetailVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.service.builder.MessageResultBuilder;
import com.nip.service.context.ComparisonContext;
import com.nip.service.enums.DetectionResult;
import com.nip.service.enums.DetectionType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.nip.common.utils.TickerPatUtils.checkDotLineGap;
import static com.nip.service.constants.MessageComparisonConstants.*;

/**
 * 多组少组检测器
 * 专门处理报文对比中的多组和少组检测逻辑
 * 
 * @author system
 * @date 2024-12-19
 */
@Slf4j
public class GroupDetector {

  /**
   * 检测多组少组
   * 
   * @param context       对比上下文
   * @param currentIndex  当前处理的索引
   * @param patKey        当前拍发报文
   * @param source        当前标准报文
   * @param resultBuilder 结果构建器
   * @return 检测结果
   */
  public DetectionResult detectMoreOrLessGroup(ComparisonContext context,
      int currentIndex,
      String patKey,
      String source,
      MessageResultBuilder resultBuilder) {

    // 先检测多组
    DetectionResult moreGroupResult = detectMoreGroup(context, currentIndex, patKey, source, resultBuilder);
    if (moreGroupResult == DetectionResult.SUCCESS) {
      return DetectionResult.SUCCESS;
    }

    // 再检测少组
    DetectionResult lessGroupResult = detectLessGroup(context, currentIndex, patKey, source, resultBuilder);
    if (lessGroupResult == DetectionResult.SUCCESS) {
      return DetectionResult.SUCCESS;
    }

    return DetectionResult.FAILED;
  }

  /**
   * 检测多组
   * 当前源报文和后拍发4组报文比对，如果有相等的则说明这中间的均是多组
   */
  private DetectionResult detectMoreGroup(ComparisonContext context,
      int currentIndex,
      String patKey,
      String source,
      MessageResultBuilder resultBuilder) {

    List<String> resolverPatKeys = context.getResolverVO().getResolverMessage();
    List<String> resolverCorrectPatLogs = context.getResolverVO().getResolverPatLogs();
    List<String> resolverCorrectMoresTime = context.getResolverVO().getResolverMoresTime();
    List<String> resolverCorrectMoresValue = context.getResolverVO().getResolverMoresValue();

    // 确定搜索范围
    int searchRange = context.isMoreOrLackLineMode() ? EXTENDED_SEARCH_RANGE : STANDARD_SEARCH_RANGE;

    List<String> moreGroup = new ArrayList<>();
    moreGroup.add(patKey);

    // 检查后续组是否匹配标准报文
    for (int j = 1; j <= searchRange; j++) {
      int nextIndex = currentIndex + j;
      if (nextIndex >= resolverPatKeys.size()) {
        break;
      }

      String nextGroup = resolverPatKeys.get(nextIndex);
      if (Objects.equals(nextGroup, source)) {
        // 找到匹配，处理多组
        handleMoreGroupDetected(context, currentIndex, j, moreGroup,
            source, resolverCorrectMoresValue, resolverCorrectMoresTime,
            resolverCorrectPatLogs, resultBuilder);
        return DetectionResult.SUCCESS;
      }
      moreGroup.add(nextGroup);
    }

    return DetectionResult.FAILED;
  }

  /**
   * 检测少组
   * 当前拍发报文和源报文中后4个报文进行比对，如果有相等则说明是少组
   */
  private DetectionResult detectLessGroup(ComparisonContext context,
      int currentIndex,
      String patKey,
      String source,
      MessageResultBuilder resultBuilder) {

    List<String> sources = context.getSources();
    int sourceIndex = context.getSourceIndex();

    // 确定搜索范围
    int searchRange = context.isMoreOrLackLineMode() ? EXTENDED_SEARCH_RANGE : STANDARD_SEARCH_RANGE;

    // 检查后续标准报文是否匹配当前拍发报文
    for (int j = 1; j <= searchRange; j++) {
      int nextSourceIndex = sourceIndex + j;
      if (nextSourceIndex >= sources.size()) {
        break;
      }

      String nextSourceGroup = sources.get(nextSourceIndex);
      if (Objects.equals(nextSourceGroup, patKey)) {
        // 找到匹配，处理少组
        handleLessGroupDetected(context, currentIndex, j, resultBuilder);
        return DetectionResult.SUCCESS;
      }
    }

    return DetectionResult.FAILED;
  }

  /**
   * 处理检测到多组的情况
   */
  private void handleMoreGroupDetected(ComparisonContext context,
      int currentIndex,
      int skipCount,
      List<String> moreGroup,
      String source,
      List<String> resolverCorrectMoresValue,
      List<String> resolverCorrectMoresTime,
      List<String> resolverCorrectPatLogs,
      MessageResultBuilder resultBuilder) {

    PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
    PostTelegramTrainRule rule = context.getRule();
    PostTelegramTrainStatisticsVO statisticsVO = context.getStatisticsVO();
    List<PostTelegramTrainFinishInfoDto> standards = context.getStandards();
    List<String> resolverPatKeys = context.getResolverVO().getResolverMessage();
    List<String> resolverCorrectPatLogs2 = context.getResolverVO().getResolverPatLogs();

    // 添加正确的标准报文到结果
    String moresValue = resolverCorrectMoresValue.get(currentIndex);
    String moresTime = resolverCorrectMoresTime.get(currentIndex);
    String patLog = resolverCorrectPatLogs.get(currentIndex);
    resultBuilder.addCorrectMessage(source, moresValue, moresTime, patLog);

    // 记录多组详情
    resultBuilder.addMoreGroupDetail(context.getSourceIndex(), moreGroup);

    // 统计多组中的点划间隔
    for (int m = 1; m <= skipCount; m++) {
      if (currentIndex + m >= resolverPatKeys.size()) {
        break;
      }
      String nextMoreGroup = resolverPatKeys.get(currentIndex + m);
      String nextPatLog = resolverCorrectPatLogs2.get(currentIndex + m);

      checkDotLineGap(nextMoreGroup, currentIndex + m, nextPatLog, standards, rule, false,
          statisticsVO, scoreVO);
    }

    // 更新评分
    scoreVO.setMoreGroup(scoreVO.getMoreGroup() + skipCount);

    log.debug("检测到多组，跳过组数: {}, 位置: {}", skipCount, context.getSourceIndex());
  }

  /**
   * 处理检测到少组的情况
   */
  private void handleLessGroupDetected(ComparisonContext context,
      int currentIndex,
      int missingCount,
      MessageResultBuilder resultBuilder) {

    PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
    List<String> resolverCorrectMoresValue = context.getResolverVO().getResolverMoresValue();
    List<String> resolverCorrectMoresTime = context.getResolverVO().getResolverMoresTime();
    List<String> resolverCorrectPatLogs = context.getResolverVO().getResolverPatLogs();
    List<String> sources = context.getSources();
    int sourceIndex = context.getSourceIndex();

    // 添加空的报文结果，表示少组
    resultBuilder.addEmptyMessages(missingCount);

    // 添加匹配的报文
    String nextGroup = sources.get(sourceIndex + missingCount);
    String moresValue = resolverCorrectMoresValue.get(currentIndex);
    String moresTime = resolverCorrectMoresTime.get(currentIndex);
    String patLog = resolverCorrectPatLogs.get(currentIndex);
    resultBuilder.addCorrectMessage(nextGroup, moresValue, moresTime, patLog);

    // 调整源报文索引
    context.incrementSourceIndex(missingCount);

    // 更新评分
    scoreVO.setLackGroup(scoreVO.getLackGroup() + missingCount);

    log.debug("检测到少组，缺少组数: {}, 位置: {}", missingCount, sourceIndex);
  }

  /**
   * 检查索引是否超出范围
   */
  private boolean isIndexOutOfRange(int index, int size) {
    return index < 0 || index >= size;
  }

  /**
   * 获取安全的字符串值
   */
  private String getSafeString(List<String> list, int index) {
    if (isIndexOutOfRange(index, list.size())) {
      return "";
    }
    return list.get(index);
  }

  /**
   * 计算多组检测的搜索范围
   */
  public int getSearchRange(ComparisonContext context) {
    return context.isMoreOrLackLineMode() ? EXTENDED_SEARCH_RANGE : STANDARD_SEARCH_RANGE;
  }

  /**
   * 检查是否应该跳过多组少组检测
   */
  public boolean shouldSkipGroupDetection(ComparisonContext context, int currentIndex) {
    // 如果当前索引超出范围，跳过检测
    if (currentIndex >= context.getResolverVO().getResolverMessage().size()) {
      return true;
    }

    // 如果源报文索引超出范围，跳过检测
    if (context.getSourceIndex() >= context.getSources().size()) {
      return true;
    }

    return false;
  }
}