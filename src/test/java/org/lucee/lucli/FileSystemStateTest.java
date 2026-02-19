package org.lucee.lucli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileSystemState class.
 * Tests path resolution, directory tracking, and tilde expansion.
 */
class FileSystemStateTest {

    private FileSystemState fsState;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fsState = new FileSystemState();
    }

    // ============================================
    // Constructor and Initial State Tests
    // ============================================

    @Test
    void testInitialWorkingDirectory() {
        Path cwd = fsState.getCurrentWorkingDirectory();
        assertNotNull(cwd);
        assertTrue(Files.exists(cwd));
        assertEquals(Path.of(System.getProperty("user.dir")).toAbsolutePath(), cwd);
    }

    @Test
    void testHomeDirectory() {
        Path home = fsState.getHomeDirectory();
        assertNotNull(home);
        assertEquals(Path.of(System.getProperty("user.home")).toAbsolutePath(), home);
    }

    // ============================================
    // changeDirectory Tests
    // ============================================

    @Test
    void testChangeDirectoryToNull() {
        // Null should navigate to home directory
        Path home = fsState.getHomeDirectory();
        assertTrue(fsState.changeDirectory(null));
        assertEquals(home, fsState.getCurrentWorkingDirectory());
    }

    @Test
    void testChangeDirectoryToEmpty() {
        // Empty string should navigate to home directory
        Path home = fsState.getHomeDirectory();
        assertTrue(fsState.changeDirectory(""));
        assertEquals(home, fsState.getCurrentWorkingDirectory());
    }

    @Test
    void testChangeDirectoryToExistingDirectory() throws IOException {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        
        assertTrue(fsState.changeDirectory(subDir.toString()));
        assertEquals(subDir.toRealPath(), fsState.getCurrentWorkingDirectory());
    }

    @Test
    void testChangeDirectoryToNonExistentDirectory() {
        assertFalse(fsState.changeDirectory("/non/existent/path/that/does/not/exist"));
    }

    @Test
    void testChangeDirectoryToFile() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.createFile(file);
        
        // Cannot cd to a file
        assertFalse(fsState.changeDirectory(file.toString()));
    }

    // ============================================
    // resolvePath Tests - Tilde Expansion
    // ============================================

    @Test
    void testResolvePathTilde() {
        Path resolved = fsState.resolvePath("~");
        assertEquals(fsState.getHomeDirectory(), resolved);
    }

    @Test
    void testResolvePathTildeSlash() {
        Path resolved = fsState.resolvePath("~/Documents");
        assertEquals(fsState.getHomeDirectory().resolve("Documents"), resolved);
    }

    @Test
    void testResolvePathTildeWithSubpath() {
        Path resolved = fsState.resolvePath("~/some/nested/path");
        assertEquals(fsState.getHomeDirectory().resolve("some/nested/path"), resolved);
    }

    // ============================================
    // resolvePath Tests - Dot Notation
    // ============================================

    @Test
    void testResolvePathCurrentDirectory() {
        Path cwd = fsState.getCurrentWorkingDirectory();
        Path resolved = fsState.resolvePath(".");
        assertEquals(cwd, resolved);
    }

    @Test
    void testResolvePathParentDirectory() {
        Path cwd = fsState.getCurrentWorkingDirectory();
        Path parent = cwd.getParent();
        
        Path resolved = fsState.resolvePath("..");
        
        if (parent != null) {
            assertEquals(parent, resolved);
        } else {
            // Root directory has no parent, should return current
            assertEquals(cwd, resolved);
        }
    }

    // ============================================
    // resolvePath Tests - Relative and Absolute Paths
    // ============================================

    @Test
    void testResolvePathNullOrEmpty() {
        Path cwd = fsState.getCurrentWorkingDirectory();
        
        assertEquals(cwd, fsState.resolvePath(null));
        assertEquals(cwd, fsState.resolvePath(""));
        assertEquals(cwd, fsState.resolvePath("   "));
    }

    @Test
    void testResolvePathRelative() throws IOException {
        // First cd to temp directory
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        fsState.changeDirectory(tempDir.toString());
        
        Path resolved = fsState.resolvePath("subdir");
        // Use toRealPath on expected to handle symlinks (e.g., /var -> /private/var on macOS)
        assertEquals(subDir.toRealPath().normalize(), resolved);
    }

    @Test
    void testResolvePathAbsolute() {
        Path resolved = fsState.resolvePath(tempDir.toString());
        assertEquals(tempDir.normalize(), resolved);
    }

    @Test
    void testResolvePathNormalization() throws IOException {
        // First cd to temp directory
        fsState.changeDirectory(tempDir.toString());
        Path subDir = tempDir.resolve("sub");
        Files.createDirectory(subDir);
        
        // Path with .. should normalize
        Path resolved = fsState.resolvePath("sub/../sub");
        // Use toRealPath on expected to handle symlinks (e.g., /var -> /private/var on macOS)
        assertEquals(subDir.toRealPath().normalize(), resolved);
    }

    // ============================================
    // getDisplayPath Tests
    // ============================================

    @Test
    void testGetDisplayPathHome() {
        fsState.changeDirectory(null); // Go to home
        assertEquals("~", fsState.getDisplayPath());
    }

    @Test
    void testGetDisplayPathHomeSubdir() throws IOException {
        Path homeSubdir = fsState.getHomeDirectory().resolve("Documents");
        if (Files.exists(homeSubdir)) {
            fsState.changeDirectory(homeSubdir.toString());
            assertEquals("~/Documents", fsState.getDisplayPath());
        }
    }

    @Test
    void testGetDisplayPathOutsideHome() throws IOException {
        // tempDir is typically not under home
        fsState.changeDirectory(tempDir.toString());
        
        // If tempDir starts with home, it will be ~/...
        // Otherwise it's the full path
        String display = fsState.getDisplayPath();
        assertNotNull(display);
        assertFalse(display.isEmpty());
    }

    // ============================================
    // exists Tests
    // ============================================

    @Test
    void testExistsFile() throws IOException {
        Path file = tempDir.resolve("exists.txt");
        Files.createFile(file);
        
        fsState.changeDirectory(tempDir.toString());
        assertTrue(fsState.exists("exists.txt"));
    }

    @Test
    void testExistsDirectory() throws IOException {
        Path dir = tempDir.resolve("existsdir");
        Files.createDirectory(dir);
        
        fsState.changeDirectory(tempDir.toString());
        assertTrue(fsState.exists("existsdir"));
    }

    @Test
    void testExistsNonExistent() throws IOException {
        fsState.changeDirectory(tempDir.toString());
        assertFalse(fsState.exists("nonexistent"));
    }

    // ============================================
    // isDirectory Tests
    // ============================================

    @Test
    void testIsDirectoryTrue() throws IOException {
        Path dir = tempDir.resolve("mydir");
        Files.createDirectory(dir);
        
        fsState.changeDirectory(tempDir.toString());
        assertTrue(fsState.isDirectory("mydir"));
    }

    @Test
    void testIsDirectoryFalseForFile() throws IOException {
        Path file = tempDir.resolve("myfile.txt");
        Files.createFile(file);
        
        fsState.changeDirectory(tempDir.toString());
        assertFalse(fsState.isDirectory("myfile.txt"));
    }

    @Test
    void testIsDirectoryFalseForNonExistent() throws IOException {
        fsState.changeDirectory(tempDir.toString());
        assertFalse(fsState.isDirectory("nonexistent"));
    }

    // ============================================
    // isRegularFile Tests
    // ============================================

    @Test
    void testIsRegularFileTrue() throws IOException {
        Path file = tempDir.resolve("regular.txt");
        Files.createFile(file);
        
        fsState.changeDirectory(tempDir.toString());
        assertTrue(fsState.isRegularFile("regular.txt"));
    }

    @Test
    void testIsRegularFileFalseForDirectory() throws IOException {
        Path dir = tempDir.resolve("regulardir");
        Files.createDirectory(dir);
        
        fsState.changeDirectory(tempDir.toString());
        assertFalse(fsState.isRegularFile("regulardir"));
    }

    @Test
    void testIsRegularFileFalseForNonExistent() throws IOException {
        fsState.changeDirectory(tempDir.toString());
        assertFalse(fsState.isRegularFile("nonexistent"));
    }

    // ============================================
    // reset Tests
    // ============================================

    @Test
    void testReset() throws IOException {
        Path initial = fsState.getCurrentWorkingDirectory();
        
        // Change to a different directory
        fsState.changeDirectory(tempDir.toString());
        assertNotEquals(initial, fsState.getCurrentWorkingDirectory());
        
        // Reset should restore initial directory
        fsState.reset();
        assertEquals(initial, fsState.getCurrentWorkingDirectory());
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    void testWhitespaceHandling() {
        Path resolved = fsState.resolvePath("  ~  ");
        assertEquals(fsState.getHomeDirectory(), resolved);
    }

    @Test
    void testNestedTildeNotSpecial() {
        // Tilde not at start should not be expanded
        Path resolved = fsState.resolvePath("folder~name");
        Path cwd = fsState.getCurrentWorkingDirectory();
        assertEquals(cwd.resolve("folder~name").normalize(), resolved);
    }
}
