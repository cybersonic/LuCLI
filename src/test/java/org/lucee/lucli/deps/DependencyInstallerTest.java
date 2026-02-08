package org.lucee.lucli.deps;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.config.DependencyConfig;

/**
 * Unit tests for DependencyInstaller implementations
 * Tests installer selection and dependency configuration
 */
public class DependencyInstallerTest {

    @TempDir
    Path tempDir;

    private Path projectDir;

    @BeforeEach
    void setUp() throws Exception {
        projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
    }

    // ===================
    // GitDependencyInstaller Support Tests
    // ===================

    @Test
    void gitInstaller_supportsGitSource() {
        GitDependencyInstaller installer = new GitDependencyInstaller(projectDir);
        DependencyConfig dep = createDependencyConfig("my-dep", "git");
        
        assertTrue(installer.supports(dep));
    }

    @Test
    void gitInstaller_doesNotSupportOtherSources() {
        GitDependencyInstaller installer = new GitDependencyInstaller(projectDir);
        
        assertFalse(installer.supports(createDependencyConfig("my-dep", "npm")));
        assertFalse(installer.supports(createDependencyConfig("my-dep", "file")));
        assertFalse(installer.supports(createDependencyConfig("my-dep", "forgebox")));
        assertFalse(installer.supports(createDependencyConfig("my-dep", null)));
    }

    // ===================
    // FileDependencyInstaller Support Tests
    // ===================

    @Test
    void fileInstaller_supportsFileSource() {
        FileDependencyInstaller installer = new FileDependencyInstaller(projectDir);
        DependencyConfig dep = createDependencyConfig("my-dep", "file");
        
        assertTrue(installer.supports(dep));
    }

    @Test
    void fileInstaller_doesNotSupportOtherSources() {
        FileDependencyInstaller installer = new FileDependencyInstaller(projectDir);
        
        assertFalse(installer.supports(createDependencyConfig("my-dep", "git")));
        assertFalse(installer.supports(createDependencyConfig("my-dep", "npm")));
        assertFalse(installer.supports(createDependencyConfig("my-dep", null)));
    }

    // ===================
    // ForgeBoxDependencyInstaller Support Tests
    // ===================

    @Test
    void forgeboxInstaller_supportsForgeboxSource() {
        ForgeBoxDependencyInstaller installer = new ForgeBoxDependencyInstaller(projectDir);
        DependencyConfig dep = createDependencyConfig("my-dep", "forgebox");
        
        assertTrue(installer.supports(dep));
    }

    @Test
    void forgeboxInstaller_doesNotSupportOtherSources() {
        ForgeBoxDependencyInstaller installer = new ForgeBoxDependencyInstaller(projectDir);
        
        assertFalse(installer.supports(createDependencyConfig("my-dep", "git")));
        assertFalse(installer.supports(createDependencyConfig("my-dep", "file")));
        assertFalse(installer.supports(createDependencyConfig("my-dep", null)));
    }

    // ===================
    // ExtensionDependencyInstaller Support Tests
    // ===================

    @Test
    void extensionInstaller_supportsExtensionType() {
        ExtensionDependencyInstaller installer = new ExtensionDependencyInstaller(projectDir);
        DependencyConfig dep = new DependencyConfig();
        dep.setName("h2");
        dep.setType("extension");
        
        assertTrue(installer.supports(dep));
    }

    @Test
    void extensionInstaller_doesNotSupportLexType() {
        // Note: ExtensionDependencyInstaller only supports "extension" type, not "lex"
        ExtensionDependencyInstaller installer = new ExtensionDependencyInstaller(projectDir);
        DependencyConfig dep = new DependencyConfig();
        dep.setName("h2");
        dep.setType("lex");
        
        assertFalse(installer.supports(dep));
    }

    @Test
    void extensionInstaller_doesNotSupportCfmlType() {
        ExtensionDependencyInstaller installer = new ExtensionDependencyInstaller(projectDir);
        DependencyConfig dep = new DependencyConfig();
        dep.setName("my-framework");
        dep.setType("cfml");
        
        assertFalse(installer.supports(dep));
    }

    // ===================
    // LockedDependency Tests
    // ===================

    @Test
    void lockedDependency_storesVersion() {
        LockedDependency locked = new LockedDependency();
        locked.setVersion("1.2.3");
        
        assertEquals("1.2.3", locked.getVersion());
    }

    @Test
    void lockedDependency_storesResolved() {
        LockedDependency locked = new LockedDependency();
        locked.setResolved("https://github.com/org/repo#main");
        
        assertEquals("https://github.com/org/repo#main", locked.getResolved());
    }

    @Test
    void lockedDependency_storesIntegrity() {
        LockedDependency locked = new LockedDependency();
        locked.setIntegrity("sha512-abc123");
        
        assertEquals("sha512-abc123", locked.getIntegrity());
    }

    @Test
    void lockedDependency_storesSource() {
        LockedDependency locked = new LockedDependency();
        locked.setSource("git");
        
        assertEquals("git", locked.getSource());
    }

    @Test
    void lockedDependency_storesType() {
        LockedDependency locked = new LockedDependency();
        locked.setType("cfml");
        
        assertEquals("cfml", locked.getType());
    }

    @Test
    void lockedDependency_storesInstallPath() {
        LockedDependency locked = new LockedDependency();
        locked.setInstallPath("dependencies/my-dep");
        
        assertEquals("dependencies/my-dep", locked.getInstallPath());
    }

    @Test
    void lockedDependency_storesMapping() {
        LockedDependency locked = new LockedDependency();
        locked.setMapping("/myframework");
        
        assertEquals("/myframework", locked.getMapping());
    }

    @Test
    void lockedDependency_storesGitCommit() {
        LockedDependency locked = new LockedDependency();
        locked.setGitCommit("abc123def456");
        
        assertEquals("abc123def456", locked.getGitCommit());
    }

    @Test
    void lockedDependency_storesSubPath() {
        LockedDependency locked = new LockedDependency();
        locked.setSubPath("src/main");
        
        assertEquals("src/main", locked.getSubPath());
    }

    // ===================
    // ChecksumCalculator Tests
    // ===================

    @Test
    void checksumCalculator_producesConsistentHash() throws Exception {
        // Create a file with known content
        Path testFile = projectDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, World!");
        
        String hash1 = ChecksumCalculator.calculate(projectDir);
        String hash2 = ChecksumCalculator.calculate(projectDir);
        
        assertEquals(hash1, hash2);
    }

    @Test
    void checksumCalculator_differentContentDifferentHash() throws Exception {
        // Create first file
        Path file1 = projectDir.resolve("file1.txt");
        Files.writeString(file1, "Content A");
        String hash1 = ChecksumCalculator.calculate(projectDir);
        
        // Modify content
        Files.writeString(file1, "Content B");
        String hash2 = ChecksumCalculator.calculate(projectDir);
        
        assertNotEquals(hash1, hash2);
    }

    @Test
    void checksumCalculator_emptyDirectoryHandled() throws Exception {
        Path emptyDir = projectDir.resolve("empty");
        Files.createDirectories(emptyDir);
        
        // Should not throw
        String hash = ChecksumCalculator.calculate(emptyDir);
        assertNotNull(hash);
    }

    // ===================
    // Helper Methods
    // ===================

    private DependencyConfig createDependencyConfig(String name, String source) {
        DependencyConfig dep = new DependencyConfig();
        dep.setName(name);
        dep.setSource(source);
        dep.setInstallPath("dependencies/" + name);
        return dep;
    }
}
