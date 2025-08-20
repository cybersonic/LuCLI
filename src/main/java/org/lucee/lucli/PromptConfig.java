package org.lucee.lucli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages prompt configurations and templates
 */
public class PromptConfig {
    private final Settings settings;
    private final ObjectMapper objectMapper;
    private final Map<String, PromptTemplate> builtinTemplates;
    
    public PromptConfig(Settings settings) {
        this.settings = settings;
        this.objectMapper = new ObjectMapper();
        this.builtinTemplates = new HashMap<>();
        
        initializeBuiltinTemplates();
        createDefaultPromptFiles();
    }
    
    /**
     * Initialize built-in prompt templates from JSON resources
     */
    private void initializeBuiltinTemplates() {
        String[] builtinPromptNames = {
            "default", "minimal", "zsh", "time", "colorful", "dev", "space", "electric"
        };
        
        for (String promptName : builtinPromptNames) {
            PromptTemplate template = loadBuiltinTemplate(promptName);
            if (template != null) {
                builtinTemplates.put(promptName, template);
            }
        }
        
        // Ensure we have at least a default prompt as fallback
        if (!builtinTemplates.containsKey("default")) {
            builtinTemplates.put("default", new PromptTemplate(
                "default",
                "Default LuCLI prompt",
                "🔧 lucli:{path}$ ",
                true, false, false, true
            ));
        }
    }
    
    /**
     * Load a built-in template from JSON resources
     */
    private PromptTemplate loadBuiltinTemplate(String name) {
        String resourcePath = "/prompts/" + name + ".json";
        
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                System.err.println("⚠️  Warning: Built-in prompt template " + name + " not found in resources");
                return null;
            }
            
            JsonNode promptJson = objectMapper.readTree(inputStream);
            return new PromptTemplate(
                promptJson.path("name").asText(name),
                promptJson.path("description").asText("Built-in prompt"),
                promptJson.path("template").asText("$ "),
                promptJson.path("showPath").asBoolean(true),
                promptJson.path("showTime").asBoolean(false),
                promptJson.path("showGit").asBoolean(false),
                promptJson.path("useEmoji").asBoolean(false)
            );
        } catch (IOException e) {
            System.err.println("⚠️  Warning: Could not load built-in prompt template " + name + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Create default prompt template files if they don't exist
     */
    private void createDefaultPromptFiles() {
        Path promptsDir = settings.getPromptsDir();
        
        for (PromptTemplate template : builtinTemplates.values()) {
            Path promptFile = promptsDir.resolve(template.name + ".json");
            if (!Files.exists(promptFile)) {
                try {
                    ObjectNode promptJson = objectMapper.createObjectNode();
                    promptJson.put("name", template.name);
                    promptJson.put("description", template.description);
                    promptJson.put("template", template.template);
                    promptJson.put("showPath", template.showPath);
                    promptJson.put("showTime", template.showTime);
                    promptJson.put("showGit", template.showGit);
                    promptJson.put("useEmoji", template.useEmoji);
                    
                    objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValue(promptFile.toFile(), promptJson);
                } catch (IOException e) {
                    System.err.println("⚠️  Warning: Could not create prompt file " + template.name + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Get current prompt template
     */
    public PromptTemplate getCurrentTemplate() {
        String currentPrompt = settings.getCurrentPrompt();
        PromptTemplate template = getTemplate(currentPrompt);
        return template != null ? template : builtinTemplates.get("default");
    }
    
    /**
     * Get a specific template by name
     */
    public PromptTemplate getTemplate(String name) {
        // Try builtin templates first
        if (builtinTemplates.containsKey(name)) {
            return builtinTemplates.get(name);
        }
        
        // Try loading from user's custom file
        return loadUserTemplate(name);
    }
    
    /**
     * Load a user-defined template from the ~/.lucli/prompts/ directory
     */
    private PromptTemplate loadUserTemplate(String name) {
        Path promptFile = settings.getPromptsDir().resolve(name + ".json");
        if (Files.exists(promptFile)) {
            try {
                JsonNode promptJson = objectMapper.readTree(promptFile.toFile());
                return new PromptTemplate(
                    promptJson.path("name").asText(name),
                    promptJson.path("description").asText("Custom prompt"),
                    promptJson.path("template").asText("$ "),
                    promptJson.path("showPath").asBoolean(true),
                    promptJson.path("showTime").asBoolean(false),
                    promptJson.path("showGit").asBoolean(false),
                    promptJson.path("useEmoji").asBoolean(false)
                );
            } catch (IOException e) {
                System.err.println("⚠️  Warning: Could not load prompt template " + name + ": " + e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Check if a template is a built-in template
     */
    public boolean isBuiltinTemplate(String name) {
        return builtinTemplates.containsKey(name);
    }
    
    /**
     * Get only built-in template names
     */
    public List<String> getBuiltinTemplateNames() {
        List<String> names = new ArrayList<>(builtinTemplates.keySet());
        Collections.sort(names);
        return names;
    }
    
    /**
     * Get only user-defined (custom) template names
     */
    public List<String> getCustomTemplateNames() {
        List<String> customNames = new ArrayList<>();
        Path promptsDir = settings.getPromptsDir();
        
        if (Files.exists(promptsDir)) {
            try (Stream<Path> files = Files.list(promptsDir)) {
                files.filter(path -> path.toString().endsWith(".json"))
                     .map(path -> path.getFileName().toString())
                     .map(filename -> filename.substring(0, filename.length() - 5)) // Remove .json
                     .filter(name -> !builtinTemplates.containsKey(name)) // Exclude built-in templates
                     .forEach(customNames::add);
            } catch (IOException e) {
                System.err.println("⚠️  Warning: Could not list custom prompt templates: " + e.getMessage());
            }
        }
        
        Collections.sort(customNames);
        return customNames;
    }
    
    /**
     * Get all available template names
     */
    public List<String> getAvailableTemplateNames() {
        Set<String> names = new HashSet<>(builtinTemplates.keySet());
        
        // Add custom templates from files
        Path promptsDir = settings.getPromptsDir();
        if (Files.exists(promptsDir)) {
            try (Stream<Path> files = Files.list(promptsDir)) {
                files.filter(path -> path.toString().endsWith(".json"))
                     .map(path -> path.getFileName().toString())
                     .map(filename -> filename.substring(0, filename.length() - 5)) // Remove .json
                     .forEach(names::add);
            } catch (IOException e) {
                System.err.println("⚠️  Warning: Could not list prompt templates: " + e.getMessage());
            }
        }
        
        List<String> sortedNames = new ArrayList<>(names);
        Collections.sort(sortedNames);
        return sortedNames;
    }
    
    /**
     * Set the current prompt template
     */
    public boolean setCurrentTemplate(String templateName) {
        PromptTemplate template = getTemplate(templateName);
        if (template != null) {
            settings.setCurrentPrompt(templateName);
            return true;
        }
        return false;
    }
    
    /**
     * Generate prompt string for current context
     */
    public String generatePrompt(FileSystemState fileSystemState) {
        PromptTemplate template = getCurrentTemplate();
        return generatePrompt(template, fileSystemState);
    }
    
    /**
     * Generate prompt string for specific template and context
     */
    public String generatePrompt(PromptTemplate template, FileSystemState fileSystemState) {
        String prompt = template.template;
        
        // Replace path placeholder
        if (template.showPath && prompt.contains("{path}")) {
            String path = fileSystemState.getDisplayPath();
            prompt = prompt.replace("{path}", path);
        }
        
        // Replace time placeholder
        if (template.showTime && prompt.contains("{time}")) {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            prompt = prompt.replace("{time}", time);
        }
        
        // Replace git placeholder (simplified - just show if we're in a git repo)
        if (template.showGit && prompt.contains("{git}")) {
            String gitInfo = getGitInfo(fileSystemState.getCurrentWorkingDirectory());
            prompt = prompt.replace("{git}", gitInfo);
        }
        
        // Remove emoji if disabled in settings
        if (!settings.showEmojis() && template.useEmoji) {
            prompt = removeEmojis(prompt);
        }
        
        return prompt;
    }
    
    /**
     * Simple git branch detection
     */
    private String getGitInfo(Path directory) {
        Path gitDir = directory.resolve(".git");
        Path current = directory;
        
        // Walk up the directory tree looking for .git
        while (current != null && !Files.exists(current.resolve(".git"))) {
            current = current.getParent();
        }
        
        if (current != null && Files.exists(current.resolve(".git"))) {
            try {
                Path headFile = current.resolve(".git/HEAD");
                if (Files.exists(headFile)) {
                    String head = Files.readString(headFile).trim();
                    if (head.startsWith("ref: refs/heads/")) {
                        String branch = head.substring("ref: refs/heads/".length());
                        return "[" + branch + "] ";
                    }
                }
            } catch (IOException e) {
                // Ignore git reading errors
            }
            return "[git] ";
        }
        
        return "";
    }
    
    /**
     * Remove emojis from prompt (simple implementation)
     */
    private String removeEmojis(String text) {
        return text.replaceAll("[\\p{So}\\p{Sc}\\p{Sk}\\p{Sm}]", "")
                   .replaceAll("🔧|🌈|🚀|⚡|💻|❯|➤|»", "")
                   .replaceAll("\\s+", " ")
                   .trim() + " ";
    }
    
    /**
     * Prompt template data class
     */
    public static class PromptTemplate {
        public final String name;
        public final String description;
        public final String template;
        public final boolean showPath;
        public final boolean showTime;
        public final boolean showGit;
        public final boolean useEmoji;
        
        public PromptTemplate(String name, String description, String template, 
                            boolean showPath, boolean showTime, boolean showGit, boolean useEmoji) {
            this.name = name;
            this.description = description;
            this.template = template;
            this.showPath = showPath;
            this.showTime = showTime;
            this.showGit = showGit;
            this.useEmoji = useEmoji;
        }
        
        @Override
        public String toString() {
            return name + " - " + description;
        }
    }
}
