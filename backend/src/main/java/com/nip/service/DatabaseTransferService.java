package com.nip.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.Config;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** MySQL 标准 SQL 文件交换。DDL 会隐式提交，不能用 JTA 声称整份导入可回滚。 */
@ApplicationScoped
public class DatabaseTransferService {
  public static final long MAX_FILE_BYTES = 512L * 1024 * 1024;
  private static final int TIMEOUT_SECONDS = 900;
  private final AtomicBoolean busy = new AtomicBoolean();
  private final Config config;

  @Inject
  public DatabaseTransferService(Config config) {
    this.config = config;
  }

  public Map<String, Object> status() {
    Target target = target();
    return Map.of("database", target.database(), "backupAvailable", available(executable(true)),
        "importAvailable", available(executable(false)), "busy", busy.get(),
        "maxFileBytes", MAX_FILE_BYTES);
  }

  public record Backup(Path path, String filename) {}

  public Backup backup() throws IOException {
    acquire();
    Path output = null;
    try {
      Target target = target();
      output = Files.createTempFile("nip-database-backup-", ".sql");
      run(command(target, true), null, output, false);
      if (Files.size(output) == 0) throw new IllegalStateException("备份文件为空，请检查数据库配置");
      String filename = target.database() + "-" + LocalDateTime.now()
          .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".sql";
      return new Backup(output, filename);
    } catch (IOException | RuntimeException e) {
      if (output != null) {
        try {
          Files.deleteIfExists(output);
        } catch (IOException cleanupFailure) {
          // Windows 杀毒扫描/子进程句柄暂未释放时交给定时清理，保留真正的失败原因。
          e.addSuppressed(cleanupFailure);
        }
      }
      throw e;
    } finally {
      busy.set(false);
    }
  }

  public void restore(FileUpload file, String confirmation) throws IOException {
    validateUpload(file == null ? null : file.fileName(), file == null ? 0 : file.size());
    Target target = target();
    if (!target.database().equals(confirmation)) {
      throw new IllegalArgumentException("请填写当前数据库名称以确认导入");
    }
    acquire();
    try {
      run(command(target, false), file.uploadedFile(), null, true);
    } finally {
      busy.set(false);
    }
  }

  static void validateUpload(String filename, long size) {
    if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".sql")) {
      throw new IllegalArgumentException("请选择 .sql 文件");
    }
    if (size <= 0 || size > MAX_FILE_BYTES) {
      throw new IllegalArgumentException("SQL 文件不能为空且不能超过 512 MiB");
    }
  }

  private void acquire() {
    if (!busy.compareAndSet(false, true)) {
      throw new IllegalStateException("已有数据库备份或导入任务正在执行，请等待完成");
    }
  }

  record Target(String host, int port, String database) {}

  static Target parseTarget(String jdbcUrl) {
    try {
      if (!jdbcUrl.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
      URI uri = URI.create(jdbcUrl.substring(5));
      String database = uri.getPath().substring(1);
      if (uri.getHost() == null || uri.getUserInfo() != null || !database.matches("[a-zA-Z0-9_]+")) {
        throw new IllegalArgumentException();
      }
      return new Target(uri.getHost(), uri.getPort() == -1 ? 3306 : uri.getPort(), database);
    } catch (RuntimeException e) {
      throw new IllegalStateException("数据库备份仅支持单主机 MySQL JDBC 地址及字母、数字、下划线库名");
    }
  }

  private Target target() {
    return parseTarget(config.getValue("quarkus.datasource.jdbc.url", String.class));
  }

  private String executable(boolean backup) {
    return config.getOptionalValue("nip.database." + (backup ? "mysqldump" : "mysql") + "-path", String.class)
        .filter(value -> !value.isBlank())
        .orElseGet(() -> bundledExecutable(backup ? "mysqldump" : "mysql",
            System.getProperty("os.name"), System.getProperty("os.arch"), Path.of(System.getProperty("user.dir"))));
  }

  static String bundledTarget(String osName, String architecture) {
    String os = osName.toLowerCase(Locale.ROOT);
    String arch = switch (architecture.toLowerCase(Locale.ROOT)) {
      case "x64", "amd64", "x86_64" -> "x64";
      case "arm64", "aarch64" -> "arm64";
      case "loong64", "loongarch64" -> "loong64";
      case "mips64el", "mips64le" -> "mips64el";
      case "sw64" -> "sw64";
      case "riscv64" -> "riscv64";
      default -> null;
    };
    if (arch == null) return null;
    if (os.startsWith("windows")) return arch.equals("x64") ? "windows-x64" : null;
    return os.equals("linux") ? "linux-" + arch : null;
  }

  static String bundledExecutable(String tool, String osName, String architecture, Path workingDirectory) {
    String target = bundledTarget(osName, architecture);
    if (target != null) {
      String relative = "bin/file/tools/mysql/" + target + "/" + tool + (target.startsWith("windows-") ? ".exe" : "");
      for (Path root : List.of(workingDirectory, workingDirectory.resolve("bw-frontend"),
          workingDirectory.resolve("../bw-frontend"))) {
        Path candidate = root.resolve(relative).toAbsolutePath().normalize();
        if (Files.isRegularFile(candidate)) return candidate.toString();
      }
    }
    return tool;
  }

  List<String> command(Target target, boolean backup) {
    List<String> args = new ArrayList<>(List.of(executable(backup), "--no-defaults", "--protocol=TCP",
        "--host=" + target.host(), "--port=" + target.port(),
        "--user=" + config.getValue("quarkus.datasource.username", String.class),
        "--default-character-set=utf8mb4",
        "--ssl-mode=" + config.getOptionalValue("nip.database.ssl-mode", String.class).orElse("REQUIRED")));
    if (backup) {
      args.addAll(List.of("--single-transaction", "--quick", "--hex-blob", "--no-tablespaces",
          "--set-gtid-purged=OFF", "--column-statistics=0", "--routines", "--events", "--triggers"));
    } else {
      // 批处理二进制模式禁用 source / system 等客户端命令；不使用 --force，首个错误即停止。
      args.addAll(List.of("--binary-mode=1", "--local-infile=0", "--connect-timeout=15"));
    }
    args.add("--database=" + target.database());
    // mysqldump 不接受 --database=（--databases 会额外产生 CREATE DATABASE/USE）。
    if (backup) args.set(args.size() - 1, target.database());
    return args;
  }

  private void run(List<String> command, Path input, Path output, boolean importing) throws IOException {
    ProcessBuilder builder = toolProcess(command);
    // 口令不进入命令行、SQL 文件或日志。子进程退出后环境随之销毁。
    builder.environment().put("MYSQL_PWD", config.getValue("quarkus.datasource.password", String.class));
    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
    builder.redirectOutput(output == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(output.toFile()));
    if (input != null) builder.redirectInput(input.toFile());
    Process process;
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new IllegalStateException("未能启动 MySQL 工具，请检查服务器工具路径与执行权限");
    }
    try {
      if (input == null) process.getOutputStream().close();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
      while (!process.waitFor(1, TimeUnit.SECONDS)) {
        if (System.nanoTime() >= deadline) {
          throw new IllegalStateException(importing
              ? "SQL 导入超时，可能已写入部分数据，请联系管理员检查后恢复备份"
              : "数据库备份超时，请在低峰期重试");
        }
        if (output != null && Files.size(output) > MAX_FILE_BYTES) {
          throw new IllegalStateException("备份超过 512 MiB，请使用服务器数据库工具备份");
        }
      }
      if (process.exitValue() != 0) {
        throw new IllegalStateException(importing
            ? "SQL 导入失败，可能已写入部分数据；请检查文件版本、语法和数据库权限后恢复备份"
            : "数据库备份失败，请检查 MySQL 工具版本、连接配置和数据库权限");
      }
      if (output != null && Files.size(output) > MAX_FILE_BYTES) {
        throw new IllegalStateException("备份超过 512 MiB，请使用服务器数据库工具备份");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(importing ? "导入被中断，可能已写入部分数据，请检查数据库" : "备份被中断");
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        try {
          process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  private boolean available(String executable) {
    Process process = null;
    try {
      process = toolProcess(List.of(executable, "--no-defaults", "--version"))
          .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }

  private ProcessBuilder toolProcess(List<String> command) {
    ProcessBuilder builder = new ProcessBuilder(command);
    // Linux 工具包可自带非 glibc 共享库；状态探测和实际执行必须使用同一加载环境。
    Path executablePath = Path.of(command.getFirst());
    if (System.getProperty("os.name").equalsIgnoreCase("Linux") && executablePath.isAbsolute()) {
      Path libraries = executablePath.getParent().resolve("lib");
      if (Files.isDirectory(libraries)) {
        String previous = builder.environment().get("LD_LIBRARY_PATH");
        builder.environment().put("LD_LIBRARY_PATH", libraries + (previous == null || previous.isBlank() ? "" : ":" + previous));
      }
    }
    return builder;
  }

  // 请求在响应体开始传输前断开或进程异常退出时，StreamingOutput 的 finally 可能未执行。
  @Scheduled(every = "1h", delayed = "1h")
  void cleanAbandonedBackups() {
    Instant cutoff = Instant.now().minusSeconds(86400);
    try (var files = Files.newDirectoryStream(Path.of(System.getProperty("java.io.tmpdir")),
        "nip-database-backup-*.sql")) {
      for (Path file : files) {
        try {
          if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) Files.deleteIfExists(file);
        } catch (IOException e) {
          org.jboss.logging.Logger.getLogger(DatabaseTransferService.class)
              .warn("未能清理过期数据库备份临时文件");
        }
      }
    } catch (IOException e) {
      org.jboss.logging.Logger.getLogger(DatabaseTransferService.class).warn("无法扫描数据库备份临时目录");
    }
  }
}
