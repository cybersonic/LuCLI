package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for StringOutput
 * Tests placeholder substitution, emoji handling, and output processing
 */
public class StringOutputTest {

    private StringOutput stringOutput;

    @BeforeEach
    void setUp() {
        stringOutput = StringOutput.getInstance();
    }

    // ===================
    // Singleton Tests
    // ===================

    @Test
    void getInstance_returnsSameInstance() {
        StringOutput first = StringOutput.getInstance();
        StringOutput second = StringOutput.getInstance();
        
        assertSame(first, second);
    }

    // ===================
    // Basic Processing Tests
    // ===================

    @Test
    void process_nullReturnsNull() {
        assertNull(stringOutput.process(null));
    }

    @Test
    void process_emptyReturnsEmpty() {
        assertEquals("", stringOutput.process(""));
    }

    @Test
    void process_plainTextUnchanged() {
        String input = "Hello, World!";
        assertEquals(input, stringOutput.process(input));
    }

    // ===================
    // System Placeholder Tests
    // ===================

    @Test
    void process_userHomeReplaced() {
        String result = stringOutput.process("Home: ${USER_HOME}");
        
        assertNotNull(result);
        assertFalse(result.contains("${USER_HOME}"));
        assertTrue(result.contains(System.getProperty("user.home")));
    }

    @Test
    void process_userNameReplaced() {
        String result = stringOutput.process("User: ${USER_NAME}");
        
        assertNotNull(result);
        assertFalse(result.contains("${USER_NAME}"));
        assertTrue(result.contains(System.getProperty("user.name")));
    }

    @Test
    void process_workingDirReplaced() {
        String result = stringOutput.process("Dir: ${WORKING_DIR}");
        
        assertNotNull(result);
        assertFalse(result.contains("${WORKING_DIR}"));
        assertTrue(result.contains(System.getProperty("user.dir")));
    }

    @Test
    void process_osNameReplaced() {
        String result = stringOutput.process("OS: ${OS_NAME}");
        
        assertNotNull(result);
        assertFalse(result.contains("${OS_NAME}"));
        assertTrue(result.contains(System.getProperty("os.name")));
    }

    @Test
    void process_javaVersionReplaced() {
        String result = stringOutput.process("Java: ${JAVA_VERSION}");
        
        assertNotNull(result);
        assertFalse(result.contains("${JAVA_VERSION}"));
        assertTrue(result.contains(System.getProperty("java.version")));
    }

    // ===================
    // Time Placeholder Tests
    // ===================

    @Test
    void process_nowReplaced() {
        String result = stringOutput.process("Time: ${NOW}");
        
        assertNotNull(result);
        assertFalse(result.contains("${NOW}"));
        // Should contain a date-like string
        assertTrue(result.matches(".*\\d{4}-\\d{2}-\\d{2}.*"));
    }

    @Test
    void process_dateReplaced() {
        String result = stringOutput.process("Date: ${DATE}");
        
        assertNotNull(result);
        assertFalse(result.contains("${DATE}"));
        // Should be a date in ISO format
        assertTrue(result.matches(".*\\d{4}-\\d{2}-\\d{2}.*"));
    }

    @Test
    void process_timestampReplaced() {
        String result = stringOutput.process("TS: ${TIMESTAMP}");
        
        assertNotNull(result);
        assertFalse(result.contains("${TIMESTAMP}"));
        // Should contain a numeric timestamp
        assertTrue(result.matches(".*\\d{10,}.*"));
    }

    // ===================
    // LuCLI Placeholder Tests
    // ===================

    @Test
    void process_lucliVersionReplaced() {
        String result = stringOutput.process("Version: ${LUCLI_VERSION}");
        
        assertNotNull(result);
        assertFalse(result.contains("${LUCLI_VERSION}"));
    }

    @Test
    void process_lucliHomeReplaced() {
        String result = stringOutput.process("Home: ${LUCLI_HOME}");
        
        assertNotNull(result);
        assertFalse(result.contains("${LUCLI_HOME}"));
        assertTrue(result.contains(".lucli") || result.contains("lucli"));
    }

    // ===================
    // Emoji Placeholder Tests
    // ===================

    @Test
    void process_emojiSuccessReplaced() {
        String result = stringOutput.process("Status: ${EMOJI_SUCCESS}");
        
        assertNotNull(result);
        assertFalse(result.contains("${EMOJI_SUCCESS}"));
        // Should contain either emoji or fallback text
        assertTrue(result.contains("✅") || result.contains("[OK]"));
    }

    @Test
    void process_emojiErrorReplaced() {
        String result = stringOutput.process("Status: ${EMOJI_ERROR}");
        
        assertNotNull(result);
        assertFalse(result.contains("${EMOJI_ERROR}"));
        assertTrue(result.contains("❌") || result.contains("[ERROR]"));
    }

    @Test
    void process_emojiWarningReplaced() {
        String result = stringOutput.process("Status: ${EMOJI_WARNING}");
        
        assertNotNull(result);
        assertFalse(result.contains("${EMOJI_WARNING}"));
        assertTrue(result.contains("⚠") || result.contains("[WARNING]"));
    }

    @Test
    void process_emojiInfoReplaced() {
        String result = stringOutput.process("Status: ${EMOJI_INFO}");
        
        assertNotNull(result);
        assertFalse(result.contains("${EMOJI_INFO}"));
        assertTrue(result.contains("ℹ") || result.contains("[INFO]"));
    }

    // ===================
    // Environment Variable Tests
    // ===================

    @Test
    void process_envPathReplaced() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String result = stringOutput.process("Path: ${ENV_PATH}");
            
            assertNotNull(result);
            assertFalse(result.contains("${ENV_PATH}"));
        }
    }

    @Test
    void process_directEnvVarFallback() {
        // If HOME is set in environment, it should be replaced
        String homeEnv = System.getenv("HOME");
        if (homeEnv != null) {
            String result = stringOutput.process("Home: ${HOME}");
            
            assertNotNull(result);
            // Should be replaced with the actual value
            assertTrue(result.contains(homeEnv));
        }
    }

    // ===================
    // Multiple Placeholder Tests
    // ===================

    @Test
    void process_multiplePlaceholders() {
        String result = stringOutput.process("User ${USER_NAME} on ${OS_NAME}");
        
        assertNotNull(result);
        assertFalse(result.contains("${USER_NAME}"));
        assertFalse(result.contains("${OS_NAME}"));
        assertTrue(result.contains(System.getProperty("user.name")));
        assertTrue(result.contains(System.getProperty("os.name")));
    }

    @Test
    void process_mixedTextAndPlaceholders() {
        String result = stringOutput.process("Hello ${USER_NAME}! Welcome to LuCLI.");
        
        assertNotNull(result);
        assertTrue(result.startsWith("Hello "));
        assertTrue(result.endsWith("! Welcome to LuCLI."));
        assertTrue(result.contains(System.getProperty("user.name")));
    }

    // ===================
    // Unknown Placeholder Tests
    // ===================

    @Test
    void process_unknownPlaceholderPreserved() {
        String result = stringOutput.process("Value: ${UNKNOWN_PLACEHOLDER_XYZ}");
        
        assertNotNull(result);
        // Unknown placeholders should be preserved
        assertTrue(result.contains("${UNKNOWN_PLACEHOLDER_XYZ}"));
    }

    // ===================
    // Custom Placeholder Tests
    // ===================

    @Test
    void addPlaceholder_staticValue() {
        stringOutput.addPlaceholder("CUSTOM_TEST", "custom-value");
        
        String result = stringOutput.process("Custom: ${CUSTOM_TEST}");
        
        assertEquals("Custom: custom-value", result);
        
        // Cleanup
        stringOutput.removePlaceholder("CUSTOM_TEST");
    }

    @Test
    void addPlaceholder_dynamicSupplier() {
        stringOutput.addPlaceholder("COUNTER", () -> String.valueOf(System.currentTimeMillis()));
        
        String result1 = stringOutput.process("Time: ${COUNTER}");
        
        assertNotNull(result1);
        assertFalse(result1.contains("${COUNTER}"));
        
        // Cleanup
        stringOutput.removePlaceholder("COUNTER");
    }

    @Test
    void removePlaceholder_works() {
        stringOutput.addPlaceholder("TEMP_TEST", "temp-value");
        
        // Verify it works
        assertEquals("Value: temp-value", stringOutput.process("Value: ${TEMP_TEST}"));
        
        // Remove it
        stringOutput.removePlaceholder("TEMP_TEST");
        
        // Should no longer be replaced (will fall back to env lookup, which won't find it)
        String result = stringOutput.process("Value: ${TEMP_TEST}");
        assertTrue(result.contains("${TEMP_TEST}"));
    }

    // ===================
    // Edge Cases
    // ===================

    @Test
    void process_nestedBracesHandled() {
        // Nested braces shouldn't break parsing
        String result = stringOutput.process("Code: { ${USER_NAME} }");
        
        assertNotNull(result);
        assertTrue(result.contains(System.getProperty("user.name")));
    }

    @Test
    void process_incompletePlaceholderPreserved() {
        // Incomplete placeholder syntax should be preserved
        String result = stringOutput.process("Incomplete: ${MISSING");
        
        assertEquals("Incomplete: ${MISSING", result);
    }

    @Test
    void process_escapedDollarSign() {
        // Just a dollar sign without braces
        String result = stringOutput.process("Cost: $100");
        
        assertEquals("Cost: $100", result);
    }
}
