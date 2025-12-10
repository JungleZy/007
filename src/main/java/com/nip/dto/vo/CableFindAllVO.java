package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Data
public class CableFindAllVO {
  @Schema(description = "报文类型编号",name = "typeId",title = "typeId")
  private String typeId;
  @Schema(description = "0:收报，1:发报，2:收发报",name = "scope",title = "scope")
  private List<Integer> scope;
}
