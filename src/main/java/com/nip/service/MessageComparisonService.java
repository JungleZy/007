package com.nip.service;

import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainResolverVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.service.builder.MessageResultBuilder;
import com.nip.service.context.ComparisonContext;
import com.nip.service.detector.BunchDetector;
import com.nip.service.detector.ErrorCodeDetector;
import com.nip.service.detector.GroupDetector;
import com.nip.service.detector.LineDetector;
import com.nip.service.enums.DetectionResult;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.nip.common.utils.TickerPatUtils.checkDotLineGap;
import static com.nip.common.utils.TickerPatUtils.resolverMessage;
import static com.nip.service.constants.MessageComparisonConstants.*;

/**
 * 报文对比服务
 * 用于处理电报训练和通用训练中的报文对比逻辑
 * 重构后使用检测器模式，将复杂逻辑分离到独立的检测器类中
 *
 * @author system
 * @date 2024-12-19
 */
@Slf4j
@ApplicationScoped
public class MessageComparisonService {

  // 检测器实例
  private final LineDetector lineDetector = new LineDetector();
  private final GroupDetector groupDetector = new GroupDetector();
  private final BunchDetector bunchDetector = new BunchDetector();
  private final ErrorCodeDetector errorCodeDetector = new ErrorCodeDetector();

  /**
   * 对比拍发报文
   *
   * @param sources      源报文
   * @param patKeys      拍发报文
   * @param scoreVO      扣分项
   * @param userContents 用户每页提交内容
   * @param standards    点划标准
   * @param rule         评分规则
   * @param statisticsVO 统计信息
   * @return 对比结果
   */
  public PostTelegramTrainResolverVO comparison(List<String> sources,
      List<String> patKeys,
      PostTelegramTrainScoreVO scoreVO,
      List<PostTelegramTrainContentAddParam> userContents,
      List<PostTelegramTrainFinishInfoDto> standards,
      PostTelegramTrainRule rule,
      PostTelegramTrainStatisticsVO statisticsVO) {

    // 边界检查和空值处理
    if (sources == null) {
      sources = new ArrayList<>();
    }
    if (patKeys == null) {
      patKeys = new ArrayList<>();
    }
    if (userContents == null) {
      userContents = new ArrayList<>();
    }
    if (standards == null) {
      standards = new ArrayList<>();
    }
    if (scoreVO == null) {
      scoreVO = new PostTelegramTrainScoreVO();
    }
    if (statisticsVO == null) {
      statisticsVO = new PostTelegramTrainStatisticsVO();
    }
    if (rule == null) {
      rule = new PostTelegramTrainRule();
    }

    // 将用户的原始报文进行解析
    PostTelegramTrainResolverVO resolverVO = resolverMessage(patKeys, scoreVO, rule, userContents);

    // 创建对比上下文，封装所有状态数据
    ComparisonContext context = new ComparisonContext(
        sources, patKeys, userContents, standards, rule,
        resolverVO, scoreVO, statisticsVO);

    // 创建结果构建器，统一处理结果添加
    MessageResultBuilder resultBuilder = new MessageResultBuilder();

    // 获取解析后的报文数据
    List<String> resolverPatKeys = resolverVO.getResolverMessage();
    if (resolverPatKeys == null) {
      resolverPatKeys = new ArrayList<>();
    }

    log.info("开始报文对比，源报文数量: {}, 解析报文数量: {}", sources.size(), resolverPatKeys.size());

    // 主要对比循环，使用检测器进行各种检测
    for (int i = 0; i < resolverPatKeys.size(); i++) {
      String patKey = resolverPatKeys.get(i);
      if (patKey == null) {
        patKey = "";
      }

      String source = getSourceMessage(context);

      // 获取当前组的附加信息 - 添加边界检查
      String patLog = "";
      String moresTime = "";
      String moresValue = "";

      if (resolverVO.getResolverPatLogs() != null && i < resolverVO.getResolverPatLogs().size()) {
        patLog = resolverVO.getResolverPatLogs().get(i);
      }
      if (resolverVO.getResolverMoresTime() != null && i < resolverVO.getResolverMoresTime().size()) {
        moresTime = resolverVO.getResolverMoresTime().get(i);
      }
      if (resolverVO.getResolverMoresValue() != null && i < resolverVO.getResolverMoresValue().size()) {
        moresValue = resolverVO.getResolverMoresValue().get(i);
      }

      // 检查报文是否匹配
      if (Objects.equals(patKey, source)) {
        // 报文匹配，处理正确情况
        handleCorrectMessage(context, i, patKey, patLog, moresTime, moresValue, resultBuilder);
      } else {
        // 报文不匹配，使用检测器进行各种检测
        i = handleMismatchedMessage(context, i, patKey, source, patLog, moresTime, moresValue, resultBuilder);
      }

      context.incrementSourceIndex();
    }

    // 设置最终结果
    resolverVO.setResolverMessage(resultBuilder.getResolverMessage());
    resolverVO.setResolverPatLogs(resultBuilder.getResolverPatLogs());
    resolverVO.setResolverMoresValue(resultBuilder.getResolverMoresValue());
    resolverVO.setResolverMoresTime(resultBuilder.getResolverMoresTime());
    resolverVO.setMoreGroups(resultBuilder.getMoreGroups());

    // 统计本页拍发的数量
    scoreVO.setPatTotalNum(scoreVO.getPatTotalNum() + resolverPatKeys.size());

    // 计算本页的少行
    calculateMissingLines(sources, resolverPatKeys, scoreVO);

    log.info("报文对比完成，正确: {}, 错误: {}", scoreVO.getCorrect(), scoreVO.getErrorNumber());
    return resolverVO;
  }

  /**
   * 获取当前源报文，改进异常处理
   */
  private String getSourceMessage(ComparisonContext context) {
    List<String> sources = context.getSources();
    int sourceIndex = context.getSourceIndex();

    try {
      if (sourceIndex > MAX_SIGN_VALUE || sourceIndex >= sources.size()) {
        return sources.isEmpty() ? "" : sources.get(sources.size() - 1);
      }
      return sources.get(sourceIndex);
    } catch (IndexOutOfBoundsException e) {
      log.warn("数组下标异常，sourceIndex: {}, sources.size: {}", sourceIndex, sources.size());
      return sources.isEmpty() ? "" : sources.get(sources.size() - 1);
    }
  }

  /**
   * 处理正确匹配的报文
   */
  private void handleCorrectMessage(ComparisonContext context, int currentIndex, String patKey,
      String patLog, String moresTime, String moresValue,
      MessageResultBuilder resultBuilder) {
    // 记录正确报文数量
    context.getScoreVO().setCorrect(context.getScoreVO().getCorrect() + 1);

    // 计算拍发是否有点粗、点虚等
    checkDotLineGap(patKey, currentIndex, patLog, context.getStandards(),
        context.getRule(), true, context.getStatisticsVO(), context.getScoreVO());

    // 添加到结果中
    resultBuilder.addCorrectMessage(patKey, moresValue, moresTime, patLog);
  }

  /**
   * 处理不匹配的报文，使用各种检测器
   */
  private int handleMismatchedMessage(ComparisonContext context, int currentIndex, String patKey, String source,
      String patLog, String moresTime, String moresValue,
      MessageResultBuilder resultBuilder) {

    int skipCount = 0;

    // 1. 检测多行少行
    DetectionResult lineResult = lineDetector.detectMoreOrLessLine(context, currentIndex, patKey, resultBuilder);
    if (lineResult == DetectionResult.SUCCESS) {
      // 多行少行检测成功，直接返回当前索引
      checkDotLineGap(patKey, currentIndex, patLog, context.getStandards(),
          context.getRule(), false, context.getStatisticsVO(), context.getScoreVO());
      context.getScoreVO().setErrorNumber(context.getScoreVO().getErrorNumber() + 1);
      return currentIndex;
    }

    // 2. 检测多组少组
    DetectionResult groupResult = groupDetector.detectMoreOrLessGroup(context, currentIndex, patKey, source,
        resultBuilder);
    if (groupResult == DetectionResult.SUCCESS) {
      checkDotLineGap(patKey, currentIndex, patLog, context.getStandards(),
          context.getRule(), false, context.getStatisticsVO(), context.getScoreVO());
      context.getScoreVO().setErrorNumber(context.getScoreVO().getErrorNumber() + 1);
      return currentIndex;
    }

    // 3. 检测串组
    DetectionResult bunchResult = bunchDetector.detectBunch(context, patKey);
    if (bunchResult == DetectionResult.SUCCESS) {
      // 串组检测成功，添加结果并返回
      resultBuilder.addCorrectMessage(patKey, moresValue, moresTime, patLog);
      checkDotLineGap(patKey, currentIndex, patLog, context.getStandards(),
          context.getRule(), false, context.getStatisticsVO(), context.getScoreVO());
      context.getScoreVO().setErrorNumber(context.getScoreVO().getErrorNumber() + 1);
      return currentIndex;
    }

    // 4. 检测错码
    ErrorCodeDetector.ErrorCodeResult errorResult = errorCodeDetector.detectErrorCode(
        context, currentIndex, patKey, source, resultBuilder);

    if (errorResult.getResult() == DetectionResult.SUCCESS) {
      skipCount = errorResult.getSkipCount();
    }

    // 计算拍发是否有点粗、点虚等
    checkDotLineGap(patKey, currentIndex, patLog, context.getStandards(),
        context.getRule(), false, context.getStatisticsVO(), context.getScoreVO());

    context.getScoreVO().setErrorNumber(context.getScoreVO().getErrorNumber() + 1);

    return currentIndex + skipCount;
  }

  /**
   * 计算缺少的行数
   */
  private void calculateMissingLines(List<String> sources, List<String> resolverPatKeys,
      PostTelegramTrainScoreVO scoreVO) {
    if (sources == null || resolverPatKeys == null || scoreVO == null) {
      return;
    }

    int sourcesLineNum = sources.size() / GROUPS_PER_LINE;
    if (sources.size() % GROUPS_PER_LINE > 0) {
      sourcesLineNum += 1;
    }

    int patKeyLineNum = resolverPatKeys.size() / GROUPS_PER_LINE;
    if (resolverPatKeys.size() % GROUPS_PER_LINE > 0) {
      patKeyLineNum += 1;
    }

    if (sourcesLineNum > patKeyLineNum) {
      int diff = sourcesLineNum - patKeyLineNum;
      scoreVO.setMoreOrLackLine(scoreVO.getMoreOrLackLine() + diff);
    }
  }
}