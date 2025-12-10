package com.nip.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * MilitaryTermDataEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-06-23 14:46
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "军语密语VO")
public class MilitaryTermDataVO {

  @Schema(name = "id",title = "id")
  private String id;

  @Schema(name = "parentId",title = "父ID")
  private String parentId;

  @Schema(name = "key",title = "名称")
  private String key;

  @Schema(name = "value",title = "值")
  private String value;

  @Schema(name = "sort",title = "位置坐标")
  private Integer sort = 0;

  @Schema(name = "child",title = "密语")
  private List<MilitaryTermDataVO> child;
}
