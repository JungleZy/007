package com.nip.service.detector;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.service.builder.MessageResultBuilder;
import com.nip.service.context.ComparisonContext;
import com.nip.service.enums.DetectionResult;
import com.nip.service.enums.DetectionType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

import static com.nip.service.constants.MessageComparisonConstants.*;

/**
 * 错码检测器
 * 专门处理报文对比中的错码检测逻辑，包括：
 * - 字间隔过小/过大检测
 * - 多少码检测
 * - 标准错码检测
 * 
 * @author system
 * @date 2024-12-19
 */
@Slf4j
public class ErrorCodeDetector {

  /**
   * 错码检测结果
   */
  @Data
  public static class ErrorCodeResult {
    private DetectionType detectionType;
    private DetectionResult result;
    private String description;
    private int skipCount; // 需要跳过的组数

    public ErrorCodeResult(DetectionType type, DetectionResult result, String description) {
      this.detectionType = type;
      this.result = result;
      this.description = description;
      this.skipCount = 0;
    }

    public ErrorCodeResult(DetectionType type, DetectionResult result, String description, int skipCount) {
      this.detectionType = type;
      this.result = result;
      this.description = description;
      this.skipCount = skipCount;
    }
  }

  /**
   * 检测错码
   * 
   * @param context       对比上下文
   * @param currentIndex  当前处理的索引
   * @param patKey        当前拍发报文
   * @param source        当前标准报文
   * @param resultBuilder 结果构建器
   * @return 错码检测结果
   */
  public ErrorCodeResult detectErrorCode(ComparisonContext context,
      int currentIndex,
      String patKey,
      String source,
      MessageResultBuilder resultBuilder) {

    // 检查报文长度，判断是否为标准长度
    if (patKey.length() == STANDARD_GROUP_LENGTH) {
      return handleStandardLengthGroup(context, currentIndex, patKey, source, resultBuilder);
    } else {
      return handleNonStandardLengthGroup(context, currentIndex, patKey, source, resultBuilder);
    }
  }

  /**
   * 处理标准长度的报文组
   */
  private ErrorCodeResult handleStandardLengthGroup(ComparisonContext context,
      int currentIndex,
      String patKey,
      String source,
      MessageResultBuilder resultBuilder) {

    List<String> resolverCorrectMoresValue = context.getResolverVO().getResolverMoresValue();
    List<String> resolverCorrectMoresTime = context.getResolverVO().getResolverMoresTime();
    List<String> resolverCorrectPatLogs = context.getResolverVO().getResolverPatLogs();

    // 检查是否包含字间隔标识符
    if (patKey.contains(WORD_GAP_MARKER)) {
      // 字间隔过小
      handleWordGapSmall(context);
      addResultToBuilder(resultBuilder, patKey,
          resolverCorrectMoresValue.get(currentIndex),
          resolverCorrectMoresTime.get(currentIndex),
          resolverCorrectPatLogs.get(currentIndex));

      return new ErrorCodeResult(DetectionType.WORD_GAP_SMALL, DetectionResult.SUCCESS, "字间隔过小");
    } else {
      // 标准错码
      addResultToBuilder(resultBuilder, patKey,
          resolverCorrectMoresValue.get(currentIndex),
          resolverCorrectMoresTime.get(currentIndex),
          resolverCorrectPatLogs.get(currentIndex));

      return new ErrorCodeResult(DetectionType.ERROR_CODE, DetectionResult.SUCCESS, "标准错码");
    }
  }

  /**
   * 处理非标准长度的报文组
   */
  private ErrorCodeResult handleNonStandardLengthGroup(ComparisonContext context,
      int currentIndex,
      String patKey,
      String source,
      MessageResultBuilder resultBuilder) {

    List<String> resolverPatKeys = context.getResolverVO().getResolverMessage();

    // 检查是否为字间隔过大情况
    ErrorCodeResult wordGapResult = checkWordGapLarge(context, currentIndex, patKey, source, resultBuilder);
    if (wordGapResult.getResult() == DetectionResult.SUCCESS) {
      return wordGapResult;
    }

    // 其他情况归类为多少码
    return handleMoreOrLackWord(context, currentIndex, patKey, resultBuilder);
  }

  /**
   * 检查字间隔过大
   * 当前组长度和下一组长度相加是否等于4，如果等于4，与当前报底相同，是字间隔过大
   */
  private ErrorCodeResult checkWordGapLarge(ComparisonContext context,
      int currentIndex,
      String patKey,
      String source,
      MessageResultBuilder resultBuilder) {

    List<String> resolverPatKeys = context.getResolverVO().getResolverMessage();

    if (currentIndex + 1 >= resolverPatKeys.size()) {
      return new ErrorCodeResult(DetectionType.MORE_OR_LACK_WORD, DetectionResult.FAILED, "无下一组");
    }

    String nextPatKey = resolverPatKeys.get(currentIndex + 1);

    // 检查长度和是否为标准长度，且合并后是否匹配标准报文
    if (patKey.length() + nextPatKey.length() == STANDARD_GROUP_LENGTH &&
        Objects.equals(source, patKey + nextPatKey)) {

      handleWordGapLarge(context, currentIndex, patKey, nextPatKey, resultBuilder);
      return new ErrorCodeResult(DetectionType.WORD_GAP_LARGE, DetectionResult.SUCCESS,
          "字间隔过大", 1); // 跳过下一组
    }

    return new ErrorCodeResult(DetectionType.MORE_OR_LACK_WORD, DetectionResult.FAILED, "非字间隔过大");
  }

  /**
   * 处理字间隔过小
   */
  private void handleWordGapSmall(ComparisonContext context) {
    PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
    PostTelegramTrainRule rule = context.getRule();

    // 根据评分规则扣分
    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getLarge().getL());

    log.debug("检测到字间隔过小，扣分: {}", rule.getLarge().getL());
  }

  /**
   * 处理字间隔过大
   */
  private void handleWordGapLarge(ComparisonContext context,
      int currentIndex,
      String patKey1,
      String patKey2,
      MessageResultBuilder resultBuilder) {

    PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
    PostTelegramTrainRule rule = context.getRule();
    List<String> resolverCorrectPatLogs = context.getResolverVO().getResolverPatLogs();
    List<String> resolverCorrectMoresValue = context.getResolverVO().getResolverMoresValue();
    List<String> resolverCorrectMoresTime = context.getResolverVO().getResolverMoresTime();

    // 根据评分规则扣分
    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getLarge().getR());

    // 合并两组数据
    String patLog1 = resolverCorrectPatLogs.get(currentIndex);
    String patLog2 = resolverCorrectPatLogs.get(currentIndex + 1);
    String moresValue1 = resolverCorrectMoresValue.get(currentIndex);
    String moresValue2 = resolverCorrectMoresValue.get(currentIndex + 1);
    String moresTime1 = resolverCorrectMoresTime.get(currentIndex);
    String moresTime2 = resolverCorrectMoresTime.get(currentIndex + 1);

    resultBuilder.addMergedMessage(patKey1, patLog1, moresValue1, moresTime1,
        patKey2, patLog2, moresValue2, moresTime2);

    log.debug("检测到字间隔过大，合并报文: {} + {}, 扣分: {}",
        patKey1, patKey2, rule.getLarge().getR());
  }

  /**
   * 处理多少码
   */
  private ErrorCodeResult handleMoreOrLackWord(ComparisonContext context,
      int currentIndex,
      String patKey,
      MessageResultBuilder resultBuilder) {

    PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
    List<String> resolverCorrectMoresValue = context.getResolverVO().getResolverMoresValue();
    List<String> resolverCorrectMoresTime = context.getResolverVO().getResolverMoresTime();
    List<String> resolverCorrectPatLogs = context.getResolverVO().getResolverPatLogs();

    // 更新多少码计数
    scoreVO.setMoreOrLackWord(scoreVO.getMoreOrLackWord() + 1);

    // 添加结果
    addResultToBuilder(resultBuilder, patKey,
        resolverCorrectMoresValue.get(currentIndex),
        resolverCorrectMoresTime.get(currentIndex),
        resolverCorrectPatLogs.get(currentIndex));

    log.debug("检测到多少码，报文长度: {}, 标准长度: {}", patKey.length(), STANDARD_GROUP_LENGTH);

    return new ErrorCodeResult(DetectionType.MORE_OR_LACK_WORD, DetectionResult.SUCCESS,
        String.format("多少码，长度: %d", patKey.length()));
  }

  /**
   * 添加结果到构建器
   */
  private void addResultToBuilder(MessageResultBuilder resultBuilder,
      String patKey,
      String moresValue,
      String moresTime,
      String patLog) {
    resultBuilder.addCorrectMessage(patKey, moresValue, moresTime, patLog);
  }

  /**
   * 检查是否应该跳过错码检测
   */
  public boolean shouldSkipErrorCodeDetection(ComparisonContext context, int currentIndex) {
    // 检查索引是否有效
    if (currentIndex >= context.getResolverVO().getResolverMessage().size()) {
      return true;
    }

    // 检查必要的数据是否存在
    List<String> resolverCorrectMoresValue = context.getResolverVO().getResolverMoresValue();
    List<String> resolverCorrectMoresTime = context.getResolverVO().getResolverMoresTime();
    List<String> resolverCorrectPatLogs = context.getResolverVO().getResolverPatLogs();

    return currentIndex >= resolverCorrectMoresValue.size() ||
        currentIndex >= resolverCorrectMoresTime.size() ||
        currentIndex >= resolverCorrectPatLogs.size();
  }

  /**
   * 获取错码类型的描述
   */
  public String getErrorCodeDescription(String patKey, String source) {
    if (patKey == null || source == null) {
      return "空报文";
    }

    if (patKey.length() != STANDARD_GROUP_LENGTH) {
      return String.format("长度异常(实际:%d, 标准:%d)", patKey.length(), STANDARD_GROUP_LENGTH);
    }

    if (patKey.contains(WORD_GAP_MARKER)) {
      return "包含字间隔标识符";
    }

    return "内容不匹配";
  }
}