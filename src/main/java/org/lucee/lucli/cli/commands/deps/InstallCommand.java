package org.lucee.lucli.cli.commands.deps;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.config.DependencyConfig;
import org.lucee.lucli.config.LuceeJsonConfig;
import org.lucee.lucli.config.LuceeLockFile;
import org.lucee.lucli.deps.ExtensionDependencyInstaller;
import org.lucee.lucli.deps.FileDependencyInstaller;
import org.lucee.lucli.deps.GitDependencyInstaller;
import org.lucee.lucli.deps.LockedDependency;
import org.lucee.lucli.server.LuceeServerManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Install command for dependencies
 * lucli deps install or lucli install (shortcut)
 */
@Command(
    name = "install",
    description = "Install dependencies from lucee.json"
)
public class InstallCommand implements Callable<Integer> {
    
    @Option(names = {"--env"}, description = "Environment (prod, dev, staging)")
    private String environment;
    
    @Option(names = {"--production"}, description = "Install only production dependencies")
    private boolean production;
    
    @Option(names = {"--force"}, description = "Force reinstall")
    private boolean force;
    
    @Option(names = {"--dry-run"}, description = "Show what would be installed")
    private boolean dryRun;
    
    @Override
    public Integer call() throws Exception {
        try {
            // 1. Parse configuration
            StringOutput.Quick.info(" Reading lucee.json...");
            Path projectDir = Paths.get(".");
            LuceeJsonConfig config = LuceeJsonConfig.load(projectDir);
            
            // 2. Apply environment if specified
            if (environment != null) {
                config.applyEnvironment(environment);
            }
            
            // 3. Parse dependencies
            StringOutput.Quick.info(" Resolving dependencies...");
            List<DependencyConfig> deps = config.parseDependencies();
            
            // 4. Determine if we should install devDependencies
            boolean installDev = !production;
            if (environment != null && config.getPackages().getInstallDevDependencies() != null) {
                installDev = config.getPackages().getInstallDevDependencies();
            }
            
            List<DependencyConfig> devDeps = installDev ? config.parseDevDependencies() : List.of();
            
            // 5. Basic stats for supported sources (git + file + extensions)
            long gitCount = deps.stream().filter(d -> isGitDependency(d)).count();
            long gitDevCount = devDeps.stream().filter(d -> isGitDependency(d)).count();
            long fileCount = deps.stream().filter(d -> isFileModuleDependency(d)).count();
            long fileDevCount = devDeps.stream().filter(d -> isFileModuleDependency(d)).count();
            long extCount = deps.stream().filter(d -> isExtensionDependency(d)).count();
            long extDevCount = devDeps.stream().filter(d -> isExtensionDependency(d)).count();
            
            if (dryRun) {
                StringOutput.Quick.print("\nWould install:");
                StringOutput.Quick.print("  " + deps.size() + " production dependencies (" + gitCount + " git, " + fileCount + " file, " + extCount + " extensions)");
                StringOutput.Quick.print("  " + devDeps.size() + " dev dependencies (" + gitDevCount + " git, " + fileDevCount + " file, " + extCount + " extensions)");
                StringOutput.Quick.print("\nGit dependencies:");
                deps.stream()
                    .filter(this::isGitDependency)
                    .forEach(d -> StringOutput.Quick.print("  " + d.getName() + " (" + d.getUrl() + "@" + d.getRef() + ")"));
                devDeps.stream()
                    .filter(this::isGitDependency)
                    .forEach(d -> StringOutput.Quick.print("  " + d.getName() + " (" + d.getUrl() + "@" + d.getRef() + ") [dev]"));

                StringOutput.Quick.print("\nFile dependencies (local path):");
                deps.stream()
                    .filter(this::isFileModuleDependency)
                    .forEach(d -> StringOutput.Quick.print("  " + d.getName() + " (path=" + d.getPath() + ", type=" + d.getType() + ")"));
                devDeps.stream()
                    .filter(this::isFileModuleDependency)
                    .forEach(d -> StringOutput.Quick.print("  " + d.getName() + " (path=" + d.getPath() + ", type=" + d.getType() + ") [dev]"));

                StringOutput.Quick.print("\nExtension dependencies:");
                deps.stream()
                    .filter(this::isExtensionDependency)
                    .forEach(d -> StringOutput.Quick.print("  " + d.getName() + " (type=extension, source=" + d.getSource() + ")"));
                devDeps.stream()
                    .filter(this::isExtensionDependency)
                    .forEach(d -> StringOutput.Quick.print("  " + d.getName() + " (type=extension, source=" + d.getSource() + ") [dev]"));
                
                StringOutput.Quick.info("Note: Git, file-based CFML/JAR and extension dependencies are supported.");
                StringOutput.Quick.print("   Other sources will be skipped:");
                deps.stream()
                    .filter(d -> !isSupportedDependency(d))
                    .forEach(d -> StringOutput.Quick.warning("  " + d.getName() + " (" + d.getSource() + ") - not implemented yet"));
                devDeps.stream()
                    .filter(d -> !isSupportedDependency(d))
                    .forEach(d -> StringOutput.Quick.warning("  " + d.getName() + " (" + d.getSource() + ") - not implemented yet [dev]"));

                // Show the realized dependency configuration (including environment overrides)
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    String realizedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
                    StringOutput.Quick.info("DRY RUN: Dependency configuration that would be used (from lucee.json):\n");
                    StringOutput.Quick.print(realizedJson);
                    StringOutput.Quick.success("Run without --dry-run to install these dependencies and update lucee-lock.json.");
                } catch (Exception e) {
                    StringOutput.Quick.warning("\n⚠️  Failed to render realized dependency configuration: " + e.getMessage());
                }
                    
                return 0;
            }
            
            // 6. Separate prod and dev dependencies for lock file
            List<DependencyConfig> prodDeps = deps;
            List<DependencyConfig> devDepsList = devDeps;
            
            List<DependencyConfig> allDeps = new ArrayList<>();
            allDeps.addAll(prodDeps);
            allDeps.addAll(devDepsList);
            
            boolean hasSupported = allDeps.stream().anyMatch(this::isSupportedDependency);
            if (!hasSupported) {
                StringOutput.Quick.info("\nℹ️  No git, file or extension dependencies to install");
                return 0;
            }
            
            // 6. Separate prod and dev dependencies for lock file
            LuceeLockFile existingLockFile = LuceeLockFile.read();
            
            StringOutput.Quick.info(" Installing dependencies...");
            GitDependencyInstaller gitInstaller = new GitDependencyInstaller(projectDir);
            ExtensionDependencyInstaller extInstaller = new ExtensionDependencyInstaller(projectDir);
            FileDependencyInstaller fileInstaller = new FileDependencyInstaller(projectDir);
            
            java.util.Map<String, LockedDependency> installedProd = new java.util.LinkedHashMap<>();
            java.util.Map<String, LockedDependency> installedDev = new java.util.LinkedHashMap<>();
            int[] successCount = new int[] { 0 };
            int[] skipCount = new int[] { 0 };
            int[] unchangedCount = new int[] { 0 };
            
            // Track visited lucee.json files to avoid infinite recursion
            java.util.Set<java.nio.file.Path> visitedConfigs = new java.util.HashSet<>();
            visitedConfigs.add(projectDir.resolve("lucee.json").normalize());
            
            for (DependencyConfig dep : allDeps) {
                processDependency(dep, devDepsList.contains(dep), existingLockFile, installedProd, installedDev,
                    gitInstaller, extInstaller, fileInstaller, successCount, skipCount, unchangedCount,
                    projectDir, visitedConfigs);
            }
            // 8. Write lock file
            if (successCount[0] > 0 || unchangedCount[0] > 0) {
                LuceeLockFile lockFile = new LuceeLockFile();
                lockFile.setDependencies(installedProd);
                lockFile.setDevDependencies(installedDev);
                
                try {
                    lockFile.write();
                } catch (Exception e) {
                    StringOutput.Quick.error("\n Failed to write lock file: " + e.getMessage());
                    if (LuCLI.verbose || LuCLI.debug) {
                        e.printStackTrace();
                    }
                    return 1;
                }
            }
            
            // 9. Report results
            StringOutput.Quick.print("");
            if (successCount[0] > 0) {
                StringOutput.Quick.success(successCount[0] + " dependencies installed");
            }
            if (unchangedCount[0] > 0) {
                StringOutput.Quick.info(unchangedCount[0] + " dependencies unchanged");
            }
            if (skipCount[0] > 0) {
                StringOutput.Quick.info(skipCount[0] + " dependencies skipped (unsupported sources)");
            }
            if (successCount[0] > 0 || unchangedCount[0] > 0) {
                StringOutput.Quick.info(" Updated lucee-lock.json");

                // Show the LUCEE_EXTENSIONS value that will be used when the
                // server starts based on the newly written lock file.
                String luceeExt = LuceeServerManager.buildLuceeExtensions(Paths.get("."));
                if (luceeExt != null && !luceeExt.isBlank()) {
                    StringOutput.Quick.print("\nLUCEE_EXTENSIONS that will be set when the server starts:");
                    StringOutput.Quick.print("  " + luceeExt);
                } else {
                    StringOutput.Quick.print("\nNo LUCEE_EXTENSIONS will be set from dependencies.");
                }
            }
            
            return 0;
            
        } catch (Exception e) {
            StringOutput.Quick.error("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
    
    /**
     * Process a single dependency, including recursive resolution of nested
     * lucee.json files for file-based module dependencies.
     */
    private void processDependency(
        DependencyConfig dep,
        boolean isDevDep,
        LuceeLockFile existingLockFile,
        java.util.Map<String, LockedDependency> installedProd,
        java.util.Map<String, LockedDependency> installedDev,
        GitDependencyInstaller gitInstaller,
        ExtensionDependencyInstaller extInstaller,
        FileDependencyInstaller fileInstaller,
        int[] successCount,
        int[] skipCount,
        int[] unchangedCount,
        java.nio.file.Path projectDir,
        java.util.Set<java.nio.file.Path> visitedConfigs
    ) {
        boolean isGit = isGitDependency(dep);
        boolean isExtension = isExtensionDependency(dep);
        boolean isFileModule = isFileModuleDependency(dep);

        if (isGit || isExtension || isFileModule) {
            try {
                // Check if dependency is already installed with same version/source
                LockedDependency existing = isDevDep
                    ? existingLockFile.getDevDependencies().get(dep.getName())
                    : existingLockFile.getDependencies().get(dep.getName());

                boolean needsInstall = force || existing == null || !matchesRequested(dep, existing);

                LockedDependency effectiveLocked;
                if (!needsInstall && installPathExists(existing.getInstallPath())) {
                    // Already installed and matches - use existing
                    if (isDevDep) {
                        installedDev.put(dep.getName(), existing);
                    } else {
                        installedProd.put(dep.getName(), existing);
                    }
                    StringOutput.Quick.print("   " + dep.getName() + " - unchanged (" + existing.getVersion() + ")");
                    unchangedCount[0]++;
                    effectiveLocked = existing;
                } else {
                    // Install/reinstall via appropriate installer
                    if (isGit) {
                        effectiveLocked = gitInstaller.install(dep);
                    } else if (isExtension) {
                        effectiveLocked = extInstaller.install(dep);
                    } else {
                        effectiveLocked = fileInstaller.install(dep);
                    }

                    if (isDevDep) {
                        installedDev.put(dep.getName(), effectiveLocked);
                    } else {
                        installedProd.put(dep.getName(), effectiveLocked);
                    }

                    successCount[0]++;
                }

                // For file-based module dependencies, look for nested lucee.json
                // inside the installed directory and recursively install their
                // dependencies as well.
                if (isFileModule && effectiveLocked != null && effectiveLocked.getInstallPath() != null) {
                    java.nio.file.Path nestedProjectDir = projectDir.resolve(effectiveLocked.getInstallPath()).normalize();
                    java.nio.file.Path nestedConfigPath = nestedProjectDir.resolve("lucee.json").normalize();

                    if (java.nio.file.Files.exists(nestedConfigPath) && visitedConfigs.add(nestedConfigPath)) {
                        try {
                            LuceeJsonConfig nestedConfig = LuceeJsonConfig.load(nestedProjectDir);
                            java.util.List<DependencyConfig> nestedDeps = nestedConfig.parseDependencies();

                            for (DependencyConfig nestedDep : nestedDeps) {
                                // If nested dependency uses a relative path, resolve it
                                // against the nested project directory so that it can be
                                // installed correctly from the root project.
                                if (nestedDep.getPath() != null) {
                                    java.nio.file.Path raw = java.nio.file.Paths.get(nestedDep.getPath());
                                    if (!raw.isAbsolute()) {
                                        java.nio.file.Path abs = nestedProjectDir.resolve(raw).normalize();
                                        nestedDep.setPath(abs.toString());
                                    }
                                }
                                processDependency(
                                    nestedDep,
                                    false, // nested deps are treated as prod by default
                                    existingLockFile,
                                    installedProd,
                                    installedDev,
                                    gitInstaller,
                                    extInstaller,
                                    fileInstaller,
                                    successCount,
                                    skipCount,
                                    unchangedCount,
                                    projectDir,
                                    visitedConfigs
                                );
                            }
                        } catch (Exception e) {
                            StringOutput.Quick.warning("  Failed to process nested dependencies for " + dep.getName() + ": " + e.getMessage());
                            if (LuCLI.verbose || LuCLI.debug) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                StringOutput.Quick.error(" Failed to install " + dep.getName() + ": " + e.getMessage());
                if (LuCLI.verbose || LuCLI.debug) {
                    e.printStackTrace();
                }
            }
        } else {
            StringOutput.Quick.warning("  " + dep.getName() + " (" + dep.getSource() + ") - skipped (not implemented)");
            skipCount[0]++;
        }
    }

    /**
     * Check if requested dependency matches the locked version
     */
    private boolean matchesRequested(DependencyConfig requested, LockedDependency locked) {
        // For git sources, compare ref/version
        if ("git".equals(requested.getSource())) {
            return requested.getRef().equals(locked.getVersion());
        }
        // For extensions, compare ID and source
        if ("extension".equals(requested.getType())) {
            boolean idMatches = (requested.getId() == null && locked.getId() == null) ||
                               (requested.getId() != null && requested.getId().equals(locked.getId()));
            boolean sourceMatches = (requested.getUrl() == null && requested.getPath() == null && locked.getSource() != null && locked.getSource().equals("extension-provider")) ||
                                   (requested.getUrl() != null && requested.getUrl().equals(locked.getSource())) ||
                                   (requested.getPath() != null && ("path:" + requested.getPath()).equals(locked.getSource()));
            return idMatches && sourceMatches;
        }
        // For non-extension file-based dependencies, compare configured path and installPath
        if ("file".equals(requested.getSource()) && !"extension".equals(requested.getType())) {
            String requestedPath = requested.getPath() != null ? requested.getPath().trim() : null;
            String lockedResolved = locked.getResolved();
            String lockedInstallPath = locked.getInstallPath();

            boolean pathMatches = requestedPath != null && lockedResolved != null && lockedResolved.equals("file:" + requestedPath);
            boolean installPathMatches = requested.getInstallPath() != null && requested.getInstallPath().equals(lockedInstallPath);
            return pathMatches && installPathMatches;
        }
        return false;
    }
    
    /**
     * Check if install path exists (directory or file)
     */
    private boolean installPathExists(String installPath) {
        if (installPath == null) return false;
        File path = new File(installPath);
        return path.exists();
    }

    /**
     * Helper predicates for supported dependency types
     */
    private boolean isGitDependency(DependencyConfig dep) {
        return dep != null && "git".equals(dep.getSource());
    }

    private boolean isExtensionDependency(DependencyConfig dep) {
        return dep != null && "extension".equals(dep.getType());
    }

    private boolean isFileModuleDependency(DependencyConfig dep) {
        return dep != null && "file".equals(dep.getSource()) && !"extension".equals(dep.getType());
    }

    private boolean isSupportedDependency(DependencyConfig dep) {
        return isGitDependency(dep) || isExtensionDependency(dep) || isFileModuleDependency(dep);
    }
}
