package org.lucee.lucli.cli.commands.deps;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.config.DependencyConfig;
import org.lucee.lucli.config.LuceeJsonConfig;
import org.lucee.lucli.config.LuceeLockFile;
import org.lucee.lucli.deps.GitDependencyInstaller;
import org.lucee.lucli.deps.LockedDependency;

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
            StringOutput.Quick.info("📦 Reading lucee.json...");
            LuceeJsonConfig config = LuceeJsonConfig.load(Paths.get("."));
            
            // 2. Apply environment if specified
            if (environment != null) {
                config.applyEnvironment(environment);
            }
            
            // 3. Parse dependencies
            StringOutput.Quick.info("🔍 Resolving dependencies...");
            List<DependencyConfig> deps = config.parseDependencies();
            
            // 4. Determine if we should install devDependencies
            boolean installDev = !production;
            if (environment != null && config.getPackages().getInstallDevDependencies() != null) {
                installDev = config.getPackages().getInstallDevDependencies();
            }
            
            List<DependencyConfig> devDeps = installDev ? config.parseDevDependencies() : List.of();
            
            // 5. Filter to git sources only for MVP
            long gitCount = deps.stream().filter(d -> "git".equals(d.getSource())).count();
            long gitDevCount = devDeps.stream().filter(d -> "git".equals(d.getSource())).count();
            
            if (dryRun) {
                System.out.println("\nWould install:");
                System.out.println("  " + deps.size() + " production dependencies (" + gitCount + " git sources)");
                System.out.println("  " + devDeps.size() + " dev dependencies (" + gitDevCount + " git sources)");
                System.out.println("\nGit dependencies:");
                deps.stream()
                    .filter(d -> "git".equals(d.getSource()))
                    .forEach(d -> System.out.println("  " + d.getName() + " (" + d.getUrl() + "@" + d.getRef() + ")"));
                devDeps.stream()
                    .filter(d -> "git".equals(d.getSource()))
                    .forEach(d -> System.out.println("  " + d.getName() + " (" + d.getUrl() + "@" + d.getRef() + ") [dev]"));
                
                StringOutput.Quick.info("\nℹ️  Note: Only git sources are supported in this MVP");
                System.out.println("   Other sources will be skipped:");
                deps.stream()
                    .filter(d -> !"git".equals(d.getSource()))
                    .forEach(d -> StringOutput.Quick.warning("  " + d.getName() + " (" + d.getSource() + ") - not implemented yet"));
                devDeps.stream()
                    .filter(d -> !"git".equals(d.getSource()))
                    .forEach(d -> StringOutput.Quick.warning("  " + d.getName() + " (" + d.getSource() + ") - not implemented yet [dev]"));
                    
                return 0;
            }
            
            // 6. Separate prod and dev dependencies for lock file
            List<DependencyConfig> prodDeps = deps;
            List<DependencyConfig> devDepsList = devDeps;
            
            List<DependencyConfig> allDeps = new ArrayList<>();
            allDeps.addAll(prodDeps);
            allDeps.addAll(devDepsList);
            
            List<DependencyConfig> gitDeps = allDeps.stream()
                .filter(d -> "git".equals(d.getSource()))
                .collect(java.util.stream.Collectors.toList());
            
            if (gitDeps.isEmpty()) {
                StringOutput.Quick.info("\nℹ️  No git dependencies to install");
                StringOutput.Quick.info("   (Only git sources are supported in this MVP)");
                return 0;
            }
            
            // 7. Read existing lock file for verification
            LuceeLockFile existingLockFile = LuceeLockFile.read();
            
            System.out.println("\n📥 Installing dependencies...");
            GitDependencyInstaller gitInstaller = new GitDependencyInstaller(Paths.get("."));
            
            java.util.Map<String, LockedDependency> installedProd = new java.util.LinkedHashMap<>();
            java.util.Map<String, LockedDependency> installedDev = new java.util.LinkedHashMap<>();
            int successCount = 0;
            int skipCount = 0;
            int unchangedCount = 0;
            
            for (DependencyConfig dep : allDeps) {
                if ("git".equals(dep.getSource())) {
                    try {
                        boolean isDevDep = devDepsList.contains(dep);
                        
                        // Check if dependency is already installed with same version
                        LockedDependency existing = isDevDep 
                            ? existingLockFile.getDevDependencies().get(dep.getName())
                            : existingLockFile.getDependencies().get(dep.getName());
                        
                        boolean needsInstall = force || existing == null || 
                            !matchesRequested(dep, existing);
                        
                        if (!needsInstall && installPathExists(existing.getInstallPath())) {
                            // Already installed and matches - use existing
                            if (isDevDep) {
                                installedDev.put(dep.getName(), existing);
                            } else {
                                installedProd.put(dep.getName(), existing);
                            }
                            System.out.println("  " + dep.getName() + " - unchanged (" + existing.getVersion() + ")");
                            unchangedCount++;
                        } else {
                            // Install/reinstall
                            LockedDependency locked = gitInstaller.install(dep);
                            
                            if (isDevDep) {
                                installedDev.put(dep.getName(), locked);
                            } else {
                                installedProd.put(dep.getName(), locked);
                            }
                            
                            successCount++;
                        }
                    } catch (Exception e) {
                        StringOutput.Quick.error("  ✗ Failed to install " + dep.getName() + ": " + e.getMessage());
                        if (LuCLI.verbose || LuCLI.debug) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    System.out.println("  " + dep.getName() + " (" + dep.getSource() + ") - skipped (not implemented)");
                    skipCount++;
                }
            }
            
            // 8. Write lock file
            if (successCount > 0 || unchangedCount > 0) {
                LuceeLockFile lockFile = new LuceeLockFile();
                lockFile.setDependencies(installedProd);
                lockFile.setDevDependencies(installedDev);
                
                try {
                    lockFile.write();
                } catch (Exception e) {
                    StringOutput.Quick.error("\n❌ Failed to write lock file: " + e.getMessage());
                    if (LuCLI.verbose || LuCLI.debug) {
                        e.printStackTrace();
                    }
                    return 1;
                }
            }
            
            // 9. Report results
            System.out.println();
            if (successCount > 0) {
                StringOutput.Quick.success("✅ " + successCount + " dependencies installed");
            }
            if (unchangedCount > 0) {
                StringOutput.Quick.info("ℹ️  " + unchangedCount + " dependencies unchanged");
            }
            if (skipCount > 0) {
                StringOutput.Quick.info("ℹ️  " + skipCount + " dependencies skipped (non-git sources)");
            }
            if (successCount > 0 || unchangedCount > 0) {
                StringOutput.Quick.info("📝 Updated lucee-lock.json");
            }
            
            return 0;
            
        } catch (Exception e) {
            StringOutput.Quick.error("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
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
        return false;
    }
    
    /**
     * Check if install path exists
     */
    private boolean installPathExists(String installPath) {
        if (installPath == null) return false;
        File path = new File(installPath);
        return path.exists() && path.isDirectory();
    }
}
