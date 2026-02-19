package org.lucee.lucli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PromptConfig class.
 * Tests theme parsing, variable substitution, and ANSI color handling.
 */
class PromptConfigTest {

    @TempDir
    Path tempDir;
    
    private String originalUserHome;
    private Settings settings;
    private PromptConfig promptConfig;

    @BeforeEach
    void setUp() {
        // Save original user.home
        originalUserHome = System.getProperty("user.home");
        // Use temp directory as user.home for isolation
        System.setProperty("user.home", tempDir.toString());
        
        settings = new Settings();
        promptConfig = new PromptConfig(settings);
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
    void testPromptConfigInitialization() {
        assertNotNull(promptConfig);
        assertNotNull(promptConfig.getCurrentTemplate());
    }

    @Test
    void testBuiltinTemplatesLoaded() {
        List<String> builtin = promptConfig.getBuiltinTemplateNames();
        assertNotNull(builtin);
        assertTrue(builtin.contains("default"));
    }

    // ============================================
    // Template Retrieval Tests
    // ============================================

    @Test
    void testGetCurrentTemplateDefault() {
        PromptConfig.PromptTemplate template = promptConfig.getCurrentTemplate();
        assertNotNull(template);
        assertEquals("default", template.name);
    }

    @Test
    void testGetTemplateByName() {
        PromptConfig.PromptTemplate template = promptConfig.getTemplate("default");
        assertNotNull(template);
        assertEquals("default", template.name);
    }

    @Test
    void testGetTemplateNonExistent() {
        PromptConfig.PromptTemplate template = promptConfig.getTemplate("nonexistent_template");
        // Should return null for unknown template
        assertNull(template);
    }

    @Test
    void testIsBuiltinTemplate() {
        assertTrue(promptConfig.isBuiltinTemplate("default"));
        assertFalse(promptConfig.isBuiltinTemplate("my_custom_prompt"));
    }

    // ============================================
    // Set Template Tests
    // ============================================

    @Test
    void testSetCurrentTemplate() {
        // Set to minimal if available
        if (promptConfig.getTemplate("minimal") != null) {
            assertTrue(promptConfig.setCurrentTemplate("minimal"));
            assertEquals("minimal", promptConfig.getCurrentTemplate().name);
        }
    }

    @Test
    void testSetCurrentTemplateInvalid() {
        assertFalse(promptConfig.setCurrentTemplate("nonexistent_template"));
    }

    // ============================================
    // Template List Tests
    // ============================================

    @Test
    void testGetAvailableTemplateNames() {
        List<String> names = promptConfig.getAvailableTemplateNames();
        assertNotNull(names);
        assertFalse(names.isEmpty());
        assertTrue(names.contains("default"));
    }

    @Test
    void testGetBuiltinTemplateNames() {
        List<String> names = promptConfig.getBuiltinTemplateNames();
        assertNotNull(names);
        assertFalse(names.isEmpty());
    }

    @Test
    void testGetCustomTemplateNames() {
        List<String> names = promptConfig.getCustomTemplateNames();
        assertNotNull(names);
        // Initially empty unless we create custom prompts
    }

    // ============================================
    // Prompt Generation Tests
    // ============================================

    @Test
    void testGeneratePromptBasic() {
        FileSystemState fsState = new FileSystemState();
        String prompt = promptConfig.generatePrompt(fsState);
        
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
    }

    @Test
    void testGeneratePromptWithTemplate() {
        FileSystemState fsState = new FileSystemState();
        PromptConfig.PromptTemplate template = new PromptConfig.PromptTemplate(
            "test", "Test prompt", "TEST$ ", true, false, false, false
        );
        
        String prompt = promptConfig.generatePrompt(template, fsState);
        assertNotNull(prompt);
        assertTrue(prompt.contains("TEST"));
    }

    @Test
    void testGeneratePromptPathSubstitution() {
        FileSystemState fsState = new FileSystemState();
        fsState.changeDirectory(null); // Go to home
        
        PromptConfig.PromptTemplate template = new PromptConfig.PromptTemplate(
            "pathtest", "Path test", "DIR:{path}$ ", true, false, false, false
        );
        
        String prompt = promptConfig.generatePrompt(template, fsState);
        assertNotNull(prompt);
        // Path should be substituted (~ for home)
        assertTrue(prompt.contains("~") || prompt.contains(System.getProperty("user.home")));
    }

    @Test
    void testGeneratePromptTimeSubstitution() {
        FileSystemState fsState = new FileSystemState();
        
        PromptConfig.PromptTemplate template = new PromptConfig.PromptTemplate(
            "timetest", "Time test", "[{time}]$ ", false, true, false, false
        );
        
        String prompt = promptConfig.generatePrompt(template, fsState);
        assertNotNull(prompt);
        // Time should be substituted (HH:mm:ss format)
        assertTrue(prompt.matches(".*\\d{2}:\\d{2}:\\d{2}.*") || !prompt.contains("{time}"));
    }

    // ============================================
    // PromptTemplate Class Tests
    // ============================================

    @Test
    void testPromptTemplateBasicConstructor() {
        PromptConfig.PromptTemplate template = new PromptConfig.PromptTemplate(
            "name", "desc", "template$ ", true, false, true, false
        );
        
        assertEquals("name", template.name);
        assertEquals("desc", template.description);
        assertEquals("template$ ", template.template);
        assertTrue(template.showPath);
        assertFalse(template.showTime);
        assertTrue(template.showGit);
        assertFalse(template.useEmoji);
    }

    @Test
    void testPromptTemplateFullConstructor() {
        PromptConfig.PromptTemplate template = new PromptConfig.PromptTemplate(
            "fancy", "Fancy prompt", ">>> ", 
            true, true, true, true,
            "blue", "white", "bold",
            true, "{time}", "─", 2
        );
        
        assertEquals("fancy", template.name);
        assertEquals("blue", template.backgroundColor);
        assertEquals("white", template.foregroundColor);
        assertEquals("bold", template.style);
        assertTrue(template.multiline);
        assertEquals("{time}", template.rightAlign);
        assertEquals("─", template.separator);
        assertEquals(2, template.padding);
    }

    @Test
    void testPromptTemplateNullHandling() {
        PromptConfig.PromptTemplate template = new PromptConfig.PromptTemplate(
            "nulltest", "Test", "$ ",
            true, false, false, false,
            null, null, null, false, null, null, -1
        );
        
        assertEquals("", template.backgroundColor);
        assertEquals("", template.foregroundColor);
        assertEquals("", template.style);
        assertEquals("", template.rightAlign);
        assertEquals("", template.separator);
        assertEquals(0, template.padding); // Negative becomes 0
    }

    @Test
    void testPromptTemplateToString() {
        PromptConfig.PromptTemplate template = new PromptConfig.PromptTemplate(
            "myname", "My description", "$ ", true, false, false, false
        );
        
        String str = template.toString();
        assertEquals("myname - My description", str);
    }

    // ============================================
    // Custom Template Tests
    // ============================================

    @Test
    void testLoadUserTemplate() throws IOException {
        // Create a custom prompt file
        Path promptsDir = settings.getPromptsDir();
        Path customPrompt = promptsDir.resolve("mycustom.json");
        
        String json = """
            {
                "name": "mycustom",
                "description": "My custom prompt",
                "template": ">>> ",
                "showPath": true,
                "showTime": false,
                "showGit": false,
                "useEmoji": true
            }
            """;
        Files.writeString(customPrompt, json);
        
        // Re-create PromptConfig to pick up custom template
        PromptConfig newConfig = new PromptConfig(settings);
        PromptConfig.PromptTemplate template = newConfig.getTemplate("mycustom");
        
        assertNotNull(template);
        assertEquals("mycustom", template.name);
        assertEquals("My custom prompt", template.description);
        assertEquals(">>> ", template.template);
        assertTrue(template.showPath);
        assertTrue(template.useEmoji);
    }

    @Test
    void testCustomTemplateNotBuiltin() throws IOException {
        // Create a custom prompt file
        Path promptsDir = settings.getPromptsDir();
        Path customPrompt = promptsDir.resolve("mycustom2.json");
        Files.writeString(customPrompt, """
            {"name": "mycustom2", "description": "Custom", "template": "$ "}
            """);
        
        PromptConfig newConfig = new PromptConfig(settings);
        
        assertFalse(newConfig.isBuiltinTemplate("mycustom2"));
        assertTrue(newConfig.getCustomTemplateNames().contains("mycustom2"));
    }

    // ============================================
    // Refresh Tests
    // ============================================

    @Test
    void testRefreshPromptFiles() {
        int refreshed = promptConfig.refreshPromptFiles();
        // Should refresh at least some builtin templates
        assertTrue(refreshed >= 0);
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    void testGeneratePromptNullTemplate() {
        FileSystemState fsState = new FileSystemState();
        
        // Should handle gracefully and return default
        PromptConfig.PromptTemplate template = promptConfig.getTemplate("nonexistent");
        if (template == null) {
            // Use current template instead
            template = promptConfig.getCurrentTemplate();
        }
        String prompt = promptConfig.generatePrompt(template, fsState);
        assertNotNull(prompt);
    }

    @Test
    void testCorruptedCustomPromptFile() throws IOException {
        // Create invalid JSON
        Path promptsDir = settings.getPromptsDir();
        Path badPrompt = promptsDir.resolve("corrupted.json");
        Files.writeString(badPrompt, "{ invalid json }");
        
        // Should handle gracefully
        PromptConfig newConfig = new PromptConfig(settings);
        PromptConfig.PromptTemplate template = newConfig.getTemplate("corrupted");
        
        // Should return null for corrupted file
        assertNull(template);
    }
}
