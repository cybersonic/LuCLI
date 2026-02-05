package org.lucee.lucli.cli.commands.deps;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.config.DependencyConfig;
import org.lucee.lucli.config.LuceeJsonConfig;
import org.lucee.lucli.config.LuceeLockFile;
import org.lucee.lucli.deps.ExtensionDependencyInstaller;
import org.lucee.lucli.deps.FileDependencyInstaller;
import org.lucee.lucli.deps.ForgeBoxDependencyInstaller;
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

    /**
     * Maximum recursion depth for nested dependency installations.
     * This is currently unused but reserved for upcoming nested install support.
     */
    private static final int MAX_NESTED_DEP_DEPTH = 3;
    
    @Option(names = {"--env"}, description = "Environment (prod, dev, staging)")
    private String environment;
    
    @Option(names = {"--production"}, description = "Install only production dependencies")
    private boolean production;
    
    @Option(names = {"--force"}, description = "Force reinstall")
    private boolean force;
    
    @Option(names = {"--dry-run"}, description = "Show what would be installed")
    private boolean dryRun;
    
    @Option(names = {"--include-nested-deps"}, description = "Include nested project dependencies in dry-run output")
    private boolean includeNestedDeps;
    
    @Override
    public Integer call() throws Exception {
        try {
            // Delegate to project-root aware helper so this can be reused for nested projects later.
            Set<Path> visited = new HashSet<>();
            return installDependenciesForProject(Paths.get("."), environment, production, force, dryRun, includeNestedDeps, 0, visited);
        } catch (Exception e) {
            StringOutput.Quick.error("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * Core implementation for installing dependencies for a given project directory.
     * This is factored out so it can be reused for nested projects.
     */
    private Integer installDependenciesForProject(Path projectDir,
                                                  String environment,
                                                  boolean production,
                                                  boolean force,
                                                  boolean dryRun,
                                                  boolean includeNestedDeps,
                                                  int depth,
                                                  Set<Path> visited) {
        // 1. Parse configuration
        StringOutput.Quick.info(" Reading lucee.json...");
        LuceeJsonConfig config;
        try {
            config = LuceeJsonConfig.load(projectDir);
        } catch (Exception e) {
            StringOutput.Quick.error("❌ Error reading lucee.json from " + projectDir + ": " + e.getMessage());
            if (LuCLI.verbose || LuCLI.debug) {
                e.printStackTrace();
            }
            return 1;
        }
        
        // 2. Apply environment if specified
        if (environment != null) {
            if (depth == 0) {
                // Root project: preserve strict behavior (error if env is unknown)
                config.applyEnvironment(environment);
            } else {
                // Nested projects: be lenient if the environment is not defined
                try {
                    config.applyEnvironment(environment);
                } catch (IllegalArgumentException e) {
                    StringOutput.Quick.warning(" Environment '" + environment + "' not found in nested project at " + projectDir + "; using base configuration.");
                    if (LuCLI.verbose || LuCLI.debug) {
                        e.printStackTrace();
                    }
                }
            }
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
        
        // 5. Basic stats for supported sources (git + extensions)
        long gitCount = deps.stream().filter(d -> "git".equals(d.getSource())).count();
        long gitDevCount = devDeps.stream().filter(d -> "git".equals(d.getSource())).count();
        long extCount = deps.stream().filter(d -> "extension".equals(d.getType())).count();
        long extDevCount = devDeps.stream().filter(d -> "extension".equals(d.getType())).count();
        long forgeCount = deps.stream().filter(d -> "forgebox".equals(d.getSource())).count();
        long forgeDevCount = devDeps.stream().filter(d -> "forgebox".equals(d.getSource())).count();
        
        // 6. Separate prod and dev dependencies for lock file / nested processing
        List<DependencyConfig> prodDeps = deps;
        List<DependencyConfig> devDepsList = devDeps;
        
        List<DependencyConfig> allDeps = new ArrayList<>();
        allDeps.addAll(prodDeps);
        allDeps.addAll(devDepsList);
        
        if (dryRun) {
            // Print header for root or nested project
            if (depth == 0) {
                StringOutput.Quick.print("\nWould install:");
            } else {
                try {
                    Path rel = Paths.get(".").toAbsolutePath().relativize(projectDir);
                    StringOutput.Quick.print("\n  [Nested: " + rel + "] Would install:");
                } catch (IllegalArgumentException e) {
                    StringOutput.Quick.print("\n  [Nested: " + projectDir + "] Would install:");
                }
            }
            
            String indent = depth == 0 ? "  " : "    ";
            StringOutput.Quick.print(indent + deps.size() + " production dependencies (" + gitCount + " git, " + extCount + " extensions, " + forgeCount + " forgebox)");
            StringOutput.Quick.print(indent + devDeps.size() + " dev dependencies (" + gitDevCount + " git, " + extDevCount + " extensions, " + forgeDevCount + " forgebox)");
            StringOutput.Quick.print("\n" + indent.substring(0, indent.length() - 2) + "Git dependencies:");
            deps.stream()
                .filter(d -> "git".equals(d.getSource()))
                .forEach(d -> StringOutput.Quick.print(indent + d.getName() + " (" + d.getUrl() + "@" + d.getRef() + ")"));
            devDeps.stream()
                .filter(d -> "git".equals(d.getSource()))
                .forEach(d -> StringOutput.Quick.print(indent + d.getName() + " (" + d.getUrl() + "@" + d.getRef() + ") [dev]"));

            StringOutput.Quick.print("\n" + indent.substring(0, indent.length() - 2) + "Extension dependencies:");
            deps.stream()
                .filter(d -> "extension".equals(d.getType()))
                .forEach(d -> StringOutput.Quick.print(indent + d.getName() + " (type=extension, source=" + d.getSource() + ")"));
            devDeps.stream()
                .filter(d -> "extension".equals(d.getType()))
                .forEach(d -> StringOutput.Quick.print(indent + d.getName() + " (type=extension, source=" + d.getSource() + ") [dev]"));
            
            if (depth == 0) {
                StringOutput.Quick.info("Note: Git sources and extension dependencies are supported.");
            }
            if (deps.stream().anyMatch(d -> !"git".equals(d.getSource()) && !"extension".equals(d.getType()) && !"forgebox".equals(d.getSource())) ||
                devDeps.stream().anyMatch(d -> !"git".equals(d.getSource()) && !"extension".equals(d.getType()) && !"forgebox".equals(d.getSource()))) {
                StringOutput.Quick.print(indent.substring(0, indent.length() - 2) + "Other sources will be skipped:");
                deps.stream()
                    .filter(d -> !"git".equals(d.getSource()) && !"extension".equals(d.getType()))
                    .forEach(d -> StringOutput.Quick.warning(indent + d.getName() + " (" + d.getSource() + ") - not implemented yet"));
                devDeps.stream()
                    .filter(d -> !"git".equals(d.getSource()) && !"extension".equals(d.getType()))
                    .forEach(d -> StringOutput.Quick.warning(indent + d.getName() + " (" + d.getSource() + ") - not implemented yet [dev]"));
            }

            // Only show realized config for the root project
            if (depth == 0) {
                // Show the realized dependency configuration (including environment overrides)
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    String realizedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
                    StringOutput.Quick.info("DRY RUN: Dependency configuration that would be used (from lucee.json):\n");
                    StringOutput.Quick.print(realizedJson);
                } catch (Exception e) {
                    StringOutput.Quick.warning("\n⚠️  Failed to render realized dependency configuration: " + e.getMessage());
                    if (LuCLI.verbose || LuCLI.debug) {
                        e.printStackTrace();
                    }
                }
            }
            
            // If --include-nested-deps is specified, scan for nested projects and recurse dry-run
            if (includeNestedDeps) {
                performDryRunNestedIntrospection(projectDir, allDeps, environment, production, force, depth, visited);
            }
            
            if (depth == 0) {
                StringOutput.Quick.success("Run without --dry-run to install these dependencies and update lucee-lock.json.");
                if (!includeNestedDeps) {
                    StringOutput.Quick.info("\nTip: Use --include-nested-deps to see dependencies of nested projects in dry-run.");
                }
            }
                
            return 0;
        }
        
        boolean hasSupported = allDeps.stream().anyMatch(d -> "git".equals(d.getSource()) || "extension".equals(d.getType()) || "forgebox".equals(d.getSource()));
        if (!hasSupported) {
            StringOutput.Quick.info("\nℹ️  No git or extension dependencies to install");
            return 0;
        }
        
        // 7. Read existing lock file for verification
        LuceeLockFile existingLockFile = LuceeLockFile.read(projectDir);
        
        StringOutput.Quick.info(" Installing dependencies...");
        GitDependencyInstaller gitInstaller = new GitDependencyInstaller(projectDir);
        ExtensionDependencyInstaller extInstaller = new ExtensionDependencyInstaller(projectDir);
        ForgeBoxDependencyInstaller forgeInstaller = new ForgeBoxDependencyInstaller(projectDir);
        
        java.util.Map<String, LockedDependency> installedProd = new java.util.LinkedHashMap<>();
        java.util.Map<String, LockedDependency> installedDev = new java.util.LinkedHashMap<>();
        int successCount = 0;
        int skipCount = 0;
        int unchangedCount = 0;
        
        
        for (DependencyConfig dep : allDeps) {
            boolean isGit = "git".equals(dep.getSource());
            boolean isExtension = "extension".equals(dep.getType());
            boolean isForgeBox = "forgebox".equals(dep.getSource());

            if (isGit || isExtension || isForgeBox) {
                try {
                    boolean isDevDep = devDepsList.contains(dep);
                    
                    // Check if dependency is already installed with same version/source
                    LockedDependency existing = isDevDep 
                        ? existingLockFile.getDevDependencies().get(dep.getName())
                        : existingLockFile.getDependencies().get(dep.getName());
                    
                    boolean needsInstall = force || existing == null || 
                        !matchesRequested(dep, existing);
                    
                    LockedDependency effectiveLocked = null;

                    if (!needsInstall && installPathExists(projectDir, existing.getInstallPath())) {
                        // Already installed and matches - use existing
                        if (isDevDep) {
                            installedDev.put(dep.getName(), existing);
                        } else {
                            installedProd.put(dep.getName(), existing);
                        }
                        StringOutput.Quick.print("   " + dep.getName() + " - unchanged (" + existing.getVersion() + ")");
                        unchangedCount++;
                        effectiveLocked = existing;
                    } else {
                        // Install/reinstall via appropriate installer
                        LockedDependency locked = isGit
                            ? gitInstaller.install(dep)
                            : (isExtension
                                ? extInstaller.install(dep)
                                : forgeInstaller.install(dep));
                        
                        if (isDevDep) {
                            installedDev.put(dep.getName(), locked);
                        } else {
                            installedProd.put(dep.getName(), locked);
                        }
                        
                        successCount++;
                        effectiveLocked = locked;
                    }

                    // After installation or reuse, check for nested lucee.json in the install directory
                    if (effectiveLocked != null) {
                        maybeInstallNestedProject(projectDir, effectiveLocked, dep.getName(), environment, production, force, dryRun, includeNestedDeps, depth, visited);
                    }
                } catch (Exception e) {
                    StringOutput.Quick.error(" Failed to install " + dep.getName() + ": " + e.getMessage());
                    if (LuCLI.verbose || LuCLI.debug) {
                        e.printStackTrace();
                    }
                }
            } else {
                StringOutput.Quick.warning("  " + dep.getName() + " (" + dep.getSource() + ") - skipped (not implemented)");
                skipCount++;
            }
        }
        // 8. Write lock file
        if (successCount > 0 || unchangedCount > 0) {
            LuceeLockFile lockFile = new LuceeLockFile();
            lockFile.setDependencies(installedProd);
            lockFile.setDevDependencies(installedDev);
            
            try {
                lockFile.write(projectDir.toFile());
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
        if (successCount > 0) {
            StringOutput.Quick.success(successCount + " dependencies installed");
        }
        if (unchangedCount > 0) {
            StringOutput.Quick.info(unchangedCount + " dependencies unchanged");
        }
        if (skipCount > 0) {
            StringOutput.Quick.info(skipCount + " dependencies skipped (unsupported sources)");
        }
        if (successCount > 0 || unchangedCount > 0) {
            StringOutput.Quick.info(" Updated lucee-lock.json");

            // Only the root project (depth == 0) influences LUCEE_EXTENSIONS
            if (depth == 0) {
                // Show the LUCEE_EXTENSIONS value that will be used when the
                // server starts based on the newly written lock file.
                String luceeExt = LuceeServerManager.buildLuceeExtensions(projectDir);
                if (luceeExt != null && !luceeExt.isBlank()) {
                    StringOutput.Quick.print("\nLUCEE_EXTENSIONS that will be set when the server starts:");
                    StringOutput.Quick.print("  " + luceeExt);
                } else {
                    StringOutput.Quick.print("\nNo LUCEE_EXTENSIONS will be set from dependencies.");
                }
            }
        }
        
        return 0;
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
                if (!needsInstall && installPathExists(projectDir, existing.getInstallPath())) {
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
        if (isGitDependency(requested)) {
            return requested.getRef().equals(locked.getVersion());
        }
        // For extensions, compare ID and source
        if (isExtensionDependency(requested)) {
            boolean idMatches = (requested.getId() == null && locked.getId() == null) ||
                               (requested.getId() != null && requested.getId().equals(locked.getId()));
            boolean sourceMatches = (requested.getUrl() == null && requested.getPath() == null && locked.getSource() != null && locked.getSource().equals("extension-provider")) ||
                                   (requested.getUrl() != null && requested.getUrl().equals(locked.getSource())) ||
                                   (requested.getPath() != null && ("path:" + requested.getPath()).equals(locked.getSource()));
            return idMatches && sourceMatches;
        }
        // For ForgeBox dependencies, compare version (and source)
        if (isForgeBoxDependency(requested)) {
            if (locked.getSource() == null || !"forgebox".equals(locked.getSource())) {
                return false;
            }
            String requestedVersion = requested.getVersion();
            String lockedVersion = locked.getVersion();
            if (requestedVersion == null || requestedVersion.isBlank()) {
                // No explicit version requested: any forgebox-locked version is acceptable
                return lockedVersion != null && !lockedVersion.isBlank();
            }
            return requestedVersion.equals(lockedVersion);
        }
        
        // For non-extension file-based dependencies, compare configured path and installPath
        if (isFileModuleDependency(requested)) {
            String requestedPath = requested.getPath() != null ? requested.getPath().trim() : null;
            String lockedResolved = locked.getResolved();
            String lockedInstallPath = locked.getInstallPath();

            boolean pathMatches = requestedPath != null && lockedResolved != null && lockedResolved.equals("file:" + requestedPath);
            boolean installPathMatches = requested.getInstallPath() != null && requested.getInstallPath().equals(lockedInstallPath);
            return pathMatches && installPathMatches;
        }
        return false;
    }

    private boolean isGitDependency(DependencyConfig dep) {
        return "git".equals(dep.getSource());
    }

    private boolean isExtensionDependency(DependencyConfig dep) {
        return "extension".equals(dep.getType());
    }

    private boolean isFileModuleDependency(DependencyConfig dep) {
        return "file".equals(dep.getSource()) && !"extension".equals(dep.getType());
    }

    private boolean isForgeBoxDependency(DependencyConfig dep) {
        return dep != null && "forgebox".equals(dep.getSource());
    }
    
    /**
     * Check if install path exists within the given project directory.
     */
    private boolean installPathExists(Path projectDir, String installPath) {
        if (installPath == null) return false;
        File path = projectDir.resolve(installPath).toFile();
        return path.exists() && path.isDirectory();
    }

    /**
     * Detect and install dependencies for a nested project if the installed dependency
     * directory contains a lucee.json file.
     */
    private void maybeInstallNestedProject(Path projectDir,
                                           LockedDependency locked,
                                           String dependencyName,
                                           String environment,
                                           boolean production,
                                           boolean force,
                                           boolean dryRun,
                                           boolean includeNestedDeps,
                                           int depth,
                                           Set<Path> visited) {
        String installPath = locked.getInstallPath();
        if (installPath == null || installPath.isBlank()) {
            return;
        }

        Path candidateDir = projectDir.resolve(installPath).normalize();
        try {
            if (!Files.isDirectory(candidateDir)) {
                return;
            }
            if (!Files.exists(candidateDir.resolve("lucee.json"))) {
                return;
            }

            // Enforce maximum depth for nested installs
            if (depth >= MAX_NESTED_DEP_DEPTH) {
                StringOutput.Quick.warning(
                    " Skipping nested project at " + candidateDir +
                    " for dependency " + dependencyName +
                    " because maximum nested dependency depth (" + MAX_NESTED_DEP_DEPTH + ") has been reached.");
                return;
            }

            Path realCandidate = candidateDir.toRealPath();
            if (visited.contains(realCandidate)) {
                // Avoid processing the same project directory multiple times (cycles / shared deps)
                return;
            }
            visited.add(realCandidate);

            // Log nested project installation with a simple prefix; indentation can be refined later
            try {
                Path rel = projectDir.relativize(candidateDir);
                StringOutput.Quick.info(" Installing dependencies for nested project: " + rel);
            } catch (IllegalArgumentException e) {
                // Fallback if relativize fails (different roots, etc.)
                StringOutput.Quick.info(" Installing dependencies for nested project: " + candidateDir);
            }

            installDependenciesForProject(candidateDir, environment, production, force, dryRun, includeNestedDeps, depth + 1, visited);
        } catch (Exception e) {
            StringOutput.Quick.warning(" Failed to process nested project for dependency " + dependencyName +
                                       " at " + candidateDir + ": " + e.getMessage());
            if (LuCLI.verbose || LuCLI.debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Dry-run nested introspection: scan already-installed dependency directories
     * (based on existing lock file) for nested lucee.json files and report what would be installed.
     */
    private void performDryRunNestedIntrospection(Path projectDir,
                                                   List<DependencyConfig> allDeps,
                                                   String environment,
                                                   boolean production,
                                                   boolean force,
                                                   int depth,
                                                   Set<Path> visited) {
        // Read existing lock file to find installed paths
        LuceeLockFile existingLockFile = LuceeLockFile.read(projectDir);
        
        for (DependencyConfig dep : allDeps) {
            boolean isGit = "git".equals(dep.getSource());
            boolean isExtension = "extension".equals(dep.getType());
            
            if (!isGit && !isExtension) {
                continue;
            }
            
            // Look for locked entry to get install path
            LockedDependency locked = existingLockFile.getDependencies().get(dep.getName());
            if (locked == null) {
                locked = existingLockFile.getDevDependencies().get(dep.getName());
            }
            
            // If not in lock file, use the config's install path (may be default)
            String installPath = (locked != null) ? locked.getInstallPath() : dep.getInstallPath();
            if (installPath == null || installPath.isBlank()) {
                continue;
            }
            
            Path candidateDir = projectDir.resolve(installPath).normalize();
            try {
                if (!Files.isDirectory(candidateDir)) {
                    continue;
                }
                if (!Files.exists(candidateDir.resolve("lucee.json"))) {
                    continue;
                }
                
                // Enforce maximum depth for nested introspection
                if (depth >= MAX_NESTED_DEP_DEPTH) {
                    StringOutput.Quick.warning(
                        "  Skipping nested project at " + candidateDir +
                        " for dependency " + dep.getName() +
                        " because maximum nested dependency depth (" + MAX_NESTED_DEP_DEPTH + ") has been reached.");
                    continue;
                }
                
                Path realCandidate = candidateDir.toRealPath();
                if (visited.contains(realCandidate)) {
                    continue;
                }
                visited.add(realCandidate);
                
                // Recursively dry-run the nested project
                installDependenciesForProject(candidateDir, environment, production, force, true, true, depth + 1, visited);
            } catch (Exception e) {
                if (LuCLI.verbose || LuCLI.debug) {
                    StringOutput.Quick.warning("  Failed to introspect nested project for dependency " + dep.getName() +
                                               " at " + candidateDir + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}
