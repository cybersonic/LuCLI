package org.lucee.lucli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages LuCLI settings stored in ~/.lucli/settings.json
 */
public class Settings {
    private static final String SETTINGS_DIR = ".lucli";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String PROMPTS_DIR = "prompts";
    
    private final Path settingsDir;
    private final Path settingsFile;
    private final Path promptsDir;
    private final ObjectMapper objectMapper;
    private JsonNode settings;
    
    public Settings() {
        Path homeDir = Paths.get(System.getProperty("user.home"));
        this.settingsDir = homeDir.resolve(SETTINGS_DIR);
        this.settingsFile = settingsDir.resolve(SETTINGS_FILE);
        this.promptsDir = settingsDir.resolve(PROMPTS_DIR);
        this.objectMapper = new ObjectMapper();
        
        initializeDirectories();
        loadSettings();
    }
    
    /**
     * Create necessary directories if they don't exist
     */
    private void initializeDirectories() {
        try {
            Files.createDirectories(settingsDir);
            Files.createDirectories(promptsDir);
        } catch (IOException e) {
            System.err.println("⚠️  Warning: Could not create settings directories: " + e.getMessage());
        }
    }
    
    /**
     * Load settings from file, create defaults if file doesn't exist
     */
    private void loadSettings() {
        try {
            if (Files.exists(settingsFile)) {
                settings = objectMapper.readTree(settingsFile.toFile());
            } else {
                // Create default settings
                settings = createDefaultSettings();
                saveSettings();
            }
        } catch (IOException e) {
            System.err.println("⚠️  Warning: Could not load settings, using defaults: " + e.getMessage());
            settings = createDefaultSettings();
        }
    }
    
    /**
     * Create default settings
     */
    private JsonNode createDefaultSettings() {
        ObjectNode defaultSettings = objectMapper.createObjectNode();
        defaultSettings.put("currentPrompt", "default");
        
        // Auto-detect emoji support based on terminal capabilities
        boolean emojiSupport = WindowsCompatibility.supportsEmojis();
        defaultSettings.put("showEmojis", emojiSupport);
        
        // Auto-detect color support
        boolean colorSupport = WindowsCompatibility.supportsColors();
        defaultSettings.put("colorSupport", colorSupport);
        
        defaultSettings.put("historySize", 1000);
        
        ObjectNode promptSettings = objectMapper.createObjectNode();
        promptSettings.put("showPath", true);
        promptSettings.put("showTime", false);
        promptSettings.put("showGit", false);
        promptSettings.put("useColors", true);
        
        defaultSettings.set("prompt", promptSettings);
        
        return defaultSettings;
    }
    
    /**
     * Save current settings to file
     */
    public void saveSettings() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(settingsFile.toFile(), settings);
        } catch (IOException e) {
            System.err.println("⚠️  Warning: Could not save settings: " + e.getMessage());
        }
    }
    
    /**
     * Get a string setting
     */
    public String getString(String key, String defaultValue) {
        JsonNode node = settings.path(key);
        return node.isMissingNode() ? defaultValue : node.asText();
    }
    
    /**
     * Get a boolean setting
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        JsonNode node = settings.path(key);
        return node.isMissingNode() ? defaultValue : node.asBoolean();
    }
    
    /**
     * Get an integer setting
     */
    public int getInt(String key, int defaultValue) {
        JsonNode node = settings.path(key);
        return node.isMissingNode() ? defaultValue : node.asInt();
    }
    
    /**
     * Set a string setting
     */
    public void setString(String key, String value) {
        if (settings instanceof ObjectNode) {
            ((ObjectNode) settings).put(key, value);
            saveSettings();
        }
    }
    
    /**
     * Set a boolean setting
     */
    public void setBoolean(String key, boolean value) {
        if (settings instanceof ObjectNode) {
            ((ObjectNode) settings).put(key, value);
            saveSettings();
        }
    }
    
    /**
     * Set an integer setting
     */
    public void setInt(String key, int value) {
        if (settings instanceof ObjectNode) {
            ((ObjectNode) settings).put(key, value);
            saveSettings();
        }
    }
    
    /**
     * Get nested setting
     */
    public JsonNode getNestedSetting(String... keys) {
        JsonNode current = settings;
        for (String key : keys) {
            current = current.path(key);
            if (current.isMissingNode()) {
                return current;
            }
        }
        return current;
    }
    
    /**
     * Set nested setting
     */
    public void setNestedSetting(String value, String... keys) {
        if (!(settings instanceof ObjectNode)) return;
        
        ObjectNode current = (ObjectNode) settings;
        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            if (!current.has(key) || !current.get(key).isObject()) {
                current.set(key, objectMapper.createObjectNode());
            }
            current = (ObjectNode) current.get(key);
        }
        current.put(keys[keys.length - 1], value);
        saveSettings();
    }
    
    /**
     * Get current prompt name
     */
    public String getCurrentPrompt() {
        return getString("currentPrompt", "default");
    }
    
    /**
     * Set current prompt name
     */
    public void setCurrentPrompt(String promptName) {
        setString("currentPrompt", promptName);
    }
    
    /**
     * Check if emojis are enabled
     */
    public boolean showEmojis() {
        return getBoolean("showEmojis", true);
    }
    
    /**
     * Set emoji display setting
     */
    public void setShowEmojis(boolean showEmojis) {
        setBoolean("showEmojis", showEmojis);
    }
    
    /**
     * Check if colors are supported/enabled
     */
    public boolean supportsColors() {
        return getBoolean("colorSupport", true);
    }
    
    /**
     * Get prompts directory path
     */
    public Path getPromptsDir() {
        return promptsDir;
    }
    
    /**
     * Get settings directory path
     */
    public Path getSettingsDir() {
        return settingsDir;
    }
    
    /**
     * Get all settings as a Map for display
     */
    public Map<String, Object> getAllSettings() {
        Map<String, Object> result = new HashMap<>();
        settings.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                result.put(entry.getKey(), value.asText());
            } else if (value.isBoolean()) {
                result.put(entry.getKey(), value.asBoolean());
            } else if (value.isInt()) {
                result.put(entry.getKey(), value.asInt());
            } else {
                result.put(entry.getKey(), value.toString());
            }
        });
        return result;
    }
}
