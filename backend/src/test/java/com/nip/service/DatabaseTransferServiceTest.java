package com.nip.service;

import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTransferServiceTest {
  @Test
  void bundledToolsFollowProjectOrInstallationDirectory(@TempDir Path root) throws Exception {
    Path desktop = root.resolve("desktop installation");
    Path dump = desktop.resolve("bin/file/tools/mysql/windows-x64/mysqldump.exe");
    Files.createDirectories(dump.getParent());
    Files.createFile(dump);
    assertEquals(dump.toString(), DatabaseTransferService.bundledExecutable("mysqldump", "Windows 11", "amd64", desktop));
    assertEquals("mysqldump", DatabaseTransferService.bundledExecutable("mysqldump", "Linux", "aarch64", desktop));
    Path repository = root.resolve("repository");
    Path projectDump = repository.resolve("bw-frontend/bin/file/tools/mysql/windows-x64/mysqldump.exe");
    Files.createDirectories(projectDump.getParent());
    Files.createFile(projectDump);
    assertEquals(projectDump.toString(), DatabaseTransferService.bundledExecutable("mysqldump", "Windows 11", "amd64", repository));
    assertEquals(projectDump.toString(), DatabaseTransferService.bundledExecutable("mysqldump", "Windows 11", "amd64", repository.resolve("backend")));
    for (String arch : java.util.List.of("x64", "arm64", "loong64", "mips64el", "sw64", "riscv64")) {
      Path linuxDump = repository.resolve("bw-frontend/bin/file/tools/mysql/linux-" + arch + "/mysqldump");
      Files.createDirectories(linuxDump.getParent());
      Files.createFile(linuxDump);
      assertEquals(linuxDump.toString(), DatabaseTransferService.bundledExecutable("mysqldump", "Linux", arch, repository));
    }
  }

  @Test
  void architectureNamesDoNotSelectAnotherCpuOrEndian() {
    assertEquals("linux-x64", DatabaseTransferService.bundledTarget("Linux", "x86_64"));
    assertEquals("linux-arm64", DatabaseTransferService.bundledTarget("Linux", "aarch64"));
    assertEquals("linux-loong64", DatabaseTransferService.bundledTarget("Linux", "loongarch64"));
    assertEquals("linux-mips64el", DatabaseTransferService.bundledTarget("Linux", "mips64le"));
    assertNull(DatabaseTransferService.bundledTarget("Linux", "mips64"));
    assertNull(DatabaseTransferService.bundledTarget("Windows 11", "aarch64"));
    assertNull(DatabaseTransferService.bundledTarget("Linux", "unknown"));
  }

  private DatabaseTransferService service() {
    return new DatabaseTransferService(new SmallRyeConfigBuilder()
        .withDefaultValue("quarkus.datasource.jdbc.url", "jdbc:mysql://localhost:3307/project006?useSSL=true")
        .withDefaultValue("quarkus.datasource.username", "backup-user")
        .withDefaultValue("quarkus.datasource.password", "secret-do-not-log")
        .withDefaultValue("nip.database.mysqldump-path", "missing-nip-mysqldump-executable")
        .withDefaultValue("nip.database.mysql-path", "missing-nip-mysql-executable")
        .build());
  }

  @Test
  void mysql57BackupOmitsMysql8OnlyStatisticsOption() {
    var config = new SmallRyeConfigBuilder()
        .withDefaultValue("quarkus.datasource.username", "backup-user")
        .withDefaultValue("nip.database.client-major", "5").build();
    var command = new DatabaseTransferService(config).command(new DatabaseTransferService.Target("localhost", 6912, "project006"), true);
    assertFalse(command.contains("--column-statistics=0"));
    assertTrue(command.contains("--single-transaction"));
    assertTrue(command.contains("--routines"));
    assertEquals("windows-ia32", DatabaseTransferService.bundledTarget("Windows 7", "x86"));
  }

  @Test
  void targetUsesActualJdbcDatabaseAndPort() {
    var target = DatabaseTransferService.parseTarget("jdbc:mysql://db.example:3307/project006?useSSL=true");
    assertEquals("db.example", target.host());
    assertEquals(3307, target.port());
    assertEquals("project006", target.database());
    assertEquals(3306, DatabaseTransferService.parseTarget("jdbc:mysql://localhost/test_db").port());
    assertThrows(IllegalStateException.class,
        () -> DatabaseTransferService.parseTarget("jdbc:mysql:loadbalance://host1,host2/test"));
    assertThrows(IllegalStateException.class,
        () -> DatabaseTransferService.parseTarget("jdbc:mysql://localhost/--all-databases"));
  }

  @Test
  void commandsExcludeCredentialsAndDisableClientCommands() {
    var service = service();
    var target = new DatabaseTransferService.Target("localhost", 3307, "project006");
    var backup = service.command(target, true);
    assertFalse(String.join(" ", backup).contains("secret-do-not-log"));
    assertTrue(backup.contains("--single-transaction"));
    assertTrue(backup.contains("--hex-blob"));
    assertFalse(backup.contains("--databases"));
    assertEquals("project006", backup.getLast());
    var restore = service.command(target, false);
    assertTrue(restore.contains("--binary-mode=1"));
    assertTrue(restore.contains("--local-infile=0"));
    assertFalse(restore.contains("--force"));
    assertEquals("--database=project006", restore.getLast());
  }

  @Test
  void rejectsEmptyWrongExtensionAndOversizedUploads() {
    assertThrows(IllegalArgumentException.class, () -> DatabaseTransferService.validateUpload(null, 1));
    assertThrows(IllegalArgumentException.class, () -> DatabaseTransferService.validateUpload("a.txt", 1));
    assertThrows(IllegalArgumentException.class, () -> DatabaseTransferService.validateUpload("a.sql", 0));
    assertThrows(IllegalArgumentException.class, () -> DatabaseTransferService.validateUpload("a.sql",
        512L * 1024 * 1024 + 1));
    assertDoesNotThrow(() -> DatabaseTransferService.validateUpload("large-backup.sql", 400L * 1024 * 1024));
    assertDoesNotThrow(() -> DatabaseTransferService.validateUpload("backup.SQL", 42));
  }

  @Test
  void missingToolsAreReportedAndFailureReleasesOperationLock() {
    var service = service();
    assertEquals(false, service.status().get("backupAvailable"));
    assertEquals(false, service.status().get("importAvailable"));
    var first = assertThrows(IllegalStateException.class, service::backup);
    assertTrue(first.getMessage().contains("未能启动"));
    assertEquals(false, service.status().get("busy"));
    assertEquals(first.getMessage(), assertThrows(IllegalStateException.class, service::backup).getMessage());
  }
}
