package org.lucee.lucli.system;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.lucee.lucli.paths.LucliPaths;

/**
 * Backup and restore operations for LuCLI home state.
 */
public class SystemBackupManager {

    private static final DateTimeFormatter BACKUP_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final long BACKUP_PROGRESS_MIN_BYTES = 16L * 1024L * 1024L;
    private static final long BACKUP_PROGRESS_MIN_NANOS = Duration.ofMillis(750).toNanos();

    public record BackupCreateOptions(
        String name,
        boolean includeCaches,
        boolean includeExistingBackups
    ) {
    }

    public record BackupCreateResult(
        Path archivePath,
        Path checksumPath,
        int fileCount,
        long archivedBytes
    ) {
    }

    public record BackupCreateProgress(
        int totalFiles,
        long totalBytes,
        int filesCompleted,
        long bytesCompleted,
        String currentEntry,
        boolean complete
    ) {
    }

    @FunctionalInterface
    public interface BackupCreateProgressListener {
        void onProgress(BackupCreateProgress progress);
    }

    private record BackupArchiveEntry(Path sourcePath, String zipEntryPath) {
    }

    public record BackupInfo(
        Path archivePath,
        Path checksumPath,
        long sizeBytes,
        Instant modifiedAt
    ) {
    }

    public record BackupVerifyResult(
        Path archivePath,
        boolean checksumFilePresent,
        boolean valid,
        String expectedSha256,
        String actualSha256,
        String message
    ) {
    }

    public record BackupRestoreOptions(
        Path destinationDir,
        boolean force
    ) {
        public boolean dryRun() {
            return !force;
        }
    }

    public record BackupRestoreResult(
        boolean dryRun,
        Path archivePath,
        Path destinationDir,
        int filesProcessed,
        long bytesProcessed,
        List<String> warnings,
        List<String> errors
    ) {
    }

    public record BackupPruneOptions(
        Duration olderThan,
        int keep,
        boolean force
    ) {
        public boolean dryRun() {
            return !force;
        }
    }

    public record BackupPruneResult(
        boolean dryRun,
        Duration olderThan,
        int keep,
        List<Path> selectedArchives,
        List<Path> deletedArchives,
        List<Path> deletedChecksumFiles,
        List<Path> skippedArchives,
        List<String> errors,
        long estimatedBytes,
        long deletedBytes
    ) {
    }

    private final LucliPaths.ResolvedPaths paths;

    public SystemBackupManager(LucliPaths.ResolvedPaths paths) {
        this.paths = paths;
    }

    public BackupCreateResult createBackup(BackupCreateOptions options) throws IOException {
        return createBackup(options, null);
    }

    public BackupCreateResult createBackup(BackupCreateOptions options, BackupCreateProgressListener progressListener) throws IOException {
        Files.createDirectories(paths.home());
        Files.createDirectories(paths.backupsDir());

        Path archivePath = nextBackupArchivePath(options.name());
        Path checksumPath = checksumPathFor(archivePath);

        Path home = paths.home().toAbsolutePath().normalize();
        Path archiveAbs = archivePath.toAbsolutePath().normalize();
        Path checksumAbs = checksumPath.toAbsolutePath().normalize();

        Set<Path> filesToArchive = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(home)) {
            for (Path candidate : walk.filter(Files::isRegularFile).toList()) {
                Path normalized = candidate.toAbsolutePath().normalize();
                if (normalized.equals(archiveAbs) || normalized.equals(checksumAbs)) {
                    continue;
                }
                if (!shouldInclude(normalized, options)) {
                    continue;
                }
                filesToArchive.add(normalized);
            }
        }
        if (options.includeExistingBackups() && Files.exists(paths.backupsDir())) {
            try (Stream<Path> walk = Files.walk(paths.backupsDir())) {
                for (Path candidate : walk.filter(Files::isRegularFile).toList()) {
                    Path normalized = candidate.toAbsolutePath().normalize();
                    if (normalized.equals(archiveAbs) || normalized.equals(checksumAbs)) {
                        continue;
                    }
                    filesToArchive.add(normalized);
                }
            }
        }

        Path backupsRoot = paths.backupsDir().toAbsolutePath().normalize();
        List<BackupArchiveEntry> archiveEntries = new ArrayList<>(filesToArchive.size());
        long totalSourceBytes = 0L;
        for (Path file : filesToArchive) {
            String relativePath = toBackupRelativePath(file, home, backupsRoot);
            if (relativePath == null) {
                continue;
            }
            long sizeBytes = Files.size(file);
            totalSourceBytes += sizeBytes;
            archiveEntries.add(new BackupArchiveEntry(file, relativePath));
        }
        final long plannedSourceBytes = totalSourceBytes;

        emitProgress(progressListener, archiveEntries.size(), plannedSourceBytes, 0, 0L, null, false);

        int fileCount = 0;
        long byteCount = 0L;
        final long[] lastProgressBytes = new long[] { 0L };
        final long[] lastProgressNanos = new long[] { System.nanoTime() };
        try (OutputStream out = Files.newOutputStream(
                archivePath,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            ZipOutputStream zipOut = new ZipOutputStream(out)) {
            for (BackupArchiveEntry archiveEntry : archiveEntries) {
                ZipEntry entry = new ZipEntry(archiveEntry.zipEntryPath());
                entry.setTime(Files.getLastModifiedTime(archiveEntry.sourcePath()).toMillis());
                zipOut.putNextEntry(entry);
                try (InputStream in = Files.newInputStream(archiveEntry.sourcePath())) {
                    final int completedFilesBeforeCurrent = fileCount;
                    final long completedBytesBeforeCurrent = byteCount;
                    byteCount += transfer(in, zipOut, bytesWritten -> {
                        if (progressListener == null) {
                            return;
                        }
                        long now = System.nanoTime();
                        long currentBytes = completedBytesBeforeCurrent + bytesWritten;
                        long bytesDelta = currentBytes - lastProgressBytes[0];
                        long nanosDelta = now - lastProgressNanos[0];
                        if (bytesDelta >= BACKUP_PROGRESS_MIN_BYTES || nanosDelta >= BACKUP_PROGRESS_MIN_NANOS) {
                            emitProgress(
                                progressListener,
                                archiveEntries.size(),
                                plannedSourceBytes,
                                completedFilesBeforeCurrent,
                                currentBytes,
                                archiveEntry.zipEntryPath(),
                                false
                            );
                            lastProgressBytes[0] = currentBytes;
                            lastProgressNanos[0] = now;
                        }
                    });
                }
                zipOut.closeEntry();
                fileCount++;
                emitProgress(
                    progressListener,
                    archiveEntries.size(),
                    plannedSourceBytes,
                    fileCount,
                    byteCount,
                    archiveEntry.zipEntryPath(),
                    false
                );
                lastProgressBytes[0] = byteCount;
                lastProgressNanos[0] = System.nanoTime();
            }
        }

        String archiveSha256 = computeFileSha256(archivePath);
        Files.writeString(
            checksumPath,
            archiveSha256 + "  " + archivePath.getFileName() + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );


        emitProgress(progressListener, archiveEntries.size(), plannedSourceBytes, fileCount, byteCount, null, true);
        return new BackupCreateResult(archivePath, checksumPath, fileCount, byteCount);
    }

    public List<BackupInfo> listBackups() throws IOException {
        if (!Files.exists(paths.backupsDir())) {
            return List.of();
        }

        List<BackupInfo> backups = new ArrayList<>();
        try (Stream<Path> stream = Files.list(paths.backupsDir())) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    continue;
                }
                backups.add(new BackupInfo(
                    file.toAbsolutePath().normalize(),
                    checksumPathFor(file),
                    Files.size(file),
                    Files.getLastModifiedTime(file).toInstant()
                ));
            }
        }

        backups.sort(Comparator.comparing(BackupInfo::modifiedAt).reversed());
        return backups;
    }

    public Optional<BackupInfo> latestBackup() throws IOException {
        List<BackupInfo> backups = listBackups();
        return backups.isEmpty() ? Optional.empty() : Optional.of(backups.get(0));
    }

    public Path resolveBackupReference(String reference) throws IOException {
        if (reference == null || reference.isBlank()) {
            Optional<BackupInfo> latest = latestBackup();
            if (latest.isEmpty()) {
                throw new IOException("No backups found in " + paths.backupsDir());
            }
            return latest.get().archivePath();
        }

        Path candidate = Paths.get(reference);
        if (!candidate.isAbsolute() && candidate.getNameCount() == 1) {
            candidate = paths.backupsDir().resolve(candidate);
        }
        if (!candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            candidate = candidate.resolveSibling(candidate.getFileName() + ".zip");
        }

        Path resolved = candidate.toAbsolutePath().normalize();
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new IOException("Backup archive not found: " + resolved);
        }
        return resolved;
    }

    public BackupVerifyResult verifyBackup(Path archivePath) throws IOException {
        Path normalizedArchive = archivePath.toAbsolutePath().normalize();
        Path checksumPath = checksumPathFor(normalizedArchive);
        String actualSha256 = computeFileSha256(normalizedArchive);

        if (!Files.exists(checksumPath)) {
            return new BackupVerifyResult(
                normalizedArchive,
                false,
                false,
                null,
                actualSha256,
                "Missing checksum file: " + checksumPath
            );
        }

        String expectedSha256 = readExpectedSha256(checksumPath);
        boolean valid = expectedSha256 != null && expectedSha256.equalsIgnoreCase(actualSha256);
        return new BackupVerifyResult(
            normalizedArchive,
            true,
            valid,
            expectedSha256,
            actualSha256,
            valid ? "Checksum OK" : "Checksum mismatch"
        );
    }

    public List<BackupVerifyResult> verifyAll() throws IOException {
        List<BackupVerifyResult> results = new ArrayList<>();
        for (BackupInfo backup : listBackups()) {
            results.add(verifyBackup(backup.archivePath()));
        }
        return results;
    }

    public BackupPruneResult pruneBackups(BackupPruneOptions options) throws IOException {
        Duration olderThan = options.olderThan() == null ? Duration.ofDays(30) : options.olderThan();
        int keep = Math.max(0, options.keep());

        List<BackupInfo> backups = listBackups();
        List<BackupInfo> candidates = new ArrayList<>();
        Instant cutoff = Instant.now().minus(olderThan);

        for (int i = 0; i < backups.size(); i++) {
            BackupInfo backup = backups.get(i);
            if (i < keep) {
                continue;
            }
            if (backup.modifiedAt().isBefore(cutoff)) {
                candidates.add(backup);
            }
        }

        List<Path> selectedArchives = new ArrayList<>();
        List<Path> deletedArchives = new ArrayList<>();
        List<Path> deletedChecksums = new ArrayList<>();
        List<Path> skippedArchives = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        long estimatedBytes = 0L;
        long deletedBytes = 0L;
        for (BackupInfo candidate : candidates) {
            selectedArchives.add(candidate.archivePath());
            estimatedBytes += candidate.sizeBytes();
            if (Files.exists(candidate.checksumPath())) {
                estimatedBytes += Files.size(candidate.checksumPath());
            }
        }

        if (!options.dryRun()) {
            for (BackupInfo candidate : candidates) {
                Path archive = candidate.archivePath();
                Path checksum = candidate.checksumPath();
                try {
                    if (Files.exists(archive)) {
                        long archiveSize = Files.size(archive);
                        Files.delete(archive);
                        deletedArchives.add(archive);
                        deletedBytes += archiveSize;
                    } else {
                        skippedArchives.add(archive);
                    }

                    if (Files.exists(checksum)) {
                        long checksumSize = Files.size(checksum);
                        Files.delete(checksum);
                        deletedChecksums.add(checksum);
                        deletedBytes += checksumSize;
                    }
                } catch (IOException e) {
                    skippedArchives.add(archive);
                    errors.add("Failed to prune " + archive.getFileName() + ": " + e.getMessage());
                }
            }
        }

        return new BackupPruneResult(
            options.dryRun(),
            olderThan,
            keep,
            selectedArchives,
            deletedArchives,
            deletedChecksums,
            skippedArchives,
            errors,
            estimatedBytes,
            deletedBytes
        );
    }

    public BackupRestoreResult restoreBackup(Path archivePath, BackupRestoreOptions options) throws IOException {
        Path normalizedArchive = archivePath.toAbsolutePath().normalize();
        Path destinationDir = options.destinationDir().toAbsolutePath().normalize();

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (options.force() && destinationDir.getParent() == null) {
            errors.add("Refusing to restore directly into filesystem root: " + destinationDir);
            return new BackupRestoreResult(
                options.dryRun(),
                normalizedArchive,
                destinationDir,
                0,
                0L,
                warnings,
                errors
            );
        }

        int filesProcessed = 0;
        long bytesProcessed = 0L;

        if (options.force()) {
            Files.createDirectories(destinationDir);
        }

        try (InputStream in = Files.newInputStream(normalizedArchive);
            ZipInputStream zipIn = new ZipInputStream(in)) {

            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zipIn.closeEntry();
                    continue;
                }

                Path target = destinationDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destinationDir)) {
                    warnings.add("Skipped suspicious zip entry: " + entry.getName());
                    zipIn.closeEntry();
                    continue;
                }

                filesProcessed++;
                if (options.force()) {
                    try {
                        Path parent = target.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        try (OutputStream fileOut = Files.newOutputStream(
                                target,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE
                            )) {
                            bytesProcessed += transfer(zipIn, fileOut);
                        }
                    } catch (IOException e) {
                        errors.add("Failed to restore " + entry.getName() + ": " + e.getMessage());
                        // Drain entry if write failed midway.
                        bytesProcessed += drain(zipIn);
                    }
                } else {
                    bytesProcessed += drain(zipIn);
                }
                zipIn.closeEntry();
            }
        }

        return new BackupRestoreResult(
            options.dryRun(),
            normalizedArchive,
            destinationDir,
            filesProcessed,
            bytesProcessed,
            warnings,
            errors
        );
    }

    private boolean shouldInclude(Path file, BackupCreateOptions options) {
        Path home = paths.home().toAbsolutePath().normalize();
        if (!file.startsWith(home)) {
            return false;
        }

        if (!options.includeExistingBackups()) {
            Path backups = paths.backupsDir().toAbsolutePath().normalize();
            Path legacyHomeBackups = home.resolve("backups").toAbsolutePath().normalize();
            if (file.startsWith(backups)) {
                return false;
            }
            if (file.startsWith(legacyHomeBackups)) {
                return false;
            }
        }

        if (!options.includeCaches()) {
            Path express = paths.expressDir().toAbsolutePath().normalize();
            Path gitCache = paths.depsGitCacheDir().toAbsolutePath().normalize();
            if (file.startsWith(express) || file.startsWith(gitCache)) {
                return false;
            }
        }
        return true;
    }

    private Path nextBackupArchivePath(String nameHint) throws IOException {
        String baseName = sanitizeBaseName(nameHint);
        if (baseName == null || baseName.isBlank()) {
            baseName = "lucli-backup-" + BACKUP_TIMESTAMP.format(Instant.now());
        }
        if (!baseName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            baseName = baseName + ".zip";
        }

        Path candidate = paths.backupsDir().resolve(baseName);
        if (!Files.exists(candidate)) {
            return candidate.toAbsolutePath().normalize();
        }

        String stem = baseName.substring(0, baseName.length() - 4);
        for (int i = 1; i < 10000; i++) {
            Path numbered = paths.backupsDir().resolve(stem + "-" + i + ".zip");
            if (!Files.exists(numbered)) {
                return numbered.toAbsolutePath().normalize();
            }
        }
        throw new IOException("Could not allocate unique backup file name for " + baseName);
    }

    private String sanitizeBaseName(String nameHint) {
        if (nameHint == null || nameHint.isBlank()) {
            return null;
        }
        String cleaned = nameHint.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }

    private Path checksumPathFor(Path archivePath) {
        return archivePath.resolveSibling(archivePath.getFileName().toString() + ".sha256");
    }

    private String toBackupRelativePath(Path file, Path home, Path backupsRoot) {
        if (file.startsWith(home)) {
            return toZipEntryPath(home.relativize(file));
        }
        if (file.startsWith(backupsRoot)) {
            return toZipEntryPath(Paths.get("backups").resolve(backupsRoot.relativize(file)));
        }
        return null;
    }

    private String readExpectedSha256(Path checksumPath) throws IOException {
        List<String> lines = Files.readAllLines(checksumPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String trimmed = line.trim();
            int firstSpace = trimmed.indexOf(' ');
            return firstSpace > 0 ? trimmed.substring(0, firstSpace) : trimmed;
        }
        return null;
    }

    private String computeFileSha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private String toZipEntryPath(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }

    private long transfer(InputStream in, OutputStream out) throws IOException {
        return transfer(in, out, null);
    }

    private long transfer(InputStream in, OutputStream out, LongConsumer progressConsumer) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            total += read;
            if (progressConsumer != null) {
                progressConsumer.accept(total);
            }
        }
        return total;
    }

    private long drain(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
        }
        return total;
    }

    private void emitProgress(
        BackupCreateProgressListener listener,
        int totalFiles,
        long totalBytes,
        int filesCompleted,
        long bytesCompleted,
        String currentEntry,
        boolean complete
    ) {
        if (listener == null) {
            return;
        }
        listener.onProgress(new BackupCreateProgress(
            totalFiles,
            totalBytes,
            filesCompleted,
            bytesCompleted,
            currentEntry,
            complete
        ));
    }
}
