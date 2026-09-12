package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * 完成自测的请求体。
 *
 * <p>刻意<b>不含</b> {@code score}：自测总分一律由服务端按 {@code t_theory_knowledge_exam_test_paper}
 * 的试卷快照重算。历史实现直接采信请求体里的 {@code score}，任何人都能给自己打满分；
 * 把字段从请求面删掉是唯一能保证「客户端分数通道不存在」的做法（只在响应 VO
 * {@code TheoryKnowledgeExamUserSelfVO} 里保留 score 作为出参）。
 */
@Data
@RegisterForReflection
public class TheoryKnowledgeExamSelfFinishDto {
  /**
   * 自测考试 id
   */
  private String examId;
  /**
   * 作答内容（JSON 文本，形如 {"singleChoice":[{"id":..,"answer":..}], ...}）
   */
  private String content;
}
