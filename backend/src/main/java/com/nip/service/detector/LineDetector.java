package com.nip.service.detector;

import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainResolverDetailVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.service.builder.MessageResultBuilder;
import com.nip.service.context.ComparisonContext;
import com.nip.service.enums.DetectionResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.nip.common.utils.TickerPatUtils.checkDotLineGap;
import static com.nip.service.constants.MessageComparisonConstants.*;

/**
 * 多行少行检测器
 * 专门处理报文对比中的多行和少行检测逻辑
 * 
 * @author system
 * @date 2024-12-19
 */
@Slf4j
public class LineDetector {

  /**
   * 多行少行检测结果。多行分支会在检测器内部对后续若干组 checkDotLineGap，
   * 主循环必须按 skipCount 跳过这些组，否则会被重复统计。
   */
  public static class LineResult {
    private final DetectionResult result;
    private final int skipCount;

    LineResult(DetectionResult result, int skipCount) {
      this.result = result;
      this.skipCount = skipCount;
    }

    public DetectionResult getResult() {
      return result;
    }

    public int getSkipCount() {
      return skipCount;
    }
  }

  private static final LineResult SKIPPED = new LineResult(DetectionResult.SKIPPED, 0);

  private static final LineResult FAILED = new LineResult(DetectionResult.FAILED, 0);

  /**
   * 检测多行少行
   *
   * @param context       对比上下文
   * @param currentIndex  当前处理的索引
   * @param patKey        当前拍发报文
   * @param resultBuilder 结果构建器
   * @return 检测结果，SUCCESS 时携带主循环需跳过的组数
   */
  public LineResult detectMoreOrLessLine(ComparisonContext context,
      int currentIndex,
      String patKey,
      MessageResultBuilder resultBuilder) {

    int sourceIndex = context.getSourceIndex();

    // 只在行首位置进行多行少行检测
    if (sourceIndex % GROUPS_PER_LINE != 0) {
      return SKIPPED;
    }

    // 先检测多行
    LineResult moreLineResult = detectMoreLine(context, currentIndex, patKey, resultBuilder);
    if (moreLineResult.getResult() == DetectionResult.SUCCESS) {
      return moreLineResult;
    }

    // 再检测少行
    LineResult lessLineResult = detectLessLine(context, currentIndex, patKey, resultBuilder);
    if (lessLineResult.getResult() == DetectionResult.SUCCESS) {
      return lessLineResult;
    }

    return FAILED;
  }

  /**
   * 检测多行
   * 与原报文的上一行第一组比对，如相同且有5组相同，则判定为多行
   */
  private LineResult detectMoreLine(ComparisonContext context,
      int currentIndex,
      String patKey,
      MessageResultBuilder resultBuilder) {

    List<String> resolverPatKeys = context.getResolverVO().getResolverMessage();
    List<String> sources = context.getSources();
    int sourceIndex = context.getSourceIndex();

    int previousLineIndex = sourceIndex - GROUPS_PER_LINE;
    if (previousLineIndex < 0 || !Objects.equals(patKey, sources.get(previousLineIndex))) {
      return FAILED;
    }

    List<String> moreLine = new ArrayList<>();
    moreLine.add(patKey);
    int matchCount = 0;

    // 检查后续9组是否匹配
    for (int j = 1; j <= MORE_LINE_GROUP_RANGE; j++) {
      if (currentIndex + j >= resolverPatKeys.size()) {
        break;
      }

      String nextLineGroup = resolverPatKeys.get(currentIndex + j);
      moreLine.add(nextLineGroup);

      // 与上一行的对应组进行比对
      for (int k = 1; k <= MORE_LINE_GROUP_RANGE; k++) {
        if (previousLineIndex + k >= sources.size()) {
          break;
        }
        String thisLineGroup = sources.get(previousLineIndex + k);
        if (Objects.equals(thisLineGroup, nextLineGroup)) {
          matchCount++;
          break;
        }
      }
    }

    // 如果匹配组数达到阈值，判定为多行
    if (matchCount >= MIN_MATCHING_GROUPS) {
      handleMoreLineDetected(context, moreLine, sourceIndex, currentIndex, patKey, resultBuilder);
      // moreLine = 当前组 + 检测器内部已 checkDotLineGap 的后续组，后者交由主循环跳过
      return new LineResult(DetectionResult.SUCCESS, moreLine.size() - 1);
    }

    return FAILED;
  }

  /**
   * 检测少行
   * 与源报文下面连续两行第一个相同且有5组相同，则判定为少行
   */
  private LineResult detectLessLine(ComparisonContext context,
      int currentIndex,
      String patKey,
      MessageResultBuilder resultBuilder) {

    List<String> resolverPatKeys = context.getResolverVO().getResolverMessage();
    List<String> sources = context.getSources();
    int sourceIndex = context.getSourceIndex();

    // 检查接下来的1-2行
    for (int lineOffset = 1; lineOffset <= MAX_LESS_LINE_ROWS; lineOffset++) {
      int nextLineIndex = sourceIndex + lineOffset * GROUPS_PER_LINE;

      if (nextLineIndex >= sources.size() || !Objects.equals(patKey, sources.get(nextLineIndex))) {
        continue;
      }

      int matchCount = countMatchingGroups(resolverPatKeys, sources, currentIndex, nextLineIndex);

      if (matchCount >= MIN_MATCHING_GROUPS) {
        handleLessLineDetected(context, currentIndex, patKey, lineOffset, resultBuilder);
        return new LineResult(DetectionResult.SUCCESS, 0);
      }
    }

    return FAILED;
  }

  /**
   * 统计匹配的组数
   */
  private int countMatchingGroups(List<String> resolverPatKeys,
      List<String> sources,
      int currentIndex,
      int nextLineIndex) {
    int count = 0;

    for (int k = 1; k <= MORE_LINE_GROUP_RANGE; k++) {
      if (currentIndex + k >= resolverPatKeys.size() ||
          nextLineIndex + k >= sources.size()) {
        break;
      }

      String patNextGroup = resolverPatKeys.get(currentIndex + k);
      String sourceNextLineGroup = sources.get(nextLineIndex + k);

      if (Objects.equals(patNextGroup, sourceNextLineGroup)) {
        count++;
      }
    }

    return count;
  }

  /**
   * 处理检测到多行的情况
   */
  private void handleMoreLineDetected(ComparisonContext context,
      List<String> moreLine,
      int sourceIndex,
      int currentIndex,
      String patKey,
      MessageResultBuilder resultBuilder) {

    PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
    PostTelegramTrainRule rule = context.getRule();
    PostTelegramTrainStatisticsVO statisticsVO = context.getStatisticsVO();
    List<PostTelegramTrainFinishInfoDto> standards = context.getStandards();
    List<String> resolverCorrectPatLogs = context.getResolverVO().getResolverPatLogs();

    // 创建多行详情
    PostTelegramTrainResolverDetailVO detailVO = new PostTelegramTrainResolverDetailVO();
    detailVO.setMessage(moreLine);
    detailVO.setPoint(sourceIndex / GROUPS_PER_LINE);

    List<PostTelegramTrainResolverDetailVO> moreLinesData = context.getResolverVO().getMoreLine();
    if (moreLinesData == null) {
      moreLinesData = new ArrayList<>();
      context.getResolverVO().setMoreLine(moreLinesData);
    }
    moreLinesData.add(detailVO);

    // 当前组虽属多行，其拍发日志/点划/耗时仍须落入结果，参照 GroupDetector.handleMoreGroupDetected
    resultBuilder.addCorrectMessage(patKey,
        getSafeString(context.getResolverVO().getResolverMoresValue(), currentIndex),
        getSafeString(context.getResolverVO().getResolverMoresTime(), currentIndex),
        getSafeString(resolverCorrectPatLogs, currentIndex));

    // 更新评分
    scoreVO.setMoreOrLackLine(scoreVO.getMoreOrLackLine() + 1);

    // 设置多行少行模式
    context.setMoreOrLackLineMode(true);

    // 调整源报文索引，保持不变
    context.decrementSourceIndex(1);

    // 处理多行后的9组，统计点划间隔
    for (int j = 1; j <= MORE_LINE_GROUP_RANGE; j++) {
      if (currentIndex + j >= context.getResolverVO().getResolverMessage().size()) {
        break;
      }
      String nextLineGroup = context.getResolverVO().getResolverMessage().get(currentIndex + j);
      String patLog = getSafeString(resolverCorrectPatLogs, currentIndex + j);

      checkDotLineGap(nextLineGroup, currentIndex + j, patLog, standards, rule, false,
          statisticsVO, scoreVO);
    }

    log.debug("检测到多行，位置: {}, 匹配组数: {}", sourceIndex / GROUPS_PER_LINE, moreLine.size());
  }

  /**
   * 处理检测到少行的情况
   */
  private void handleLessLineDetected(ComparisonContext context,
      int currentIndex,
      String patKey,
      int missingLines,
      MessageResultBuilder resultBuilder) {

    PostTelegramTrainScoreVO scoreVO = context.getScoreVO();

    // 添加空的报文结果，表示少行
    int emptyGroupCount = missingLines * GROUPS_PER_LINE;
    resultBuilder.addEmptyMessages(emptyGroupCount);

    // 当前组对上的是 sources[sourceIndex + emptyGroupCount]，须写入结果，参照 GroupDetector.handleLessGroupDetected
    resultBuilder.addCorrectMessage(patKey,
        getSafeString(context.getResolverVO().getResolverMoresValue(), currentIndex),
        getSafeString(context.getResolverVO().getResolverMoresTime(), currentIndex),
        getSafeString(context.getResolverVO().getResolverPatLogs(), currentIndex));

    // 更新评分
    scoreVO.setMoreOrLackLine(scoreVO.getMoreOrLackLine() + missingLines);

    // 调整源报文索引：跳过缺失的整行，当前组由主循环末尾的 incrementSourceIndex() 消费
    context.incrementSourceIndex(emptyGroupCount);

    // 设置多行少行模式
    context.setMoreOrLackLineMode(true);

    log.debug("检测到少行，缺少行数: {}, 空组数: {}", missingLines, emptyGroupCount);
  }

  /**
   * 获取安全的字符串值（越界取空串，与 GroupDetector.getSafeString 同语义）
   */
  private String getSafeString(List<String> list, int index) {
    if (list == null || index < 0 || index >= list.size()) {
      return "";
    }
    return list.get(index);
  }
}