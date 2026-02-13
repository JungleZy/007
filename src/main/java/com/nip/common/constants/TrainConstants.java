package com.nip.common.constants;

import java.math.BigDecimal;

/**
 * 训练相关常量
 */
public final class TrainConstants {

    private TrainConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // 分数阈值
    public static final BigDecimal SCORE_EXCELLENT_THRESHOLD = new BigDecimal("90");
    public static final BigDecimal SCORE_GOOD_THRESHOLD = new BigDecimal("70");

    // 消息生成限制
    public static final int MAX_GENERATE_MESSAGE_COUNT = 200;

    // 每页消息数量
    public static final int MESSAGES_PER_PAGE = 100;

    // 分数等级描述
    public static final String LEVEL_EXCELLENT = "优秀";
    public static final String LEVEL_GOOD = "良好";
    public static final String LEVEL_BELOW_STANDARD = "待提高";
}
