package org.lucee.lucli.paths;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized LuCLI path resolution.
 *
 * Resolution order for LuCLI home:
 * 1) JVM system property: -Dlucli.home
 * 2) Environment variable: LUCLI_HOME
 * 3) Default: ~/.lucli
 */
public final class LucliPaths {

    public static final String LUCLI_HOME_SYSTEM_PROPERTY = "lucli.home";
    public static final String LUCLI_HOME_ENV_VAR = "LUCLI_HOME";
    public static final String LUCLI_BACKUPS_DIR_SYSTEM_PROPERTY = "lucli.backups.dir";
    public static final String LUCLI_BACKUPS_DIR_ENV_VAR = "LUCLI_BACKUPS_DIR";

    private LucliPaths() {
    }

    /**
     * Resolve paths using the current process environment.
     */
    public static ResolvedPaths resolve() {
        return resolve(
            System.getProperty(LUCLI_HOME_SYSTEM_PROPERTY),
            System.getenv(LUCLI_HOME_ENV_VAR),
            System.getProperty(LUCLI_BACKUPS_DIR_SYSTEM_PROPERTY),
            System.getenv(LUCLI_BACKUPS_DIR_ENV_VAR),
            System.getProperty("user.home")
        );
    }

    /**
     * Resolve paths from explicit values (primarily useful for tests).
     */
    public static ResolvedPaths resolve(String systemPropertyValue, String envValue, String userHome) {
        return resolve(systemPropertyValue, envValue, null, null, userHome);
    }

    /**
     * Resolve paths from explicit values (primarily useful for tests).
     */
    public static ResolvedPaths resolve(
        String systemPropertyValue,
        String envValue,
        String backupsPropertyValue,
        String backupsEnvValue,
        String userHome
    ) {
        Path resolvedHome;
        String source;
        if (systemPropertyValue != null && !systemPropertyValue.trim().isEmpty()) {
            resolvedHome = Paths.get(systemPropertyValue.trim());
            source = "system-property";
        } else if (envValue != null && !envValue.trim().isEmpty()) {
            resolvedHome = Paths.get(envValue.trim());
            source = "environment";
        } else {
            String resolvedUserHome = userHome;
            if (resolvedUserHome == null || resolvedUserHome.trim().isEmpty()) {
                resolvedUserHome = System.getProperty("user.home");
            }
            resolvedHome = Paths.get(resolvedUserHome, ".lucli");
            source = "default";
        }

        Path resolvedBackups;
        if (backupsPropertyValue != null && !backupsPropertyValue.trim().isEmpty()) {
            resolvedBackups = Paths.get(backupsPropertyValue.trim());
        } else if (backupsEnvValue != null && !backupsEnvValue.trim().isEmpty()) {
            resolvedBackups = Paths.get(backupsEnvValue.trim());
        } else {
            Path normalizedHome = resolvedHome.toAbsolutePath().normalize();
            Path homeParent = normalizedHome.getParent();
            if (homeParent == null) {
                resolvedBackups = normalizedHome.resolveSibling(".lucli_backups");
            } else {
                resolvedBackups = homeParent.resolve(".lucli_backups");
            }
        }

        return forHome(resolvedHome, source, resolvedBackups);
    }

    /**
     * Build a resolved paths object for a known LuCLI home path.
     */
    public static ResolvedPaths forHome(Path lucliHome, String source) {
        Path normalizedHome = lucliHome.toAbsolutePath().normalize();
        Path parent = normalizedHome.getParent();
        Path defaultBackups = parent == null
            ? normalizedHome.resolveSibling(".lucli_backups")
            : parent.resolve(".lucli_backups");
        return forHome(normalizedHome, source, defaultBackups);
    }

    /**
     * Build a resolved paths object for a known LuCLI home path and backups path.
     */
    public static ResolvedPaths forHome(Path lucliHome, String source, Path backupsDir) {
        Path normalizedHome = lucliHome.toAbsolutePath().normalize();
        Path normalizedBackups = backupsDir.toAbsolutePath().normalize();
        String normalizedSource = (source == null || source.isBlank()) ? "unknown" : source;
        return new ResolvedPaths(normalizedHome, normalizedSource, normalizedBackups);
    }

    /**
     * All core LuCLI paths derived from the same home directory.
     */
    public record ResolvedPaths(Path home, String homeSource, Path backupsHome) {

        public Path serversDir() {
            return home.resolve("servers");
        }

        public Path expressDir() {
            return home.resolve("express");
        }

        public Path depsDir() {
            return home.resolve("deps");
        }

        public Path depsGitCacheDir() {
            return depsDir().resolve("git-cache");
        }

        public Path modulesDir() {
            return home.resolve("modules");
        }

        public Path backupsDir() {
            return backupsHome;
        }

        public Path secretsDir() {
            return home.resolve("secrets");
        }

        public Path secretsStoreFile() {
            return secretsDir().resolve("local.json");
        }

        public Path settingsFile() {
            return home.resolve("settings.json");
        }

        public Map<String, String> asDisplayMap() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("home", home.toString());
            values.put("homeSource", homeSource);
            values.put("serversDir", serversDir().toString());
            values.put("expressDir", expressDir().toString());
            values.put("depsDir", depsDir().toString());
            values.put("depsGitCacheDir", depsGitCacheDir().toString());
            values.put("modulesDir", modulesDir().toString());
            values.put("backupsDir", backupsDir().toString());
            values.put("secretsDir", secretsDir().toString());
            values.put("secretsStoreFile", secretsStoreFile().toString());
            values.put("settingsFile", settingsFile().toString());
            return values;
        }
    }
}
