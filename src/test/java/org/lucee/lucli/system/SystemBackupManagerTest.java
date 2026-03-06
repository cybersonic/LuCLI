package org.lucee.lucli.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.paths.LucliPaths;

class SystemBackupManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void createListAndVerifyBackup() throws Exception {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(tempDir.resolve("lucli-home"), "test");
        seedHome(paths);

        SystemBackupManager manager = new SystemBackupManager(paths);
        SystemBackupManager.BackupCreateResult created = manager.createBackup(
            new SystemBackupManager.BackupCreateOptions(null, true, false)
        );

        assertTrue(Files.exists(created.archivePath()));
        assertTrue(Files.exists(created.checksumPath()));
        assertTrue(created.fileCount() > 0);

        assertEquals(1, manager.listBackups().size());
        SystemBackupManager.BackupVerifyResult verify = manager.verifyBackup(created.archivePath());
        assertTrue(verify.valid(), verify.message());
    }

    @Test
    void createBackupCanExcludeCaches() throws Exception {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(tempDir.resolve("lucli-home"), "test");
        seedHome(paths);
        Files.createDirectories(paths.expressDir());
        Files.createDirectories(paths.depsGitCacheDir());
        Files.writeString(paths.expressDir().resolve("cache.zip"), "cache");
        Files.writeString(paths.depsGitCacheDir().resolve("repo.txt"), "repo");

        SystemBackupManager manager = new SystemBackupManager(paths);
        SystemBackupManager.BackupCreateResult created = manager.createBackup(
            new SystemBackupManager.BackupCreateOptions("no-cache", false, false)
        );

        Set<String> entries = zipEntries(created.archivePath());
        assertTrue(entries.contains("settings.json"));
        assertFalse(entries.contains("express/cache.zip"));
        assertFalse(entries.contains("deps/git-cache/repo.txt"));
    }

    @Test
    void restoreRunsDryRunThenApply() throws Exception {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(tempDir.resolve("lucli-home"), "test");
        seedHome(paths);

        SystemBackupManager manager = new SystemBackupManager(paths);
        SystemBackupManager.BackupCreateResult created = manager.createBackup(
            new SystemBackupManager.BackupCreateOptions("restore-test", true, false)
        );

        Path restoreTarget = tempDir.resolve("restore-target");

        SystemBackupManager.BackupRestoreResult dryRun = manager.restoreBackup(
            created.archivePath(),
            new SystemBackupManager.BackupRestoreOptions(restoreTarget, false)
        );
        assertTrue(dryRun.dryRun());
        assertFalse(Files.exists(restoreTarget.resolve("settings.json")));

        SystemBackupManager.BackupRestoreResult applied = manager.restoreBackup(
            created.archivePath(),
            new SystemBackupManager.BackupRestoreOptions(restoreTarget, true)
        );
        assertFalse(applied.dryRun());
        assertTrue(applied.errors().isEmpty());
        assertTrue(Files.exists(restoreTarget.resolve("settings.json")));
        assertEquals("true", Files.readString(restoreTarget.resolve("settings.json"), StandardCharsets.UTF_8).trim());
    }

    @Test
    void verifyFailsWhenArchiveIsTampered() throws Exception {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(tempDir.resolve("lucli-home"), "test");
        seedHome(paths);

        SystemBackupManager manager = new SystemBackupManager(paths);
        SystemBackupManager.BackupCreateResult created = manager.createBackup(
            new SystemBackupManager.BackupCreateOptions("tamper-test", true, false)
        );

        Files.writeString(
            created.archivePath(),
            "tamper",
            StandardCharsets.UTF_8,
            StandardOpenOption.APPEND
        );

        SystemBackupManager.BackupVerifyResult verify = manager.verifyBackup(created.archivePath());
        assertFalse(verify.valid());
    }

    @Test
    void pruneHonorsKeepCountAndDryRun() throws Exception {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(tempDir.resolve("lucli-home"), "test");
        seedHome(paths);

        SystemBackupManager manager = new SystemBackupManager(paths);
        SystemBackupManager.BackupCreateResult one = manager.createBackup(
            new SystemBackupManager.BackupCreateOptions("prune-one", true, false)
        );
        SystemBackupManager.BackupCreateResult two = manager.createBackup(
            new SystemBackupManager.BackupCreateOptions("prune-two", true, false)
        );
        SystemBackupManager.BackupCreateResult three = manager.createBackup(
            new SystemBackupManager.BackupCreateOptions("prune-three", true, false)
        );

        Files.setLastModifiedTime(one.archivePath(), FileTime.from(Instant.now().minus(Duration.ofDays(90))));
        Files.setLastModifiedTime(two.archivePath(), FileTime.from(Instant.now().minus(Duration.ofDays(60))));
        Files.setLastModifiedTime(three.archivePath(), FileTime.from(Instant.now().minus(Duration.ofDays(1))));

        // Keep newest 1 backup and prune anything older than 30 days.
        SystemBackupManager.BackupPruneResult dryRun = manager.pruneBackups(
            new SystemBackupManager.BackupPruneOptions(Duration.ofDays(30), 1, false)
        );
        assertTrue(dryRun.dryRun());
        assertEquals(2, dryRun.selectedArchives().size());
        assertTrue(Files.exists(one.archivePath()));
        assertTrue(Files.exists(two.archivePath()));
        assertTrue(Files.exists(three.archivePath()));

        SystemBackupManager.BackupPruneResult applied = manager.pruneBackups(
            new SystemBackupManager.BackupPruneOptions(Duration.ofDays(30), 1, true)
        );
        assertFalse(applied.dryRun());
        assertEquals(2, applied.deletedArchives().size());
        assertFalse(Files.exists(one.archivePath()));
        assertFalse(Files.exists(two.archivePath()));
        assertTrue(Files.exists(three.archivePath()));
    }

    private void seedHome(LucliPaths.ResolvedPaths paths) throws IOException {
        Files.createDirectories(paths.home());
        Files.createDirectories(paths.modulesDir());
        Files.createDirectories(paths.secretsDir());
        Files.createDirectories(paths.serversDir());
        Files.writeString(paths.settingsFile(), "true");
        Files.writeString(paths.modulesDir().resolve("demo.txt"), "module");
        Files.writeString(paths.secretsStoreFile(), "secret");
        Files.writeString(paths.serversDir().resolve("server.json"), "server");
    }

    private Set<String> zipEntries(Path archivePath) throws IOException {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(archivePath))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.add(entry.getName());
                }
                zipIn.closeEntry();
            }
        }
        return entries;
    }
}
