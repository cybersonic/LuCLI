package org.lucee.lucli.deps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.config.DependencyConfig;

/**
 * Git dependency installer
 * Clones Git repositories and installs them with optional subPath support
 */
public class GitDependencyInstaller implements DependencyInstaller {
    
    private final Path projectDir;
    private final Path tempDir;
    
    public GitDependencyInstaller(Path projectDir) {
        this.projectDir = projectDir;
        // Use ~/.lucli/tmp/deps for temp clones
        String lucliHome = System.getProperty("lucli.home");
        if (lucliHome == null) {
            lucliHome = System.getenv("LUCLI_HOME");
        }
        if (lucliHome == null) {
            lucliHome = System.getProperty("user.home") + "/.lucli";
        }
        this.tempDir = Paths.get(lucliHome, "tmp", "deps");
    }
    
    @Override
    public boolean supports(DependencyConfig dep) {
        return "git".equals(dep.getSource());
    }
    
    @Override
    public LockedDependency install(DependencyConfig dep) throws Exception {
        StringOutput.Quick.info("  Installing " + dep.getName() + " from git...");
        
        // 1. Create temp directory for this dependency
        Path depTempDir = tempDir.resolve(dep.getName());
        Files.createDirectories(depTempDir);
        
        try {
            // 2. Clone the repository
            StringOutput.Quick.info("    🔄 Cloning " + dep.getUrl() + "...");
            cloneRepository(dep.getUrl(), depTempDir);
            
            // 3. Checkout the specified ref
            String ref = dep.getRef() != null ? dep.getRef() : "main";
            StringOutput.Quick.info("    📌 Checking out " + ref + "...");
            checkoutRef(depTempDir, ref);
            
            // 4. Get commit hash
            String commitHash = getCommitHash(depTempDir);
            StringOutput.Quick.info("    📝 Commit: " + commitHash.substring(0, 8));
            
            // 5. Determine source directory (with subPath if specified)
            Path sourceDir = depTempDir;
            if (dep.getSubPath() != null && !dep.getSubPath().trim().isEmpty()) {
                sourceDir = depTempDir.resolve(dep.getSubPath());
                if (!Files.exists(sourceDir)) {
                    throw new IOException("SubPath '" + dep.getSubPath() + "' not found in repository");
                }
                StringOutput.Quick.info("    📦 Extracting " + dep.getSubPath() + "/...");
            }
            
            // 6. Copy to install path
            Path installPath = projectDir.resolve(dep.getInstallPath());
            StringOutput.Quick.info("    📂 Installing to " + dep.getInstallPath() + "...");
            
            // Remove existing installation if present
            if (Files.exists(installPath)) {
                deleteDirectory(installPath);
            }
            
            // Copy files
            copyDirectory(sourceDir, installPath);
            
            // 7. Calculate checksum
            String checksum = ChecksumCalculator.calculate(installPath);
            
            // 8. Create lock file entry
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
            
        } finally {
            // 9. Clean up temp directory
            if (Files.exists(depTempDir)) {
                deleteDirectory(depTempDir);
            }
        }
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
