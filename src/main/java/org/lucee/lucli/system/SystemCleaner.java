package org.lucee.lucli.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.lucee.lucli.paths.LucliPaths;

/**
 * Safe cleaner for LuCLI system-level state.
 */
public class SystemCleaner {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);

    public enum TargetType {
        DIRECTORY,
        FILE
    }

    public record CleanTarget(Path path, TargetType type, String category, String reason, long estimatedBytes) {
    }

    public record CleanOptions(boolean includeCaches, boolean includeBackups, boolean force, Duration backupMaxAge) {
        public boolean dryRun() {
            return !force;
        }
    }

    public record CleanResult(
        boolean dryRun,
        List<CleanTarget> targets,
        List<Path> deletedPaths,
        List<Path> skippedPaths,
        List<String> errors,
        long estimatedBytes,
        long deletedBytes
    ) {
    }

    private final LucliPaths.ResolvedPaths paths;

    public SystemCleaner(LucliPaths.ResolvedPaths paths) {
        this.paths = paths;
    }

    public CleanResult clean(CleanOptions options) throws IOException {
        List<CleanTarget> targets = collectTargets(options);
        long estimatedBytes = targets.stream().mapToLong(CleanTarget::estimatedBytes).sum();

        List<Path> deleted = new ArrayList<>();
        List<Path> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        long deletedBytes = 0L;

        if (!options.dryRun()) {
            for (CleanTarget target : targets) {
                try {
                    if (deleteTarget(target)) {
                        deleted.add(target.path());
                        deletedBytes += target.estimatedBytes();
                    } else {
                        skipped.add(target.path());
                    }
                } catch (IOException e) {
                    skipped.add(target.path());
                    errors.add("Failed to delete " + target.path() + ": " + e.getMessage());
                }
            }

            if (options.includeBackups()) {
                try {
                    pruneEmptyDirectories(paths.backupsDir());
                } catch (IOException e) {
                    errors.add("Failed to prune empty backup directories: " + e.getMessage());
                }
            }
        }

        return new CleanResult(options.dryRun(), targets, deleted, skipped, errors, estimatedBytes, deletedBytes);
    }

    public static Duration parseDurationSpec(String spec) {
        if (spec == null || spec.isBlank()) {
            return Duration.ofDays(30);
        }

        Matcher matcher = DURATION_PATTERN.matcher(spec.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration '" + spec + "'. Expected format like 30d, 12h, 90m.");
        }

        long value = Long.parseLong(matcher.group(1));
        char unit = Character.toLowerCase(matcher.group(2).charAt(0));

        return switch (unit) {
            case 's' -> Duration.ofSeconds(value);
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            case 'd' -> Duration.ofDays(value);
            case 'w' -> Duration.ofDays(value * 7);
            default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
        };
    }

    private List<CleanTarget> collectTargets(CleanOptions options) throws IOException {
        List<CleanTarget> targets = new ArrayList<>();

        if (options.includeCaches()) {
            addDirectoryTarget(targets, paths.expressDir(), "cache", "Lucee Express cache");
            addDirectoryTarget(targets, paths.depsGitCacheDir(), "cache", "Dependency git cache");
        }

        if (options.includeBackups()) {
            targets.addAll(collectBackupTargets(options.backupMaxAge()));
        }

        targets.sort(Comparator.comparing(t -> t.path().toString()));
        return targets;
    }

    private void addDirectoryTarget(List<CleanTarget> targets, Path path, String category, String reason) throws IOException {
        if (!Files.exists(path) || !isSafeTarget(path)) {
            return;
        }
        long size = estimatePathSize(path);
        targets.add(new CleanTarget(path, TargetType.DIRECTORY, category, reason, size));
    }

    private List<CleanTarget> collectBackupTargets(Duration maxAge) throws IOException {
        List<CleanTarget> targets = new ArrayList<>();
        Path backupsRoot = paths.backupsDir();
        if (!Files.exists(backupsRoot)) {
            return targets;
        }

        Instant cutoff = Instant.now().minus(maxAge == null ? Duration.ofDays(30) : maxAge);
        try (Stream<Path> walk = Files.walk(backupsRoot)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                if (!isSafeTarget(file)) {
                    continue;
                }
                FileTime lastModified = Files.getLastModifiedTime(file);
                if (lastModified.toInstant().isBefore(cutoff)) {
                    long size = estimatePathSize(file);
                    targets.add(new CleanTarget(file, TargetType.FILE, "backup", "Backup older than retention window", size));
                }
            }
        }
        return targets;
    }

    private boolean deleteTarget(CleanTarget target) throws IOException {
        Path path = target.path();
        if (!Files.exists(path)) {
            return false;
        }
        if (target.type() == TargetType.FILE) {
            Files.delete(path);
            return true;
        }

        try (Stream<Path> walk = Files.walk(path)) {
            for (Path candidate : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(candidate);
            }
        }
        return true;
    }

    private void pruneEmptyDirectories(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path candidate : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (!Files.isDirectory(candidate) || candidate.equals(root)) {
                    continue;
                }
                try (Stream<Path> children = Files.list(candidate)) {
                    if (children.findAny().isEmpty()) {
                        Files.deleteIfExists(candidate);
                    }
                }
            }
        }
    }

    private long estimatePathSize(Path path) throws IOException {
        if (!Files.exists(path)) {
            return 0L;
        }
        if (Files.isRegularFile(path)) {
            return Files.size(path);
        }

        long size = 0L;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                size += Files.size(file);
            }
        }
        return size;
    }

    private boolean isSafeTarget(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path home = paths.home().toAbsolutePath().normalize();
        Path backups = paths.backupsDir().toAbsolutePath().normalize();

        boolean underHome = normalized.startsWith(home) && !normalized.equals(home);
        boolean underBackups = normalized.startsWith(backups) && !normalized.equals(backups);
        if (!underHome && !underBackups) {
            return false;
        }

        Path servers = paths.serversDir().toAbsolutePath().normalize();
        Path modules = paths.modulesDir().toAbsolutePath().normalize();
        Path secrets = paths.secretsDir().toAbsolutePath().normalize();
        Path settings = paths.settingsFile().toAbsolutePath().normalize();

        if (normalized.equals(settings)) {
            return false;
        }
        if (normalized.equals(servers) || normalized.startsWith(servers)) {
            return false;
        }
        if (normalized.equals(modules) || normalized.startsWith(modules)) {
            return false;
        }
        if (normalized.equals(secrets) || normalized.startsWith(secrets)) {
            return false;
        }
        return true;
    }
}
