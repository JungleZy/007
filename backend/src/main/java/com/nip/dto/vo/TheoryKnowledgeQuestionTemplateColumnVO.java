package com.nip.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 题库导入模板的单列规格。
 *
 * <p>本仓的导入导出分工是「后端只提供数据/规格，由前端生成文件」——见
 * {@code MilitaryTermDataController.saveBatch} 的「代替之前文件导入」与
 * {@code TheoryKnowledgeQuestionController.exportQuestionByLevelId} 的
 * 「后端只提供数据由前端生成文件导出」。前端据本规格生成 .xlsx 模板，
 * 用户填好后由前端解析成 JSON 行提交给 {@code /theoryKnowledgeQuestion/saveBatch}。
 *
 * <p>列规格与 {@link com.nip.dto.TheoryKnowledgeQuestionDto} 必须同源：模板列变了
 * 而导入口径没跟着变，用户按模板填的表就导不进来。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "题库导入模板列规格")
public class TheoryKnowledgeQuestionTemplateColumnVO {

  @Schema(title = "对应 TheoryKnowledgeQuestionDto 的字段名")
  private String field;

  @Schema(title = "模板表头的中文标题")
  private String title;

  @Schema(title = "是否必填")
  private Boolean required;

  @Schema(title = "示例值")
  private String example;

  @Schema(title = "填写说明")
  private String remark;
}
