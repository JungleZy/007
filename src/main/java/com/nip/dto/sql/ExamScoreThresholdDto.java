package com.nip.dto.sql;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * 单场考试成绩与其试卷分档阈值投影：用于按各场试卷 passMark/total 动态分档。
 *
 * @param score    考生得分
 * @param passMark 该场试卷及格分
 * @param total    该场试卷总分
 */
@RegisterForReflection
public record ExamScoreThresholdDto(Integer score, Integer passMark, Integer total) {
}
