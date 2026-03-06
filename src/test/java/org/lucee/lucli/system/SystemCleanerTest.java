package org.lucee.lucli.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.paths.LucliPaths;

class SystemCleanerTest {

    @TempDir
    Path tempDir;

    @Test
    void dryRunDoesNotDeleteTargets() throws Exception {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(tempDir.resolve("lucli-home"), "test");
        Files.createDirectories(paths.expressDir());
        Files.writeString(paths.expressDir().resolve("cache.zip"), "cache");

        SystemCleaner cleaner = new SystemCleaner(paths);
        SystemCleaner.CleanResult result = cleaner.clean(
            new SystemCleaner.CleanOptions(true, false, false, Duration.ofDays(30))
        );

        assertTrue(result.dryRun());
        assertFalse(result.targets().isEmpty());
        assertTrue(Files.exists(paths.expressDir().resolve("cache.zip")));
    }

    @Test
    void forceDeletesCacheTargets() throws Exception {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(tempDir.resolve("lucli-home"), "test");
        Files.createDirectories(paths.expressDir());
        Files.createDirectories(paths.depsGitCacheDir());
        Files.writeString(paths.expressDir().resolve("cache.zip"), "cache");
        Files.writeString(paths.depsGitCacheDir().resolve("repo"), "repo");

        SystemCleaner cleaner = new SystemCleaner(paths);
        SystemCleaner.CleanResult result = cleaner.clean(
            new SystemCleaner.CleanOptions(true, false, true, Duration.ofDays(30))
        );

        assertFalse(result.dryRun());
        assertTrue(result.errors().isEmpty());
        assertFalse(Files.exists(paths.expressDir()));
        assertFalse(Files.exists(paths.depsGitCacheDir()));
    }

    @Test
    void backupRetentionTargetsOnlyOlderFiles() throws Exception {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(tempDir.resolve("lucli-home"), "test");
        Files.createDirectories(paths.backupsDir());

        Path oldBackup = paths.backupsDir().resolve("old-backup.zip");
        Path freshBackup = paths.backupsDir().resolve("fresh-backup.zip");
        Files.writeString(oldBackup, "old");
        Files.writeString(freshBackup, "new");

        Files.setLastModifiedTime(oldBackup, FileTime.from(Instant.now().minus(Duration.ofDays(60))));
        Files.setLastModifiedTime(freshBackup, FileTime.from(Instant.now().minus(Duration.ofDays(2))));

        SystemCleaner cleaner = new SystemCleaner(paths);
        SystemCleaner.CleanResult result = cleaner.clean(
            new SystemCleaner.CleanOptions(false, true, false, Duration.ofDays(30))
        );

        assertEquals(1, result.targets().size());
        assertEquals(oldBackup.toAbsolutePath().normalize(), result.targets().get(0).path().toAbsolutePath().normalize());
        assertTrue(Files.exists(oldBackup));
        assertTrue(Files.exists(freshBackup));
    }
}
