package com.nip.service;

import com.nip.common.utils.JSONUtils;
import com.nip.dto.UpdateInfoDto;
import com.nip.dto.UpdatePackageDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 组网升级的服务端一侧：报告本机产品版本，并把桌面外壳留存的升级包分发给客户端。
 *
 * <p>目录布局由外壳维护：{@code <目录>/<target>/<style>/info.json} 与同目录下的包文件。
 * 本类只读，不解析包内容；包的可信性由客户端验签保证，这里的 sha256 仅用于下载完整性校验。
 *
 * <p>target 与风格只接受固定枚举，文件名必须与枚举值拼出的固定格式一致，
 * 因此请求参数不可能拼出目录之外的路径。
 */
@ApplicationScoped
public class UpdatePackageService {
  public static final Set<String> TARGETS = Set.of("windows-x64", "windows-ia32", "linux-x64", "linux-arm64",
      "linux-loong64", "linux-mips64el", "linux-sw64", "linux-riscv64");
  public static final Set<String> STYLES = Set.of("HJ", "HJJ", "LJ");
  private static final String VERSION_PATTERN = "\\d+\\.\\d+\\.\\d+";

  private final String version;
  private final Optional<Path> directory;

  @Inject
  public UpdatePackageService(@ConfigProperty(name = "quarkus.application.version") String version,
                              @ConfigProperty(name = "nip.update.directory") Optional<String> directory) {
    this.version = version;
    this.directory = directory.filter(value -> !value.isBlank()).map(Path::of);
  }

  public record StoredPackage(UpdatePackageDto info, Path file) {
  }

  public static boolean supported(String target, String style) {
    return target != null && style != null && TARGETS.contains(target) && STYLES.contains(style);
  }

  public UpdateInfoDto info() {
    List<UpdatePackageDto> packages = new ArrayList<>();
    for (String target : TARGETS) {
      for (String style : STYLES) {
        find(target, style).ifPresent(stored -> packages.add(stored.info()));
      }
    }
    packages.sort((left, right) -> (left.getTarget() + left.getStyle()).compareTo(right.getTarget() + right.getStyle()));
    return new UpdateInfoDto(version, packages);
  }

  /**
   * 调用方须先用 {@link #supported} 校验参数。info.json 缺失、损坏或与实际文件不符时视为没有包。
   */
  public Optional<StoredPackage> find(String target, String style) {
    if (directory.isEmpty() || !supported(target, style)) {
      return Optional.empty();
    }
    Path folder = directory.get().resolve(target).resolve(style);
    try {
      UpdatePackageDto info = JSONUtils.fromJson(Files.readString(folder.resolve("info.json"), StandardCharsets.UTF_8), UpdatePackageDto.class);
      if (info == null || info.getVersion() == null || !info.getVersion().matches(VERSION_PATTERN)
          || info.getSha256() == null || !info.getSha256().matches("[0-9a-f]{64}")
          || !("nip-update-" + info.getVersion() + "-" + target + "-" + style + ".nipupd").equals(info.getFileName())) {
        return Optional.empty();
      }
      Path file = folder.resolve(info.getFileName());
      if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != info.getSize()) {
        return Optional.empty();
      }
      info.setTarget(target);
      info.setStyle(style);
      return Optional.of(new StoredPackage(info, file));
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }
}
