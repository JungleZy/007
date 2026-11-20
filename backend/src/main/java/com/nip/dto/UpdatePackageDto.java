package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * 留存的升级包。字段与桌面外壳写入的 info.json 一致。
 */
@Data
@RegisterForReflection
public class UpdatePackageDto {
  private String fileName;
  private String version;
  private String target;
  private String style;
  private long size;
  private String sha256;
}
