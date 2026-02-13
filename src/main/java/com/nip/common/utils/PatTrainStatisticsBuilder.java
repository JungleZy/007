package com.nip.common.utils;

import com.nip.dto.general.statistic.GeneralPatTrainScoreInfoVO;
import com.nip.dto.general.statistic.GeneralPatTrainSchoolReportVO;
import com.nip.dto.general.statistic.GeneralPatTrainUserTendencyVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 训练统计结果构建器
 * 用于统一构建统计结果，减少重复代码
 * @param <U> 用户实体类型
 * @param <E> 错误统计类型
 */
public class PatTrainStatisticsBuilder<U, E> {

    private final List<U> trainUserEntities;
    private final GeneralPatTrainSchoolReportVO schoolReport = new GeneralPatTrainSchoolReportVO();
    private final List<GeneralPatTrainUserTendencyVO> userTendencies = new ArrayList<>();
    private E errorCollect;
    private int goodCount = 0;
    private int niceCount = 0;
    private int belowStandardCount = 0;

    // 函数式接口，用于从用户实体中提取数据
    private Function<U, BigDecimal> scoreExtractor;
    private Function<U, String> userIdExtractor;
    private Function<U, String> deductInfoExtractor;
    private Function<U, LocalDateTime> createTimeExtractor;
    private Function<U, Integer> statusExtractor;

    private PatTrainStatisticsBuilder(List<U> trainUserEntities) {
        this.trainUserEntities = trainUserEntities;
    }

    public static <U, E> PatTrainStatisticsBuilder<U, E> create(List<U> trainUserEntities) {
        return new PatTrainStatisticsBuilder<>(trainUserEntities);
    }

    public PatTrainStatisticsBuilder<U, E> withScoreExtractor(Function<U, BigDecimal> extractor) {
        this.scoreExtractor = extractor;
        return this;
    }

    public PatTrainStatisticsBuilder<U, E> withUserIdExtractor(Function<U, String> extractor) {
        this.userIdExtractor = extractor;
        return this;
    }

    public PatTrainStatisticsBuilder<U, E> withDeductInfoExtractor(Function<U, String> extractor) {
        this.deductInfoExtractor = extractor;
        return this;
    }

    public PatTrainStatisticsBuilder<U, E> withCreateTimeExtractor(Function<U, LocalDateTime> extractor) {
        this.createTimeExtractor = extractor;
        return this;
    }

    public PatTrainStatisticsBuilder<U, E> withStatusExtractor(Function<U, Integer> extractor) {
        this.statusExtractor = extractor;
        return this;
    }

    /**
     * 计算分数分布
     */
    public PatTrainStatisticsBuilder<U, E> calculateScoreDistribution() {
        for (U user : trainUserEntities) {
            BigDecimal score = scoreExtractor.apply(user);
            if (score.compareTo(PatTrainStatisticsUtil.SCORE_EXCELLENT) >= 0) {
                goodCount++;
            } else if (score.compareTo(PatTrainStatisticsUtil.SCORE_GOOD) >= 0) {
                niceCount++;
            } else {
                belowStandardCount++;
            }
        }

        int total = trainUserEntities.size();
        schoolReport.setGood(createScoreInfo(goodCount, total));
        schoolReport.setNice(createScoreInfo(niceCount, total));
        schoolReport.setBelowStandard(createScoreInfo(belowStandardCount, total));

        return this;
    }

    /**
     * 构建用户成绩趋势
     * @param userInfoProvider 用户信息提供者
     * @param lastScoresProvider 历史成绩提供者
     */
    public PatTrainStatisticsBuilder<U, E> buildUserTendencies(
            UserInfoProvider userInfoProvider,
            LastScoresProvider lastScoresProvider) {
        for (U user : trainUserEntities) {
            String userId = userIdExtractor.apply(user);
            BigDecimal thisScore = scoreExtractor.apply(user);
            LocalDateTime createTime = createTimeExtractor != null ? createTimeExtractor.apply(user) : null;

            UserInfo userInfo = userInfoProvider.getUserInfo(userId);
            if (userInfo == null) {
                continue;
            }

            List<BigDecimal> lastScores = lastScoresProvider.getLastScores(userId, createTime);

            GeneralPatTrainUserTendencyVO tendency = new GeneralPatTrainUserTendencyVO();
            tendency.setUserId(userId);
            tendency.setUserName(userInfo.userName());
            tendency.setUserImg(userInfo.userImg());
            tendency.setThisScore(thisScore);
            tendency.setLastScore(ArraySafeGetUtils.get(lastScores, 0, BigDecimal.ZERO));
            tendency.setLastLastScore(ArraySafeGetUtils.get(lastScores, 1, BigDecimal.ZERO));

            if (statusExtractor != null) {
                tendency.setStatus(statusExtractor.apply(user));
            }

            userTendencies.add(tendency);
        }
        return this;
    }

    /**
     * 设置错误统计
     */
    public PatTrainStatisticsBuilder<U, E> withErrorCollect(E errorCollect) {
        this.errorCollect = errorCollect;
        return this;
    }

    /**
     * 获取成绩分布报告
     */
    public GeneralPatTrainSchoolReportVO getSchoolReport() {
        return schoolReport;
    }

    /**
     * 获取用户成绩趋势列表
     */
    public List<GeneralPatTrainUserTendencyVO> getUserTendencies() {
        return userTendencies;
    }

    /**
     * 获取错误统计
     */
    public E getErrorCollect() {
        return errorCollect;
    }

    /**
     * 获取各等级人数
     */
    public int getGoodCount() {
        return goodCount;
    }

    public int getNiceCount() {
        return niceCount;
    }

    public int getBelowStandardCount() {
        return belowStandardCount;
    }

    private GeneralPatTrainScoreInfoVO createScoreInfo(int count, int total) {
        GeneralPatTrainScoreInfoVO info = new GeneralPatTrainScoreInfoVO();
        info.setPeopleNumber(count);
        info.setRate(PatTrainStatisticsUtil.calculateRate(count, total));
        return info;
    }

    /**
     * 用户信息提供者接口
     */
    @FunctionalInterface
    public interface UserInfoProvider {
        UserInfo getUserInfo(String userId);
    }

    /**
     * 历史成绩提供者接口
     */
    @FunctionalInterface
    public interface LastScoresProvider {
        List<BigDecimal> getLastScores(String userId, LocalDateTime afterTime);
    }

    /**
     * 用户信息记录
     */
    public record UserInfo(String userId, String userName, String userImg) {}
}
