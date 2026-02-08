package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CommandProcessor
 * Tests command parsing, quote handling, and argument extraction
 */
public class CommandProcessorTest {

    private CommandProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CommandProcessor();
    }

    // ===================
    // Command Parsing Tests
    // ===================

    @Test
    void parseCommand_simpleCommand() {
        String[] result = processor.parseCommand("ls");
        
        assertEquals(1, result.length);
        assertEquals("ls", result[0]);
    }

    @Test
    void parseCommand_commandWithArgs() {
        String[] result = processor.parseCommand("ls -la /tmp");
        
        assertEquals(3, result.length);
        assertEquals("ls", result[0]);
        assertEquals("-la", result[1]);
        assertEquals("/tmp", result[2]);
    }

    @Test
    void parseCommand_doubleQuotedArgs() {
        String[] result = processor.parseCommand("echo \"hello world\"");
        
        assertEquals(2, result.length);
        assertEquals("echo", result[0]);
        assertEquals("hello world", result[1]);
    }

    @Test
    void parseCommand_singleQuotedArgs() {
        String[] result = processor.parseCommand("echo 'hello world'");
        
        assertEquals(2, result.length);
        assertEquals("echo", result[0]);
        assertEquals("hello world", result[1]);
    }

    @Test
    void parseCommand_mixedQuotedArgs() {
        String[] result = processor.parseCommand("cmd \"arg one\" 'arg two' three");
        
        assertEquals(4, result.length);
        assertEquals("cmd", result[0]);
        assertEquals("arg one", result[1]);
        assertEquals("arg two", result[2]);
        assertEquals("three", result[3]);
    }

    @Test
    void parseCommand_emptyQuotedString() {
        String[] result = processor.parseCommand("cmd \"\" arg");
        
        assertEquals(2, result.length);
        assertEquals("cmd", result[0]);
        assertEquals("arg", result[1]);
    }

    @Test
    void parseCommand_multipleSpaces() {
        String[] result = processor.parseCommand("cmd    arg1    arg2");
        
        assertEquals(3, result.length);
        assertEquals("cmd", result[0]);
        assertEquals("arg1", result[1]);
        assertEquals("arg2", result[2]);
    }

    @Test
    void parseCommand_emptyString() {
        String[] result = processor.parseCommand("");
        
        assertEquals(0, result.length);
    }

    @Test
    void parseCommand_onlySpaces() {
        String[] result = processor.parseCommand("     ");
        
        assertEquals(0, result.length);
    }

    @Test
    void parseCommand_pathWithSpaces() {
        String[] result = processor.parseCommand("cd \"/Users/mark/My Documents\"");
        
        assertEquals(2, result.length);
        assertEquals("cd", result[0]);
        assertEquals("/Users/mark/My Documents", result[1]);
    }

    @Test
    void parseCommand_quotedPathInMiddle() {
        String[] result = processor.parseCommand("cp \"/source path/file.txt\" \"/dest path/file.txt\"");
        
        assertEquals(3, result.length);
        assertEquals("cp", result[0]);
        assertEquals("/source path/file.txt", result[1]);
        assertEquals("/dest path/file.txt", result[2]);
    }

    @Test
    void parseCommand_flagsWithValues() {
        String[] result = processor.parseCommand("server start --port 8080 --name \"my server\"");
        
        assertEquals(6, result.length);
        assertEquals("server", result[0]);
        assertEquals("start", result[1]);
        assertEquals("--port", result[2]);
        assertEquals("8080", result[3]);
        assertEquals("--name", result[4]);
        assertEquals("my server", result[5]);
    }

    @Test
    void parseCommand_equalsInValue() {
        String[] result = processor.parseCommand("set \"key=value with spaces\"");
        
        assertEquals(2, result.length);
        assertEquals("set", result[0]);
        assertEquals("key=value with spaces", result[1]);
    }

    // ===================
    // File System State Tests
    // ===================

    @Test
    void getFileSystemState_notNull() {
        assertNotNull(processor.getFileSystemState());
    }

    @Test
    void getFileSystemState_hasCurrentDirectory() {
        assertNotNull(processor.getFileSystemState().getCurrentWorkingDirectory());
    }

    // ===================
    // Execute Command Tests
    // ===================

    @Test
    void executeCommand_emptyReturnsEmpty() {
        String result = processor.executeCommand("");
        assertEquals("", result);
    }

    @Test
    void executeCommand_nullReturnsEmpty() {
        String result = processor.executeCommand(null);
        assertEquals("", result);
    }

    @Test
    void executeCommand_whitespaceOnlyReturnsEmpty() {
        String result = processor.executeCommand("   ");
        assertEquals("", result);
    }

    @Test
    void executeCommand_pwdReturnsPath() {
        String result = processor.executeCommand("pwd");
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should return a valid path
        assertTrue(result.startsWith("/") || result.contains(":")); // Unix or Windows
    }

    @Test
    void executeCommand_unknownCommandReturnsError() {
        String result = processor.executeCommand("nonexistent_command_xyz");
        
        assertTrue(result.contains("Unknown command"));
    }

    // ===================
    // Settings Integration Tests
    // ===================

    @Test
    void getSettings_notNull() {
        assertNotNull(processor.getSettings());
    }

    @Test
    void getPromptConfig_notNull() {
        assertNotNull(processor.getPromptConfig());
    }
}
