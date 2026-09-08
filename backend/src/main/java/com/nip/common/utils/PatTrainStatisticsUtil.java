package com.nip.common.utils;

import cn.hutool.core.text.CharSequenceUtil;
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
 * 训练统计工具类
 * 提取公共的统计逻辑，减少代码重复
 */
public final class PatTrainStatisticsUtil {

    private PatTrainStatisticsUtil() {
    }

    // 分数阈值常量
    public static final BigDecimal SCORE_EXCELLENT = new BigDecimal("90");
    public static final BigDecimal SCORE_GOOD = new BigDecimal("70");
    public static final BigDecimal PERCENTAGE_SCALE = new BigDecimal("100");

    /**
     * 计算分数分布统计
     * @param scores 分数列表
     * @return 成绩分布报告
     */
    public static GeneralPatTrainSchoolReportVO calculateScoreDistribution(List<BigDecimal> scores) {
        GeneralPatTrainSchoolReportVO reportVO = new GeneralPatTrainSchoolReportVO();
        int good = 0;
        int nice = 0;
        int belowStandard = 0;

        for (BigDecimal score : scores) {
            if (score.compareTo(SCORE_EXCELLENT) >= 0) {
                good++;
            } else if (score.compareTo(SCORE_GOOD) >= 0) {
                nice++;
            } else {
                belowStandard++;
            }
        }

        int totalNumber = scores.size();
        reportVO.setGood(createScoreInfo(good, totalNumber));
        reportVO.setNice(createScoreInfo(nice, totalNumber));
        reportVO.setBelowStandard(createScoreInfo(belowStandard, totalNumber));

        return reportVO;
    }

    /**
     * 创建分数信息对象
     */
    private static GeneralPatTrainScoreInfoVO createScoreInfo(int count, int total) {
        GeneralPatTrainScoreInfoVO info = new GeneralPatTrainScoreInfoVO();
        info.setPeopleNumber(count);
        info.setRate(calculateRate(count, total));
        return info;
    }

    /**
     * 计算百分比
     */
    public static BigDecimal calculateRate(int count, int total) {
        if (total == 0 || count == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(count)
                .divide(new BigDecimal(total), 10, RoundingMode.HALF_UP)
                .multiply(PERCENTAGE_SCALE)
                .setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * 构建用户成绩趋势数据
     * @param userId 用户ID
     * @param userName 用户名
     * @param userImg 用户头像
     * @param thisScore 本次成绩
     * @param lastScore 上次成绩
     * @param lastLastScore 上上次成绩
     * @return 用户成绩趋势对象
     */
    public static GeneralPatTrainUserTendencyVO buildUserTendency(
            String userId,
            String userName,
            String userImg,
            BigDecimal thisScore,
            BigDecimal lastScore,
            BigDecimal lastLastScore) {
        GeneralPatTrainUserTendencyVO tendency = new GeneralPatTrainUserTendencyVO();
        tendency.setUserId(userId);
        tendency.setUserName(userName);
        tendency.setUserImg(userImg);
        tendency.setThisScore(thisScore);
        tendency.setLastScore(lastScore);
        tendency.setLastLastScore(lastLastScore);
        return tendency;
    }

    /**
     * 合并错误统计信息
     * @param target 目标统计对象
     * @param source 源统计对象
     * @param getters 获取器数组
     * @param setters 设置器数组
     */
    public static void mergeErrorStatistics(
            Object target,
            Object source,
            Function<Object, Integer>[] getters,
            java.util.function.BiConsumer<Object, Integer>[] setters) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < getters.length; i++) {
            Integer sourceValue = getters[i].apply(source);
            Integer targetValue = getters[i].apply(target);
            setters[i].accept(target, targetValue + (sourceValue != null ? sourceValue : 0));
        }
    }

    /**
     * 安全获取JSON字符串对应的对象
     * @param jsonStr JSON字符串
     * @param clazz 目标类型
     * @return 解析后的对象，如果字符串为空则返回null
     */
    public static <T> T safeFromJson(String jsonStr, Class<T> clazz) {
        if (CharSequenceUtil.isBlank(jsonStr)) {
            return null;
        }
        return JSONUtils.fromJson(jsonStr, clazz);
    }
}
