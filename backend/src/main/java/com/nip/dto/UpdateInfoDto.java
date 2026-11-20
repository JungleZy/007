package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 本机产品版本与可分发的升级包。
 */
@Data
@AllArgsConstructor
@RegisterForReflection
public class UpdateInfoDto {
  private String version;
  private List<UpdatePackageDto> packages;
}
