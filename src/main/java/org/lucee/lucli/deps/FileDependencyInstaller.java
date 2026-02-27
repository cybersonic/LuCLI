package org.lucee.lucli.deps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.stream.Stream;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.config.DependencyConfig;

/**
 * Installer for file-based dependencies (CFML modules or raw JARs) where the
 * source is a local path rather than a git repository.
 *
 * Supported shapes in lucee.json (simplified):
 *
 *   "my-module": {
 *     "type": "cfml",
 *     "source": "file",
 *     "path": "../some/local/module",
 *     "installPath": "dependencies/my-module"
    *   }
    *
    *   "my-lib": {
    *     "type": "jar",
    *     "source": "file",
    *     "path": "./lib/my-lib.jar",
    *     "installPath": "lib/my-lib.jar"
    *   }
 */
public class FileDependencyInstaller implements DependencyInstaller {

    private final Path projectDir;

    public FileDependencyInstaller(Path projectDir) {
        this.projectDir = projectDir != null ? projectDir : Paths.get(".");
    }

    @Override
    public boolean supports(DependencyConfig dep) {
        return dep != null && "file".equals(dep.getSource()) && !"extension".equals(dep.getType());
    }

    @Override
    public LockedDependency install(DependencyConfig dep) throws Exception {
        if (dep.getPath() == null || dep.getPath().trim().isEmpty()) {
            throw new IllegalArgumentException("File dependency '" + dep.getName() + "' is missing required 'path' property.");
        }

        String configuredPath = dep.getPath().trim();
        Path rawPath = Paths.get(configuredPath);
        Path sourcePath = rawPath.isAbsolute() ? rawPath : projectDir.resolve(rawPath).normalize();

        if (!Files.exists(sourcePath)) {
            throw new IOException("Source path for dependency '" + dep.getName() + "' not found: " + sourcePath);
        }

        Path installPath = projectDir.resolve(dep.getInstallPath());
        StringOutput.Quick.info("  Installing " + dep.getName() + " from local path...");

        // Remove existing installation if present
        deleteExisting(installPath);

        if (Files.isDirectory(sourcePath)) {
            // Copy directory tree (skipping any nested .git folders)
            copyDirectory(sourcePath, installPath);
        } else {
            // Single file (e.g. JAR). Ensure parent directory exists then copy.
            Path parent = installPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(sourcePath, installPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Calculate integrity hash based on installed contents
        String integrity;
        if (Files.isDirectory(installPath)) {
            integrity = "sha512-" + ChecksumCalculator.calculate(installPath);
        } else {
            integrity = "sha512-" + calculateFileChecksum(installPath);
        }

        LockedDependency locked = new LockedDependency();
        locked.setType(dep.getType());
        locked.setSource("file");
        locked.setInstallPath(dep.getInstallPath());
        locked.setMapping(dep.getMapping());
        locked.setIntegrity(integrity);
        locked.setResolved("file:" + configuredPath);

        if (dep.getVersion() != null) {
            locked.setVersion(dep.getVersion());
        }

        StringOutput.Quick.success("    ✓ Installed " + dep.getName() + " from " + configuredPath);
        return locked;
    }

    private void deleteExisting(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder())
                      .forEach(p -> {
                          try {
                              Files.deleteIfExists(p);
                          } catch (IOException ignored) {
                              // Best-effort cleanup
                          }
                      });
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    // Skip any .git directories to mirror git-based installs
                    String sp = sourcePath.toString();
                    if (sp.contains("/.git/") || sp.endsWith("/.git")) {
                        return;
                    }

                    Path relative = source.relativize(sourcePath);
                    Path targetPath = target.resolve(relative);
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Path parent = targetPath.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy " + sourcePath, e);
                }
            });
        }
    }

    private String calculateFileChecksum(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] data = Files.readAllBytes(file);
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
