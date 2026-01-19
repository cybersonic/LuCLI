package org.lucee.lucli.deps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import org.lucee.lucli.Settings;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.config.DependencyConfig;

/**
 * Git dependency installer
 * Clones Git repositories and installs them with optional subPath support.
 *
 * Uses a shared cache under ~/.lucli/deps/git-cache so subsequent installs of
 * the same repository can reuse the clone. This behavior is controlled by the
 * usePersistentGitCache setting in ~/.lucli/settings.json (defaults to true).
 */
public class GitDependencyInstaller implements DependencyInstaller {
    
    private final Path projectDir;
    private final Path cacheRoot;
    private final Settings settings;
    
    public GitDependencyInstaller(Path projectDir) {
        this.projectDir = projectDir;
        this.settings = new Settings();
        
        String lucliHome = System.getProperty("lucli.home");
        if (lucliHome == null) {
            lucliHome = System.getenv("LUCLI_HOME");
        }
        if (lucliHome == null) {
            lucliHome = System.getProperty("user.home") + "/.lucli";
        }
        // Use ~/.lucli/deps/git-cache for git dependency clones
        this.cacheRoot = Paths.get(lucliHome, "deps", "git-cache");
    }
    
    @Override
    public boolean supports(DependencyConfig dep) {
        return "git".equals(dep.getSource());
    }
    
    @Override
    public LockedDependency install(DependencyConfig dep) throws Exception {
        StringOutput.Quick.info("  Installing " + dep.getName() + " from git...");
        
        boolean useCache = settings.usePersistentGitCache();
        String ref = dep.getRef() != null ? dep.getRef() : "main";
        
        if (useCache) {
            // Persistent cache: reuse clones by name+URL hash, always fetching
            Files.createDirectories(cacheRoot);
            String cacheKey = buildCacheKey(dep);
            Path repoDir = cacheRoot.resolve(cacheKey);
            
            try {
                try {
                    ensureRepository(repoDir, dep.getUrl());
                } catch (Exception e) {
                    // Cache corrupted or fetch failed - delete and re-clone once
                    StringOutput.Quick.info("    ⚠️  Cache for " + dep.getName() + " is invalid, recloning...");
                    deleteDirectory(repoDir);
                    cloneRepository(dep.getUrl(), repoDir);
                }
                
                return installFromRepoDir(dep, repoDir, ref);
            } catch (Exception e) {
                // We've already attempted a fresh clone once; surface the error
                throw e;
            }
        } else {
            // Non-persistent behavior: use a throwaway clone and delete it after
            Files.createDirectories(cacheRoot);
            String tempKey = buildCacheKey(dep) + "-" + System.nanoTime();
            Path tempDir = cacheRoot.resolve(tempKey);
            
            try {
                StringOutput.Quick.info("    🔄 Cloning " + dep.getUrl() + "...");
                cloneRepository(dep.getUrl(), tempDir);
                return installFromRepoDir(dep, tempDir, ref);
            } finally {
                if (Files.exists(tempDir)) {
                    deleteDirectory(tempDir);
                }
            }
        }
    }
    
    /**
     * Build a stable cache key from dependency name and URL.
     */
    private String buildCacheKey(DependencyConfig dep) {
        String name = dep.getName() != null ? dep.getName() : "dep";
        String url = dep.getUrl() != null ? dep.getUrl() : "";
        int urlHash = url.hashCode();
        return name + "-" + Integer.toHexString(urlHash);
    }
    
    /**
     * Ensure the repository exists and is up to date:
     * - If repo dir doesn't exist: clone
     * - If it exists and is a git repo: fetch --tags
     * - If it exists but is not a git repo: signal corruption
     */
    private void ensureRepository(Path repoDir, String url) throws Exception {
        if (Files.exists(repoDir.resolve(".git"))) {
            ProcessBuilder pb = new ProcessBuilder("git", "fetch", "--tags");
            pb.directory(repoDir.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new RuntimeException("Git fetch failed: " + output);
            }
        } else if (Files.exists(repoDir)) {
            // Directory exists but is not a git repo - treat as corrupt cache
            throw new RuntimeException("Existing cache directory is not a git repository: " + repoDir);
        } else {
            StringOutput.Quick.info("    🔄 Cloning " + url + "...");
            cloneRepository(url, repoDir);
        }
    }
    
    /**
     * Common installation flow once we have a prepared git working directory.
     */
    private LockedDependency installFromRepoDir(DependencyConfig dep, Path repoDir, String ref) throws Exception {
        // 1. Checkout the specified ref
        StringOutput.Quick.info("    📌 Checking out " + ref + "...");
        checkoutRef(repoDir, ref);
        
        // 2. Get commit hash
        String commitHash = getCommitHash(repoDir);
        StringOutput.Quick.info("    📝 Commit: " + commitHash.substring(0, 8));
        
        // 3. Determine source directory (with subPath if specified)
        Path sourceDir = repoDir;
        if (dep.getSubPath() != null && !dep.getSubPath().trim().isEmpty()) {
            sourceDir = repoDir.resolve(dep.getSubPath());
            if (!Files.exists(sourceDir)) {
                throw new IOException("SubPath '" + dep.getSubPath() + "' not found in repository");
            }
            StringOutput.Quick.info("    📦 Extracting " + dep.getSubPath() + "/...");
        }
        
        // 4. Copy to install path
        Path installPath = projectDir.resolve(dep.getInstallPath());
        StringOutput.Quick.info("    📂 Installing to " + dep.getInstallPath() + "...");
        
        // Remove existing installation if present
        if (Files.exists(installPath)) {
            deleteDirectory(installPath);
        }
        
        // Copy files
        copyDirectory(sourceDir, installPath);
        
        // 5. Calculate checksum
        String checksum = ChecksumCalculator.calculate(installPath);
        
        // 6. Create lock file entry
        LockedDependency locked = new LockedDependency();
        locked.setVersion(ref);
        locked.setResolved(dep.getUrl() + "#" + ref);
        locked.setIntegrity("sha512-" + checksum);
        locked.setSource("git");
        locked.setType(dep.getType());
        locked.setInstallPath(dep.getInstallPath());
        locked.setMapping(dep.getMapping());
        locked.setGitCommit(commitHash);
        if (dep.getSubPath() != null) {
            locked.setSubPath(dep.getSubPath());
        }
        
        StringOutput.Quick.success("    ✓ Installed " + dep.getName());
        return locked;
    }
    
    /**
     * Clone a Git repository
     * For tags/specific refs, we clone with --no-tags and fetch the ref separately
     */
    private void cloneRepository(String url, Path targetDir) throws Exception {
        // Clone with single branch only (faster)
        ProcessBuilder pb = new ProcessBuilder("git", "clone", "--single-branch", url, targetDir.toString());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new RuntimeException("Git clone failed: " + output);
        }
    }
    
    /**
     * Checkout a specific ref (tag, branch, or commit)
     */
    private void checkoutRef(Path repoDir, String ref) throws Exception {
        // Try to checkout directly first (works for branches)
        ProcessBuilder pb = new ProcessBuilder("git", "checkout", ref);
        pb.directory(repoDir.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            // If checkout failed, fetch all tags and try again (for tags)
            ProcessBuilder fetchPb = new ProcessBuilder("git", "fetch", "--tags");
            fetchPb.directory(repoDir.toFile());
            fetchPb.redirectErrorStream(true);
            Process fetchProcess = fetchPb.start();
            fetchProcess.waitFor();
            
            // Try checkout again
            pb = new ProcessBuilder("git", "checkout", ref);
            pb.directory(repoDir.toFile());
            pb.redirectErrorStream(true);
            process = pb.start();
            exitCode = process.waitFor();
            
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new RuntimeException("Git checkout failed for ref '" + ref + "': " + output);
            }
        }
    }
    
    /**
     * Get the current commit hash
     */
    private String getCommitHash(Path repoDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
        pb.directory(repoDir.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Git rev-parse failed: " + output);
        }
        
        return output;
    }
    
    /**
     * Copy directory recursively
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    
                    // Skip .git directory
                    if (sourcePath.toString().contains("/.git/") || 
                        sourcePath.toString().endsWith("/.git")) {
                        return;
                    }
                    
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy " + sourcePath, e);
                }
            });
        }
    }
    
    /**
     * Delete directory recursively
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                      .map(Path::toFile)
                      .forEach(File::delete);
            }
        }
    }
}
