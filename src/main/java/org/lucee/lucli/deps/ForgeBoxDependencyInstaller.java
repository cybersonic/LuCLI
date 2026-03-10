package org.lucee.lucli.deps;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.config.DependencyConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Installer for ForgeBox-based CFML dependencies.
 *
 * Delegates installation to CommandBox (`box install`) while respecting the
 * DependencyConfig.installPath and mapping logic used elsewhere in LuCLI.
 *
 * Expected lucee.json shape (simplified):
 *
 *   "testbox": {
 *     "type": "cfml",
 *     "source": "forgebox",
 *     "version": "^5.0.0",
 *     "installPath": "dependencies/testbox",
 *     "mapping": "/testbox"
 *   }
 */
public class ForgeBoxDependencyInstaller implements DependencyInstaller {

    private final Path projectDir;

    public ForgeBoxDependencyInstaller(Path projectDir) {
        this.projectDir = projectDir != null ? projectDir : Paths.get(".");
    }

    @Override
    public boolean supports(DependencyConfig dep) {
        if (dep == null) return false;
        String source = dep.getSource();
        return "forgebox".equalsIgnoreCase(source);
    }

    @Override
    public LockedDependency install(DependencyConfig dep) throws Exception {
        if (dep.getName() == null || dep.getName().isBlank()) {
            throw new IllegalArgumentException("ForgeBox dependency is missing a name.");
        }

        if (dep.getInstallPath() == null || dep.getInstallPath().isBlank()) {
            // In practice applyDefaults() should have populated this, but fail
            // loudly if it has not.
            throw new IllegalStateException(
                "ForgeBox dependency '" + dep.getName() + "' is missing installPath. " +
                "Did you forget to call applyDefaults()?"
            );
        }

        if (!isCommandBoxAvailable()) {
            throw new IllegalStateException(
                "CommandBox (the `box` CLI) is required to install ForgeBox dependencies, " +
                "but `box` was not found on PATH. Please install CommandBox and try again."
            );
        }

        String name = dep.getName().trim();

        // Allow advanced users to override the package slug via url if they
        // need to, but default to the dependency name.
        String slug = (dep.getUrl() != null && !dep.getUrl().isBlank())
                ? dep.getUrl().trim()
                : name;

        String version = dep.getVersion();
        String pkgArg = (version != null && !version.isBlank())
                ? slug + "@" + version.trim()
                : slug;

        Path installDir = projectDir.resolve(dep.getInstallPath()).normalize();
        Path parent = installDir.getParent();
        Path installRoot = parent != null ? parent : projectDir;
        Files.createDirectories(installRoot);
        Path installRootBoxJson = installRoot.resolve("box.json");
        boolean hadInstallRootBoxJson = Files.exists(installRootBoxJson);

        // Ensure reinstall behavior is deterministic.
        deleteExisting(installDir);

        StringOutput.Quick.info("  Installing " + name + " from ForgeBox into " + dep.getInstallPath() + "...");

        ProcessBuilder pb = new ProcessBuilder(
            "box",
            "install",
            pkgArg
        );
        pb.directory(installRoot.toFile());
        pb.redirectErrorStream(true);

        Process p = pb.start();
        byte[] outBytes = p.getInputStream().readAllBytes();
        int exit = p.waitFor();
        String output = new String(outBytes);

        if (exit != 0) {
            throw new RuntimeException(
                "CommandBox install failed for " + pkgArg + " (exit " + exit + "):\n" + output
            );
        }

        // CommandBox installs package endpoints as <working-dir>/<package-name>.
        // Normalize into the configured installPath so lock entries are consistent
        // even if the desired path basename differs from dependency name.
        Path installedByBox = installRoot.resolve(name).normalize();
        if (!Files.exists(installDir)) {
            if (Files.exists(installedByBox)) {
                if (!installedByBox.equals(installDir)) {
                    Path configuredParent = installDir.getParent();
                    if (configuredParent != null) {
                        Files.createDirectories(configuredParent);
                    }
                    Files.move(installedByBox, installDir, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                throw new RuntimeException(
                    "CommandBox install completed, but expected install directory was not found: " +
                    installDir + "\nCommand output:\n" + output
                );
            }
        }

        // CommandBox creates a box.json in the working directory as a side-effect.
        // If one did not exist before install, remove the generated file.
        if (!hadInstallRootBoxJson && Files.exists(installRootBoxJson)) {
            Files.deleteIfExists(installRootBoxJson);
        }
        // Compute checksum of installed directory so we can detect changes on
        // subsequent runs similar to git/file installers.
        String checksum = ChecksumCalculator.calculate(installDir);

        LockedDependency locked = new LockedDependency();
        locked.setType(dep.getType());          // typically "cfml"
        locked.setSource("forgebox");
        locked.setInstallPath(dep.getInstallPath());
        locked.setMapping(dep.getMapping());
        locked.setIntegrity("sha512-" + checksum);
        locked.setVersion(version != null && !version.isBlank() ? version.trim() : "latest");
        locked.setResolved("forgebox:" + pkgArg);

        StringOutput.Quick.success("    ✓ Installed " + name + " from ForgeBox");
        return locked;
    }

    private void deleteExisting(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder())
                      .forEach(p -> {
                          try {
                              Files.deleteIfExists(p);
                          } catch (Exception ignored) {
                              // Best-effort cleanup
                          }
                      });
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    private boolean isCommandBoxAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("box", "version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes(); // drain output
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
