package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Data
public class CableVO {
  @Schema(name = "id",title = "编号", description = "为空时新增，不为空表示修改")
  private String id;
  @Schema(name = "title",title = "标题")
  private String title;
  @Schema(name = "typeId",title = "分类编号")
  private String typeId;
  @Schema(name = "scope",title = "所属域", description = "0:收报，1:发报，2:收发报")
  private Integer scope;
  @Schema(name = "codeType",title = "类型", description = " 0 数码报 1 字码报 2 混合报")
  private Integer codeType;
  @Schema(name = "codeSort",title = "长短码", description = "0 短码 1 长码")
  private Integer codeSort;
  @Schema(name = "remark",title = "备注信息")
  private String remark;
  @Schema(name = "floors",title = "报体", description = "按照页、组、码组成三维数组")
  private List<List<List<String>>> floors;
}
