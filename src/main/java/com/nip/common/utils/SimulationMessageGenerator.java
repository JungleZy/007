package com.nip.common.utils;

import com.nip.entity.simulation.router.SimulationRouterRoomContentEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomPageEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 模拟训练消息生成工具类
 * 提取公共的消息体生成逻辑，减少代码重复
 */
public final class SimulationMessageGenerator {

    private SimulationMessageGenerator() {
    }

    // 报文类型常量
    public static final int TYPE_NUMBER = 0;
    public static final int TYPE_CHAR = 3;
    public static final int TYPE_MIXED = 4;

    // 字符边界常量
    private static final int CHAR_A = 65;
    private static final int CHAR_Z = 90;
    private static final int CHAR_0 = 48;
    private static final int CHAR_9 = 57;
    private static final int MIXED_MAX = 35;

    // 每页消息数量
    private static final int MESSAGES_PER_PAGE = 100;
    private static final int GROUPS_PER_MESSAGE = 4;
    private static final int AVG_THRESHOLD = 400;

    /**
     * 生成消息体
     * @param generateNumber 生成数量
     * @param pageNumber 起始页码
     * @param index 起始索引
     * @param train 训练配置
     * @param roomId 房间ID
     * @param saver 保存函数，用于将生成的实体列表保存到数据库
     * @return 生成的消息实体列表
     */
    public static List<SimulationRouterRoomPageEntity> generateMessageBody(
            Integer generateNumber,
            Integer pageNumber,
            int index,
            SimulationRouterRoomContentEntity train,
            Integer roomId,
            Function<List<SimulationRouterRoomPageEntity>, List<SimulationRouterRoomPageEntity>> saver) {

        List<SimulationRouterRoomPageEntity> ret = new ArrayList<>();
        int pageNum = pageNumber;
        Integer isAvg = train.getBdType();
        Integer isRandom = train.getIsRandom();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> avgB = new ArrayList<>();

        switch (train.getBwType()) {
            case TYPE_CHAR:
                generateCharMessages(generateNumber, pageNumber, index, isAvg, isRandom, random, avgB, ret, roomId);
                break;
            case TYPE_MIXED:
                generateMixedMessages(generateNumber, pageNumber, index, isAvg, isRandom, random, avgB, ret, roomId);
                break;
            default:
                generateNumberMessages(generateNumber, pageNumber, isAvg, isRandom, random, avgB, ret, roomId);
                break;
        }

        // 处理平均分配模式
        if (isAvg.compareTo(1) == 0) {
            processAverageDistribution(avgB, isRandom, pageNum, roomId, ret);
        }

        return saver.apply(ret);
    }

    /**
     * 生成字码报消息
     */
    private static void generateCharMessages(
            Integer generateNumber, Integer pageNumber, int index,
            Integer isAvg, Integer isRandom, ThreadLocalRandom random,
            List<String> avgB, List<SimulationRouterRoomPageEntity> ret, Integer roomId) {

        int charIndex = index;
        for (int i = 0; i < generateNumber; i++) {
            int currentPage = pageNumber + i / MESSAGES_PER_PAGE;
            StringBuilder body = new StringBuilder();

            if (isAvg.compareTo(1) == 0) {
                for (int j = 0; j < GROUPS_PER_MESSAGE; j++) {
                    char c = (char) charIndex;
                    charIndex = (charIndex == CHAR_Z) ? CHAR_A : charIndex + 1;
                    avgB.add(String.valueOf(c));
                }
            } else if (isRandom.compareTo(1) == 0) {
                for (int j = 0; j < GROUPS_PER_MESSAGE; j++) {
                    char a = (char) (random.nextInt(26) + CHAR_A);
                    body.append(a);
                }
            } else {
                for (int j = 0; j < GROUPS_PER_MESSAGE; j++) {
                    char c = (char) charIndex;
                    charIndex = (charIndex == CHAR_Z) ? CHAR_A : charIndex + 1;
                    body.append(c);
                }
            }

            if (isAvg.compareTo(1) != 0) {
                ret.add(createPageEntity(body.toString(), currentPage, i % MESSAGES_PER_PAGE, roomId));
            }
        }
    }

    /**
     * 生成混合报消息
     */
    private static void generateMixedMessages(
            Integer generateNumber, Integer pageNumber, int index,
            Integer isAvg, Integer isRandom, ThreadLocalRandom random,
            List<String> avgB, List<SimulationRouterRoomPageEntity> ret, Integer roomId) {

        int charAndNumberIndex = index;
        for (int i = 0; i < generateNumber; i++) {
            int currentPage = pageNumber + i / MESSAGES_PER_PAGE;
            StringBuilder body = new StringBuilder();

            if (isAvg.compareTo(1) == 0) {
                for (int j = 0; j < GROUPS_PER_MESSAGE; j++) {
                    char c = convertMixedIndexToChar(charAndNumberIndex);
                    charAndNumberIndex = (charAndNumberIndex == MIXED_MAX) ? 0 : charAndNumberIndex + 1;
                    avgB.add(String.valueOf(c));
                }
            } else if (isRandom.compareTo(1) == 0) {
                for (int j = 0; j < GROUPS_PER_MESSAGE; j++) {
                    int i1 = random.nextInt(36);
                    char c = (char) (i1 < 10 ? i1 + CHAR_0 : i1 + 55);
                    body.append(c);
                }
            } else {
                for (int j = 0; j < GROUPS_PER_MESSAGE; j++) {
                    char c = convertMixedIndexToChar(charAndNumberIndex);
                    charAndNumberIndex = (charAndNumberIndex == MIXED_MAX) ? 0 : charAndNumberIndex + 1;
                    body.append(c);
                }
            }

            if (isAvg.compareTo(1) != 0) {
                ret.add(createPageEntity(body.toString(), currentPage, i % MESSAGES_PER_PAGE, roomId));
            }
        }
    }

    /**
     * 生成数字报消息
     */
    private static void generateNumberMessages(
            Integer generateNumber, Integer pageNumber,
            Integer isAvg, Integer isRandom, ThreadLocalRandom random,
            List<String> avgB, List<SimulationRouterRoomPageEntity> ret, Integer roomId) {

        int numberIndex = 0;
        for (int i = 0; i < generateNumber; i++) {
            int currentPage = pageNumber + i / MESSAGES_PER_PAGE;
            StringBuilder body = new StringBuilder();

            if (isAvg.compareTo(1) == 0) {
                for (int j = 0; j < GROUPS_PER_MESSAGE; j++) {
                    avgB.add(String.valueOf(numberIndex));
                    numberIndex = (numberIndex == 9) ? 0 : numberIndex + 1;
                }
            } else if (isRandom.compareTo(1) == 0) {
                for (int j = 0; j < GROUPS_PER_MESSAGE; j++) {
                    body.append(random.nextInt(10));
                }
            } else {
                for (int j = 0; j < GROUPS_PER_MESSAGE; j++) {
                    body.append(numberIndex);
                    numberIndex = (numberIndex == 9) ? 0 : numberIndex + 1;
                }
            }

            if (isAvg.compareTo(1) != 0) {
                ret.add(createPageEntity(body.toString(), currentPage, i % MESSAGES_PER_PAGE, roomId));
            }
        }
    }

    /**
     * 处理平均分配模式
     */
    private static void processAverageDistribution(
            List<String> avgB, Integer isRandom, int pageNum,
            Integer roomId, List<SimulationRouterRoomPageEntity> ret) {

        if (isRandom.compareTo(1) == 0) {
            Collections.shuffle(avgB);
        }

        int sort = 0;
        StringBuilder body = new StringBuilder();
        int currentPage = pageNum;

        for (int i = 0; i < avgB.size(); i++) {
            body.append(avgB.get(i));
            if (i != 0 && i % AVG_THRESHOLD == 0) {
                currentPage++;
            }
            if (body.length() == GROUPS_PER_MESSAGE) {
                ret.add(createPageEntity(body.toString(), currentPage, sort % MESSAGES_PER_PAGE, roomId));
                sort++;
                body = new StringBuilder();
            }
        }
    }

    /**
     * 混合索引转字符
     */
    private static char convertMixedIndexToChar(int index) {
        return (char) (index < 10 ? index + CHAR_0 : index + 55);
    }

    /**
     * 创建页面实体
     */
    private static SimulationRouterRoomPageEntity createPageEntity(String key, int pageNumber, int sort, Integer roomId) {
        SimulationRouterRoomPageEntity entity = new SimulationRouterRoomPageEntity();
        entity.setKey(key);
        entity.setPageNumber(pageNumber);
        entity.setSort(sort);
        entity.setRoomId(roomId);
        return entity;
    }
}
