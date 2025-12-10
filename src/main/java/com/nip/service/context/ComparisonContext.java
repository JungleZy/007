package com.nip.service.context;

import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainResolverVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.service.enums.ProcessingState;
import lombok.Data;

import java.util.List;

/**
 * 报文对比上下文类
 * 封装对比过程中的状态数据和配置信息
 * 
 * @author system
 * @date 2024-12-19
 */
@Data
public class ComparisonContext {

  // ==================== 输入数据 ====================

  /** 源报文列表 */
  private final List<String> sources;

  /** 用户拍发报文列表 */
  private final List<String> patKeys;

  /** 用户每页提交内容 */
  private final List<PostTelegramTrainContentAddParam> userContents;

  /** 点划标准 */
  private final List<PostTelegramTrainFinishInfoDto> standards;

  /** 评分规则 */
  private final PostTelegramTrainRule rule;

  // ==================== 状态数据 ====================

  /** 解析后的报文对象 */
  private final PostTelegramTrainResolverVO resolverVO;

  /** 评分对象 */
  private final PostTelegramTrainScoreVO scoreVO;

  /** 统计信息对象 */
  private final PostTelegramTrainStatisticsVO statisticsVO;

  // ==================== 运行时状态 ====================

  /** 当前源报文索引 */
  private int sourceIndex;

  /** 当前处理状态 */
  private ProcessingState processingState;

  /** 是否处于多行少行模式 */
  private boolean isMoreOrLackLineMode;

  // ==================== 构造函数 ====================

  /**
   * 构造函数
   * 
   * @param sources      源报文列表
   * @param patKeys      用户拍发报文列表
   * @param userContents 用户每页提交内容
   * @param standards    点划标准
   * @param rule         评分规则
   * @param resolverVO   解析后的报文对象
   * @param scoreVO      评分对象
   * @param statisticsVO 统计信息对象
   */
  public ComparisonContext(List<String> sources,
      List<String> patKeys,
      List<PostTelegramTrainContentAddParam> userContents,
      List<PostTelegramTrainFinishInfoDto> standards,
      PostTelegramTrainRule rule,
      PostTelegramTrainResolverVO resolverVO,
      PostTelegramTrainScoreVO scoreVO,
      PostTelegramTrainStatisticsVO statisticsVO) {
    this.sources = sources;
    this.patKeys = patKeys;
    this.userContents = userContents;
    this.standards = standards;
    this.rule = rule;
    this.resolverVO = resolverVO;
    this.scoreVO = scoreVO;
    this.statisticsVO = statisticsVO;
    this.sourceIndex = 0;
    this.processingState = ProcessingState.NORMAL;
    this.isMoreOrLackLineMode = false;
  }

  // ==================== 便利方法 ====================

  /**
   * 增加源报文索引
   */
  public void incrementSourceIndex() {
    this.sourceIndex++;
  }

  /**
   * 按指定数量增加源报文索引
   * 
   * @param increment 增加的数量
   */
  public void incrementSourceIndex(int increment) {
    this.sourceIndex += increment;
  }

  /**
   * 减少源报文索引
   * 
   * @param decrement 减少的数量
   */
  public void decrementSourceIndex(int decrement) {
    this.sourceIndex -= decrement;
  }

  /**
   * 获取当前源报文
   * 
   * @return 当前源报文，如果索引超出范围则返回最后一个
   */
  public String getCurrentSource() {
    if (sources == null || sources.isEmpty()) {
      return "";
    }
    if (sourceIndex >= sources.size()) {
      return sources.get(sources.size() - 1);
    }
    if (sourceIndex < 0) {
      return sources.get(0);
    }
    return sources.get(sourceIndex);
  }

  /**
   * 设置多行少行模式
   * 
   * @param isMoreOrLackLineMode 是否为多行少行模式
   */
  public void setMoreOrLackLineMode(boolean isMoreOrLackLineMode) {
    this.isMoreOrLackLineMode = isMoreOrLackLineMode;
  }

  /**
   * 检查是否还有更多源报文
   * 
   * @return 如果还有更多源报文返回true
   */
  public boolean hasMoreSources() {
    return sources != null && sourceIndex < sources.size() - 1;
  }

  /**
   * 检查指定索引是否在源报文范围内
   * 
   * @param index 要检查的索引
   * @return 如果索引有效返回true
   */
  public boolean isValidSourceIndex(int index) {
    return sources != null && index >= 0 && index < sources.size();
  }
}