package org.lucee.lucli.cli.commands;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.paths.LucliPaths;
import org.lucee.lucli.system.SystemBackupManager;
import org.lucee.lucli.system.SystemCleaner;

import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * System-level commands for LuCLI home management.
 */
@Command(
    name = "system",
    description = "Manage LuCLI system-level state",
    mixinStandardHelpOptions = true,
    subcommands = {
        SystemCommand.InspectCommand.class,
        SystemCommand.PathsCommand.class,
        SystemCommand.CleanCommand.class,
        SystemCommand.BackupCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class SystemCommand implements Callable<Integer> {
    private static final Path DEFAULT_CFCONFIG_RELATIVE_PATH = Paths.get(
        "lucee-server",
        "lucee-server",
        "context",
        ".CFConfig.json"
    );

    @Override
    public Integer call() throws Exception {
        new CommandLine(this).usage(System.out);
        return 0;
    }

    private static Path resolveCfConfigPath(String explicitPath) {
        if (explicitPath != null && !explicitPath.isBlank()) {
            return Paths.get(explicitPath);
        }
        return LucliPaths.resolve().home().resolve(DEFAULT_CFCONFIG_RELATIVE_PATH);
    }

    static int printCfConfig(Path cfConfigPath) {
        Path normalizedPath = cfConfigPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedPath)) {
            StringOutput.Quick.error("CFConfig file not found: " + normalizedPath);
            return 1;
        }
        if (!Files.isRegularFile(normalizedPath)) {
            StringOutput.Quick.error("CFConfig path is not a file: " + normalizedPath);
            return 1;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Object parsed = mapper.readValue(normalizedPath.toFile(), Object.class);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed));
            return 0;
        } catch (Exception e) {
            StringOutput.Quick.error("Failed to parse CFConfig JSON at " + normalizedPath + ": " + e.getMessage());
            return 1;
        }
    }

    @Command(
        name = "inspect",
        description = "Inspect LuCLI-managed state artifacts"
    )
    static class InspectCommand implements Callable<Integer> {
        @Option(
            names = "--lucee",
            description = "Print Lucee CFConfig JSON in a readable format"
        )
        private boolean lucee;
        @Option(
            names = "--path",
            paramLabel = "<path>",
            description = "Optional path to .CFConfig.json (defaults to LuCLI home location)"
        )
        private String path;

        @Override
        public Integer call() throws Exception {
            if (!lucee) {
                new CommandLine(this).usage(System.out);
                return 0;
            }
            return printCfConfig(resolveCfConfigPath(path));
        }
    }

    @Command(
        name = "paths",
        description = "Print resolved LuCLI paths"
    )
    static class PathsCommand implements Callable<Integer> {

        @Option(names = "--json", description = "Output as JSON")
        private boolean json;

        @Override
        public Integer call() throws Exception {
            LucliPaths.ResolvedPaths paths = LucliPaths.resolve();
            Map<String, String> values = paths.asDisplayMap();

            if (json) {
                ObjectMapper mapper = new ObjectMapper();
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(values));
                return 0;
            }

            System.out.println("LuCLI resolved paths:");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }
            return 0;
        }
    }

    @Command(
        name = "clean",
        description = "Prune cache and backup artifacts safely"
    )
    static class CleanCommand implements Callable<Integer> {

        @Option(names = "--caches", description = "Clean cache targets")
        private boolean caches;

        @Option(names = "--backups", description = "Clean backup targets")
        private boolean backups;

        @Option(names = "--all", description = "Clean all known safe targets")
        private boolean all;

        @Option(names = {"-f", "--force"}, description = "Apply deletions (default is dry-run)")
        private boolean force;

        @Option(
            names = "--older-than",
            paramLabel = "<duration>",
            defaultValue = "30d",
            description = "Backup retention window, e.g. 30d, 12h, 90m (used when cleaning backups)"
        )
        private String olderThan;

        @Override
        public Integer call() throws Exception {
            boolean includeCaches = all || caches;
            boolean includeBackups = all || backups;

            // Default scope when no explicit target flags are provided.
            if (!includeCaches && !includeBackups) {
                includeCaches = true;
                includeBackups = true;
            }

            Duration backupAge = Duration.ofDays(30);
            if (includeBackups) {
                try {
                    backupAge = SystemCleaner.parseDurationSpec(olderThan);
                } catch (IllegalArgumentException e) {
                    StringOutput.Quick.error(e.getMessage());
                    return 1;
                }
            }

            LucliPaths.ResolvedPaths paths = LucliPaths.resolve();
            SystemCleaner cleaner = new SystemCleaner(paths);
            SystemCleaner.CleanOptions options = new SystemCleaner.CleanOptions(
                includeCaches,
                includeBackups,
                force,
                backupAge
            );

            SystemCleaner.CleanResult result = cleaner.clean(options);
            printResult(result, includeCaches, includeBackups, olderThan);
            return result.errors().isEmpty() ? 0 : 1;
        }

        private void printResult(SystemCleaner.CleanResult result, boolean includeCaches, boolean includeBackups, String olderThanValue) {
            String mode = result.dryRun() ? "DRY RUN (nothing deleted)" : "APPLY (deletions executed)";
            StringOutput.Quick.info("system clean: " + mode);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("caches", includeCaches);
            summary.put("backups", includeBackups);
            summary.put("olderThan", includeBackups ? olderThanValue : "n/a");
            summary.put("targets", result.targets().size());
            summary.put("estimatedBytes", result.estimatedBytes());

            System.out.println("Scope:");
            for (Map.Entry<String, Object> entry : summary.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }

            if (result.targets().isEmpty()) {
                StringOutput.Quick.info("No matching clean targets found.");
                return;
            }

            System.out.println("Targets:");
            for (SystemCleaner.CleanTarget target : result.targets()) {
                System.out.println(
                    "  - [" + target.category() + "] " + target.path()
                    + " (" + target.reason() + ", " + target.estimatedBytes() + " bytes)"
                );
            }

            if (result.dryRun()) {
                StringOutput.Quick.tip("Re-run with --force to apply deletions.");
                return;
            }

            StringOutput.Quick.success("Deleted " + result.deletedPaths().size() + " target(s), " + result.deletedBytes() + " bytes.");
            if (!result.skippedPaths().isEmpty()) {
                StringOutput.Quick.warning("Skipped " + result.skippedPaths().size() + " target(s).");
            }
            for (String error : result.errors()) {
                StringOutput.Quick.error(error);
            }
        }
    }

    @Command(
        name = "backup",
        description = "Create, inspect, verify, and restore LuCLI backups",
        subcommands = {
            BackupCommand.CreateCommand.class,
            BackupCommand.ListBackupsCommand.class,
            BackupCommand.VerifyCommand.class,
            BackupCommand.PruneCommand.class,
            BackupCommand.RestoreCommand.class,
            CommandLine.HelpCommand.class
        }
    )
    static class BackupCommand implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            new CommandLine(this).usage(System.out);
            return 0;
        }

        private static SystemBackupManager manager() {
            return new SystemBackupManager(LucliPaths.resolve());
        }

        @Command(
            name = "create",
            description = "Create a backup archive of LuCLI home"
        )
        static class CreateCommand implements Callable<Integer> {

            @Option(names = "--name", paramLabel = "<name>", description = "Optional backup name ('.zip' added if missing)")
            private String name;

            @Option(names = "--exclude-caches", description = "Exclude cache directories from the backup")
            private boolean excludeCaches;

            @Option(names = "--include-backups", description = "Include existing backups directory contents in the archive")
            private boolean includeBackups;

            @Override
            public Integer call() throws Exception {
                try {
                    SystemBackupManager.BackupCreateResult result = manager().createBackup(
                        new SystemBackupManager.BackupCreateOptions(
                            name,
                            !excludeCaches,
                            includeBackups
                        )
                    );

                    StringOutput.Quick.success("Backup created.");
                    System.out.println("  archive: " + result.archivePath());
                    System.out.println("  checksum: " + result.checksumPath());
                    System.out.println("  files: " + result.fileCount());
                    System.out.println("  bytes: " + result.archivedBytes());
                    return 0;
                } catch (Exception e) {
                    StringOutput.Quick.error("Backup creation failed: " + e.getMessage());
                    return 1;
                }
            }
        }

        @Command(
            name = "list",
            description = "List available backup archives"
        )
        static class ListBackupsCommand implements Callable<Integer> {

            @Option(names = "--json", description = "Output as JSON")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                List<SystemBackupManager.BackupInfo> backups = manager().listBackups();
                if (backups.isEmpty()) {
                    StringOutput.Quick.info("No backups found.");
                    return 0;
                }

                if (json) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (SystemBackupManager.BackupInfo backup : backups) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("archive", backup.archivePath().toString());
                        row.put("sizeBytes", backup.sizeBytes());
                        row.put("modifiedAt", backup.modifiedAt().toString());
                        row.put("checksumFile", backup.checksumPath().toString());
                        row.put("checksumPresent", Files.exists(backup.checksumPath()));
                        rows.add(row);
                    }
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
                    return 0;
                }

                System.out.println("Available LuCLI backups:");
                for (SystemBackupManager.BackupInfo backup : backups) {
                    String checksumStatus = Files.exists(backup.checksumPath()) ? "checksum:yes" : "checksum:no";
                    System.out.println(
                        "  - " + backup.archivePath().getFileName()
                            + " (" + backup.sizeBytes() + " bytes, " + backup.modifiedAt() + ", " + checksumStatus + ")"
                    );
                }
                return 0;
            }
        }

        @Command(
            name = "verify",
            description = "Verify backup checksum integrity"
        )
        static class VerifyCommand implements Callable<Integer> {

            @Parameters(
                index = "0",
                arity = "0..1",
                paramLabel = "[BACKUP]",
                description = "Backup name or path (defaults to latest backup)"
            )
            private String backupReference;

            @Option(names = "--all", description = "Verify all backup archives")
            private boolean all;

            @Override
            public Integer call() throws Exception {
                try {
                    List<SystemBackupManager.BackupVerifyResult> results = new ArrayList<>();
                    SystemBackupManager backupManager = manager();

                    if (all) {
                        results.addAll(backupManager.verifyAll());
                        if (results.isEmpty()) {
                            StringOutput.Quick.info("No backups found.");
                            return 0;
                        }
                    } else {
                        Path backupPath = backupManager.resolveBackupReference(backupReference);
                        results.add(backupManager.verifyBackup(backupPath));
                    }

                    boolean allValid = true;
                    for (SystemBackupManager.BackupVerifyResult result : results) {
                        if (result.valid()) {
                            StringOutput.Quick.success(result.archivePath().getFileName() + ": " + result.message());
                        } else {
                            allValid = false;
                            StringOutput.Quick.error(result.archivePath().getFileName() + ": " + result.message());
                            System.out.println("  expected: " + (result.expectedSha256() == null ? "<missing>" : result.expectedSha256()));
                            System.out.println("  actual:   " + result.actualSha256());
                        }
                    }

                    return allValid ? 0 : 1;
                } catch (Exception e) {
                    StringOutput.Quick.error("Backup verification failed: " + e.getMessage());
                    return 1;
                }
            }
        }

        @Command(
            name = "prune",
            description = "Prune old backups with retention policy (dry-run by default)"
        )
        static class PruneCommand implements Callable<Integer> {

            @Option(
                names = "--older-than",
                paramLabel = "<duration>",
                defaultValue = "30d",
                description = "Age threshold for pruning, e.g. 30d, 12h, 90m"
            )
            private String olderThan;

            @Option(
                names = "--keep",
                paramLabel = "<count>",
                defaultValue = "10",
                description = "Keep at least this many newest backups regardless of age"
            )
            private int keep;

            @Option(
                names = {"-f", "--force"},
                description = "Apply deletions (without this, prune runs in dry-run mode)"
            )
            private boolean force;

            @Override
            public Integer call() throws Exception {
                if (keep < 0) {
                    StringOutput.Quick.error("--keep must be >= 0");
                    return 1;
                }

                Duration olderThanDuration;
                try {
                    olderThanDuration = SystemCleaner.parseDurationSpec(olderThan);
                } catch (IllegalArgumentException e) {
                    StringOutput.Quick.error(e.getMessage());
                    return 1;
                }

                try {
                    SystemBackupManager.BackupPruneResult result = manager().pruneBackups(
                        new SystemBackupManager.BackupPruneOptions(olderThanDuration, keep, force)
                    );

                    StringOutput.Quick.info("backup prune mode: " + (result.dryRun() ? "DRY RUN" : "APPLY"));
                    System.out.println("  olderThan: " + olderThan);
                    System.out.println("  keep: " + keep);
                    System.out.println("  selected: " + result.selectedArchives().size());
                    System.out.println("  estimatedBytes: " + result.estimatedBytes());

                    if (result.selectedArchives().isEmpty()) {
                        StringOutput.Quick.info("No backups matched prune criteria.");
                        return 0;
                    }

                    for (Path archive : result.selectedArchives()) {
                        System.out.println("  - " + archive.getFileName());
                    }

                    if (result.dryRun()) {
                        StringOutput.Quick.tip("Re-run with --force to apply pruning.");
                        return 0;
                    }

                    StringOutput.Quick.success(
                        "Pruned "
                            + result.deletedArchives().size()
                            + " backup(s), reclaimed "
                            + result.deletedBytes()
                            + " bytes."
                    );
                    if (!result.errors().isEmpty()) {
                        for (String error : result.errors()) {
                            StringOutput.Quick.error(error);
                        }
                        return 1;
                    }
                    return 0;
                } catch (Exception e) {
                    StringOutput.Quick.error("Backup prune failed: " + e.getMessage());
                    return 1;
                }
            }
        }

        @Command(
            name = "restore",
            description = "Restore a backup archive (dry-run by default)"
        )
        static class RestoreCommand implements Callable<Integer> {

            @Parameters(
                index = "0",
                arity = "0..1",
                paramLabel = "[BACKUP]",
                description = "Backup name or path (defaults to latest backup)"
            )
            private String backupReference;

            @Option(
                names = {"-t", "--to"},
                paramLabel = "<dir>",
                description = "Restore destination directory (default: LuCLI home)"
            )
            private String destinationDirectory;

            @Option(
                names = {"-f", "--force"},
                description = "Apply restore (without this, restore runs as dry-run preview)"
            )
            private boolean force;

            @Option(
                names = "--skip-verify",
                description = "Skip checksum verification before restore"
            )
            private boolean skipVerify;

            @Override
            public Integer call() throws Exception {
                try {
                    SystemBackupManager backupManager = manager();
                    Path archivePath = backupManager.resolveBackupReference(backupReference);

                    if (!skipVerify) {
                        SystemBackupManager.BackupVerifyResult verifyResult = backupManager.verifyBackup(archivePath);
                        if (!verifyResult.valid()) {
                            StringOutput.Quick.error("Restore aborted: backup verification failed.");
                            StringOutput.Quick.error("Reason: " + verifyResult.message());
                            return 1;
                        }
                    }

                    Path destination = destinationDirectory != null && !destinationDirectory.isBlank()
                        ? Paths.get(destinationDirectory)
                        : LucliPaths.resolve().home();

                    SystemBackupManager.BackupRestoreResult result = backupManager.restoreBackup(
                        archivePath,
                        new SystemBackupManager.BackupRestoreOptions(destination, force)
                    );

                    StringOutput.Quick.info("Restore mode: " + (result.dryRun() ? "DRY RUN" : "APPLY"));
                    System.out.println("  archive: " + result.archivePath());
                    System.out.println("  destination: " + result.destinationDir());
                    System.out.println("  files: " + result.filesProcessed());
                    System.out.println("  bytes: " + result.bytesProcessed());

                    for (String warning : result.warnings()) {
                        StringOutput.Quick.warning(warning);
                    }
                    for (String error : result.errors()) {
                        StringOutput.Quick.error(error);
                    }

                    if (result.dryRun()) {
                        StringOutput.Quick.tip("Re-run with --force to apply restore.");
                    } else if (result.errors().isEmpty()) {
                        StringOutput.Quick.success("Restore completed.");
                    }

                    return result.errors().isEmpty() ? 0 : 1;
                } catch (Exception e) {
                    StringOutput.Quick.error("Backup restore failed: " + e.getMessage());
                    return 1;
                }
            }
        }
    }
}
