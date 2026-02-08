package org.lucee.lucli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExternalCommandProcessor class.
 * Tests command parsing and internal routing (avoids slow external commands).
 */
class ExternalCommandProcessorTest {

    @TempDir
    Path tempDir;
    
    private String originalUserHome;
    private CommandProcessor commandProcessor;
    private ExternalCommandProcessor externalProcessor;
    private Settings settings;

    @BeforeEach
    void setUp() {
        // Save original user.home
        originalUserHome = System.getProperty("user.home");
        // Use temp directory as user.home for isolation
        System.setProperty("user.home", tempDir.toString());
        
        commandProcessor = new CommandProcessor();
        settings = new Settings();
        externalProcessor = new ExternalCommandProcessor(commandProcessor, settings);
    }

    @AfterEach
    void tearDown() {
        // Restore original user.home
        System.setProperty("user.home", originalUserHome);
    }

    // ============================================
    // Basic Command Execution Tests
    // ============================================

    @Test
    void testExecuteNullCommand() {
        String result = externalProcessor.executeCommand(null);
        assertEquals("", result);
    }

    @Test
    void testExecuteEmptyCommand() {
        String result = externalProcessor.executeCommand("");
        assertEquals("", result);
    }

    @Test
    void testExecuteWhitespaceCommand() {
        String result = externalProcessor.executeCommand("   ");
        assertEquals("", result);
    }

    // ============================================
    // Internal Command Routing Tests
    // ============================================

    @Test
    void testRoutesToInternalLs() {
        String result = externalProcessor.executeCommand("ls");
        // Should execute via internal processor
        // Make sure we are getting the right thing
        assertTrue(result.indexOf("src/", 0) > 0);
        assertNotNull(result);
    }

    @Test
    void testRoutesToInternalCd() {
        String result = externalProcessor.executeCommand("cd");
        assertNotNull(result);
    }

    @Test
    void testRoutesToInternalPwd() {
        String result = externalProcessor.executeCommand("pwd");
        assertNotNull(result);
        // Should return current directory
        assertFalse(result.isEmpty());
    }

    @Test
    void testRoutesToInternalMkdir() throws IOException {
        commandProcessor.getFileSystemState().changeDirectory(tempDir.toString());
        String result = externalProcessor.executeCommand("mkdir testdir");
        // Should attempt to create directory
        assertNotNull(result);
    }

    @Test
    void testRoutesToInternalCat() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World");
        commandProcessor.getFileSystemState().changeDirectory(tempDir.toString());
        
        String result = externalProcessor.executeCommand("cat test.txt");
        assertNotNull(result);
    }

    // ============================================
    // Working Directory Tests
    // ============================================

    @Test
    void testCommandExecutesInCurrentDirectory() throws IOException {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        
        commandProcessor.getFileSystemState().changeDirectory(subDir.toString());
        
        String result = externalProcessor.executeCommand("pwd");
        assertNotNull(result);
    }

    @Test
    void testCommandWithQuotedArgs() throws IOException {
        Path testFile = tempDir.resolve("test file.txt");
        Files.writeString(testFile, "content");
        commandProcessor.getFileSystemState().changeDirectory(tempDir.toString());
        
        String result = externalProcessor.executeCommand("cat \"test file.txt\"");
        assertNotNull(result);
    }
}
