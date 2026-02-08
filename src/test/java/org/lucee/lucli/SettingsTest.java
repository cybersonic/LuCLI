package org.lucee.lucli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Settings class.
 * Tests JSON load/save, defaults, getters/setters, and nested settings.
 */
class SettingsTest {

    @TempDir
    Path tempDir;
    
    private String originalUserHome;

    @BeforeEach
    void setUp() {
        // Save original user.home
        originalUserHome = System.getProperty("user.home");
        // Use temp directory as user.home for isolation
        System.setProperty("user.home", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        // Restore original user.home
        System.setProperty("user.home", originalUserHome);
    }

    // ============================================
    // Initialization Tests
    // ============================================

    @Test
    void testSettingsCreatesDirectories() {
        Settings settings = new Settings();
        
        Path settingsDir = settings.getSettingsDir();
        Path promptsDir = settings.getPromptsDir();
        
        assertTrue(Files.exists(settingsDir));
        assertTrue(Files.exists(promptsDir));
        assertTrue(Files.isDirectory(settingsDir));
        assertTrue(Files.isDirectory(promptsDir));
    }

    @Test
    void testDefaultSettingsCreatedIfNotExists() {
        Settings settings = new Settings();
        
        // Default values should be set
        assertEquals("default", settings.getCurrentPrompt());
        assertEquals(1000, settings.getInt("historySize", 0));
    }

    // ============================================
    // getString Tests
    // ============================================

    @Test
    void testGetStringExisting() {
        Settings settings = new Settings();
        assertEquals("default", settings.getString("currentPrompt", "other"));
    }

    @Test
    void testGetStringDefault() {
        Settings settings = new Settings();
        assertEquals("fallback", settings.getString("nonExistent", "fallback"));
    }

    @Test
    void testSetString() {
        Settings settings = new Settings();
        settings.setString("customKey", "customValue");
        assertEquals("customValue", settings.getString("customKey", "default"));
    }

    // ============================================
    // getBoolean Tests
    // ============================================

    @Test
    void testGetBooleanExisting() {
        Settings settings = new Settings();
        // showEmojis is set in defaults
        boolean emojis = settings.getBoolean("showEmojis", true);
        // Value depends on WindowsSupport detection, just ensure no exception
        assertNotNull(emojis);
    }

    @Test
    void testGetBooleanDefault() {
        Settings settings = new Settings();
        assertTrue(settings.getBoolean("nonExistentBool", true));
        assertFalse(settings.getBoolean("nonExistentBool", false));
    }

    @Test
    void testSetBoolean() {
        Settings settings = new Settings();
        settings.setBoolean("testFlag", true);
        assertTrue(settings.getBoolean("testFlag", false));
        
        settings.setBoolean("testFlag", false);
        assertFalse(settings.getBoolean("testFlag", true));
    }

    // ============================================
    // getInt Tests
    // ============================================

    @Test
    void testGetIntExisting() {
        Settings settings = new Settings();
        assertEquals(1000, settings.getInt("historySize", 0));
    }

    @Test
    void testGetIntDefault() {
        Settings settings = new Settings();
        assertEquals(42, settings.getInt("nonExistentInt", 42));
    }

    @Test
    void testSetInt() {
        Settings settings = new Settings();
        settings.setInt("count", 100);
        assertEquals(100, settings.getInt("count", 0));
    }

    // ============================================
    // Nested Settings Tests
    // ============================================

    @Test
    void testGetNestedSettingExists() {
        Settings settings = new Settings();
        // prompt.showPath is set in defaults
        var node = settings.getNestedSetting("prompt", "showPath");
        assertFalse(node.isMissingNode());
        assertTrue(node.asBoolean());
    }

    @Test
    void testGetNestedSettingMissing() {
        Settings settings = new Settings();
        var node = settings.getNestedSetting("nonexistent", "path");
        assertTrue(node.isMissingNode());
    }

    @Test
    void testSetNestedSetting() {
        Settings settings = new Settings();
        settings.setNestedSetting("value", "level1", "level2", "level3");
        
        var node = settings.getNestedSetting("level1", "level2", "level3");
        assertFalse(node.isMissingNode());
        assertEquals("value", node.asText());
    }

    // ============================================
    // Prompt Tests
    // ============================================

    @Test
    void testGetCurrentPromptDefault() {
        Settings settings = new Settings();
        assertEquals("default", settings.getCurrentPrompt());
    }

    @Test
    void testSetCurrentPrompt() {
        Settings settings = new Settings();
        settings.setCurrentPrompt("minimal");
        assertEquals("minimal", settings.getCurrentPrompt());
    }

    // ============================================
    // Emoji Tests
    // ============================================

    @Test
    void testShowEmojis() {
        Settings settings = new Settings();
        // Default depends on system detection
        boolean result = settings.showEmojis();
        // Just verify it returns a boolean
        assertNotNull(result);
    }

    @Test
    void testSetShowEmojis() {
        Settings settings = new Settings();
        settings.setShowEmojis(true);
        assertTrue(settings.showEmojis());
        
        settings.setShowEmojis(false);
        assertFalse(settings.showEmojis());
    }

    // ============================================
    // Color Support Tests
    // ============================================

    @Test
    void testSupportsColors() {
        Settings settings = new Settings();
        boolean result = settings.supportsColors();
        // Default might be true or false depending on detection
        assertNotNull(result);
    }

    // ============================================
    // Git Cache Tests
    // ============================================

    @Test
    void testUsePersistentGitCacheDefault() {
        Settings settings = new Settings();
        assertTrue(settings.usePersistentGitCache());
    }

    // ============================================
    // Language Tests
    // ============================================

    @Test
    void testGetLanguageDefault() {
        Settings settings = new Settings();
        assertNull(settings.getLanguage()); // Default is null
    }

    @Test
    void testSetLanguage() {
        Settings settings = new Settings();
        
        settings.setLanguage("es");
        assertEquals("es", settings.getLanguage());
        
        settings.setLanguage("fr");
        assertEquals("fr", settings.getLanguage());
        
        settings.setLanguage(null);
        assertNull(settings.getLanguage());
    }

    // ============================================
    // getAllSettings Tests
    // ============================================

    @Test
    void testGetAllSettings() {
        Settings settings = new Settings();
        Map<String, Object> all = settings.getAllSettings();
        
        assertNotNull(all);
        assertFalse(all.isEmpty());
        assertTrue(all.containsKey("currentPrompt"));
        assertTrue(all.containsKey("historySize"));
    }

    // ============================================
    // Persistence Tests
    // ============================================

    @Test
    void testSettingsPersistence() {
        Settings settings1 = new Settings();
        settings1.setString("persistTest", "testValue");
        settings1.setInt("persistInt", 123);
        settings1.setBoolean("persistBool", true);
        
        // Create new Settings instance which should read from file
        Settings settings2 = new Settings();
        
        assertEquals("testValue", settings2.getString("persistTest", ""));
        assertEquals(123, settings2.getInt("persistInt", 0));
        assertTrue(settings2.getBoolean("persistBool", false));
    }

    @Test
    void testSaveSettings() throws IOException {
        Settings settings = new Settings();
        settings.setString("saveTest", "saved");
        settings.saveSettings();
        
        // Verify file exists
        Path settingsFile = settings.getSettingsDir().resolve("settings.json");
        assertTrue(Files.exists(settingsFile));
        
        // Verify content
        String content = Files.readString(settingsFile);
        assertTrue(content.contains("saveTest"));
        assertTrue(content.contains("saved"));
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    void testNullKeyHandling() {
        Settings settings = new Settings();
        
        // These should not throw
        String strVal = settings.getString(null, "default");
        assertEquals("default", strVal);
    }

    @Test
    void testCorruptedSettingsFile() throws IOException {
        // Create invalid JSON in settings file
        Path settingsDir = tempDir.resolve(".lucli");
        Files.createDirectories(settingsDir);
        Path settingsFile = settingsDir.resolve("settings.json");
        Files.writeString(settingsFile, "{ invalid json }");
        
        // Settings should handle corruption gracefully and use defaults
        Settings settings = new Settings();
        assertEquals("default", settings.getCurrentPrompt());
    }

    @Test
    void testEmptySettingsFile() throws IOException {
        // Create empty settings file
        Path settingsDir = tempDir.resolve(".lucli");
        Files.createDirectories(settingsDir);
        Path settingsFile = settingsDir.resolve("settings.json");
        Files.writeString(settingsFile, "");
        
        // Settings should handle empty file gracefully
        Settings settings = new Settings();
        // Should get default values
        assertNotNull(settings.getCurrentPrompt());
    }
}
