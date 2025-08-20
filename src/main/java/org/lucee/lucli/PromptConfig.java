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
            "default", "minimal", "zsh", "time", "colorful", "dev", "space", "electric",
            "hacker", "gaming", "corporate", "train", "locomotive", "powerline", "powerline-pro"
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
                promptJson.path("useEmoji").asBoolean(false),
                promptJson.path("backgroundColor").asText(""),
                promptJson.path("foregroundColor").asText(""),
                promptJson.path("style").asText(""),
                promptJson.path("multiline").asBoolean(false),
                promptJson.path("rightAlign").asText(""),
                promptJson.path("separator").asText(""),
                promptJson.path("padding").asInt(0)
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
                    promptJson.path("useEmoji").asBoolean(false),
                    promptJson.path("backgroundColor").asText(""),
                    promptJson.path("foregroundColor").asText(""),
                    promptJson.path("style").asText(""),
                    promptJson.path("multiline").asBoolean(false),
                    promptJson.path("rightAlign").asText(""),
                    promptJson.path("separator").asText(""),
                    promptJson.path("padding").asInt(0)
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
     * Refresh prompt files - recreate all builtin prompt files from JAR resources
     * This allows users to get the latest prompt templates
     */
    public int refreshPromptFiles() {
        Path promptsDir = settings.getPromptsDir();
        int refreshedCount = 0;
        
        for (PromptTemplate template : builtinTemplates.values()) {
            Path promptFile = promptsDir.resolve(template.name + ".json");
            try {
                // Load the latest template from JAR resources
                PromptTemplate latestTemplate = loadBuiltinTemplate(template.name);
                if (latestTemplate != null) {
                    // Create JSON with all the advanced properties
                    ObjectNode promptJson = objectMapper.createObjectNode();
                    promptJson.put("name", latestTemplate.name);
                    promptJson.put("description", latestTemplate.description);
                    promptJson.put("template", latestTemplate.template);
                    promptJson.put("showPath", latestTemplate.showPath);
                    promptJson.put("showTime", latestTemplate.showTime);
                    promptJson.put("showGit", latestTemplate.showGit);
                    promptJson.put("useEmoji", latestTemplate.useEmoji);
                    
                    // Include advanced styling properties
                    if (!latestTemplate.backgroundColor.isEmpty()) {
                        promptJson.put("backgroundColor", latestTemplate.backgroundColor);
                    }
                    if (!latestTemplate.foregroundColor.isEmpty()) {
                        promptJson.put("foregroundColor", latestTemplate.foregroundColor);
                    }
                    if (!latestTemplate.style.isEmpty()) {
                        promptJson.put("style", latestTemplate.style);
                    }
                    if (latestTemplate.multiline) {
                        promptJson.put("multiline", latestTemplate.multiline);
                    }
                    if (!latestTemplate.rightAlign.isEmpty()) {
                        promptJson.put("rightAlign", latestTemplate.rightAlign);
                    }
                    if (!latestTemplate.separator.isEmpty()) {
                        promptJson.put("separator", latestTemplate.separator);
                    }
                    if (latestTemplate.padding > 0) {
                        promptJson.put("padding", latestTemplate.padding);
                    }
                    
                    // Write/overwrite the file
                    objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValue(promptFile.toFile(), promptJson);
                    refreshedCount++;
                }
            } catch (IOException e) {
                System.err.println("⚠️  Warning: Could not refresh prompt file " + template.name + ": " + e.getMessage());
            }
        }
        
        return refreshedCount;
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
        
        // Replace icons with Unicode symbols that work in any terminal
        prompt = UnicodeIcons.replaceIcons(prompt);
        
        // Apply advanced styling if specified
        prompt = applyAdvancedStyling(prompt, template);
        
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
     * Apply advanced styling (colors, backgrounds, formatting) to prompt
     */
    private String applyAdvancedStyling(String prompt, PromptTemplate template) {
        if (prompt == null || prompt.isEmpty()) return prompt;
        
        StringBuilder styledPrompt = new StringBuilder();
        
        // Add padding if specified
        if (template.padding > 0) {
            styledPrompt.append(" ".repeat(template.padding));
        }
        
        // Apply background color
        if (!template.backgroundColor.isEmpty()) {
            styledPrompt.append(getAnsiBackgroundCode(template.backgroundColor));
        }
        
        // Apply foreground color
        if (!template.foregroundColor.isEmpty()) {
            styledPrompt.append(getAnsiForegroundCode(template.foregroundColor));
        }
        
        // Apply text styling
        if (!template.style.isEmpty()) {
            styledPrompt.append(getAnsiStyleCode(template.style));
        }
        
        // Add the actual prompt content
        styledPrompt.append(prompt);
        
        // Reset ANSI codes
        if (!template.backgroundColor.isEmpty() || !template.foregroundColor.isEmpty() || !template.style.isEmpty()) {
            styledPrompt.append("\u001B[0m"); // ANSI reset
        }
        
        // Handle multi-line prompts
        if (template.multiline && !template.rightAlign.isEmpty()) {
            return createMultiLinePrompt(styledPrompt.toString(), template.rightAlign, template.separator);
        }
        
        // Add padding after if specified
        if (template.padding > 0) {
            styledPrompt.append(" ".repeat(template.padding));
        }
        
        return styledPrompt.toString();
    }
    
    /**
     * Create a multi-line prompt with right-aligned content
     */
    private String createMultiLinePrompt(String leftPrompt, String rightContent, String separator) {
        try {
            // Get terminal width (default to 80 if unavailable)
            int terminalWidth = getTerminalWidth();
            
            // Process right content for placeholders (time, git, etc.)
            String processedRight = rightContent;
            
            // Replace time placeholder in right content
            if (processedRight.contains("{time}")) {
                String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                processedRight = processedRight.replace("{time}", time);
            }
            
            // Replace git placeholder in right content
            if (processedRight.contains("{git}")) {
                // We need access to fileSystemState, so for now use a simple git indicator
                processedRight = processedRight.replace("{git}", "[git] ");
            }
            
            // Replace Unicode icons in right content
            processedRight = UnicodeIcons.replaceIcons(processedRight);
            
            // Calculate spacing
            int leftLength = getDisplayLength(leftPrompt);
            int rightLength = getDisplayLength(processedRight);
            int spacingNeeded = Math.max(1, terminalWidth - leftLength - rightLength - 2);
            
            // Create separator line if specified
            String separatorLine = "";
            if (!separator.isEmpty()) {
                String separatorChar = UnicodeIcons.replaceIcons(separator);
                separatorLine = "\n" + separatorChar.repeat(Math.max(1, terminalWidth / separatorChar.length())) + "\n";
            }
            
            return separatorLine + leftPrompt + " ".repeat(spacingNeeded) + processedRight + "\n";
        } catch (Exception e) {
            // Fallback to simple prompt if terminal width detection fails
            return leftPrompt;
        }
    }
    
    /**
     * Get terminal width (simplified implementation)
     */
    private int getTerminalWidth() {
        try {
            String cols = System.getenv("COLUMNS");
            if (cols != null && !cols.isEmpty()) {
                return Integer.parseInt(cols);
            }
        } catch (NumberFormatException e) {
            // Ignore and use default
        }
        return 80; // Default width
    }
    
    /**
     * Calculate display length of string (excluding ANSI codes)
     */
    private int getDisplayLength(String text) {
        if (text == null) return 0;
        // Remove ANSI escape codes for length calculation
        return text.replaceAll("\\u001B\\[[;\\d]*m", "").length();
    }
    
    /**
     * Get ANSI background color code
     */
    private String getAnsiBackgroundCode(String color) {
        switch (color.toLowerCase()) {
            case "black": return "\u001B[40m";
            case "red": return "\u001B[41m";
            case "green": return "\u001B[42m";
            case "yellow": return "\u001B[43m";
            case "blue": return "\u001B[44m";
            case "magenta": case "purple": return "\u001B[45m";
            case "cyan": return "\u001B[46m";
            case "white": return "\u001B[47m";
            case "bright-black": case "gray": case "grey": return "\u001B[100m";
            case "bright-red": return "\u001B[101m";
            case "bright-green": return "\u001B[102m";
            case "bright-yellow": return "\u001B[103m";
            case "bright-blue": return "\u001B[104m";
            case "bright-magenta": case "bright-purple": return "\u001B[105m";
            case "bright-cyan": return "\u001B[106m";
            case "bright-white": return "\u001B[107m";
            default:
                // Try to parse RGB hex color (simplified)
                if (color.startsWith("#") && color.length() == 7) {
                    return "\u001B[48;2;" + parseHexColor(color) + "m";
                }
                return "";
        }
    }
    
    /**
     * Get ANSI foreground color code
     */
    private String getAnsiForegroundCode(String color) {
        switch (color.toLowerCase()) {
            case "black": return "\u001B[30m";
            case "red": return "\u001B[31m";
            case "green": return "\u001B[32m";
            case "yellow": return "\u001B[33m";
            case "blue": return "\u001B[34m";
            case "magenta": case "purple": return "\u001B[35m";
            case "cyan": return "\u001B[36m";
            case "white": return "\u001B[37m";
            case "bright-black": case "gray": case "grey": return "\u001B[90m";
            case "bright-red": return "\u001B[91m";
            case "bright-green": return "\u001B[92m";
            case "bright-yellow": return "\u001B[93m";
            case "bright-blue": return "\u001B[94m";
            case "bright-magenta": case "bright-purple": return "\u001B[95m";
            case "bright-cyan": return "\u001B[96m";
            case "bright-white": return "\u001B[97m";
            default:
                // Try to parse RGB hex color (simplified)
                if (color.startsWith("#") && color.length() == 7) {
                    return "\u001B[38;2;" + parseHexColor(color) + "m";
                }
                return "";
        }
    }
    
    /**
     * Get ANSI style code
     */
    private String getAnsiStyleCode(String style) {
        switch (style.toLowerCase()) {
            case "bold": return "\u001B[1m";
            case "dim": case "faint": return "\u001B[2m";
            case "italic": return "\u001B[3m";
            case "underline": return "\u001B[4m";
            case "blink": return "\u001B[5m";
            case "reverse": case "invert": return "\u001B[7m";
            case "strikethrough": return "\u001B[9m";
            default: return "";
        }
    }
    
    /**
     * Parse hex color to RGB values for ANSI 24-bit color
     */
    private String parseHexColor(String hex) {
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return r + ";" + g + ";" + b;
        } catch (Exception e) {
            return "255;255;255"; // Default to white
        }
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
     * Prompt template data class with advanced styling support
     */
    public static class PromptTemplate {
        public final String name;
        public final String description;
        public final String template;
        public final boolean showPath;
        public final boolean showTime;
        public final boolean showGit;
        public final boolean useEmoji;
        
        // Advanced styling properties
        public final String backgroundColor;  // ANSI background color (e.g., "blue", "#1e1e1e")
        public final String foregroundColor;  // ANSI foreground color (e.g., "white", "#00ff00")
        public final String style;            // Text style: "bold", "italic", "underline", "dim"
        public final boolean multiline;       // Whether prompt spans multiple lines
        public final String rightAlign;       // Text to display on the right side of terminal
        public final String separator;        // Character/string used for visual separation
        public final int padding;             // Amount of padding around prompt content
        
        // Constructor for basic templates (backward compatibility)
        public PromptTemplate(String name, String description, String template, 
                            boolean showPath, boolean showTime, boolean showGit, boolean useEmoji) {
            this(name, description, template, showPath, showTime, showGit, useEmoji, 
                 "", "", "", false, "", "", 0);
        }
        
        // Full constructor with advanced styling
        public PromptTemplate(String name, String description, String template, 
                            boolean showPath, boolean showTime, boolean showGit, boolean useEmoji,
                            String backgroundColor, String foregroundColor, String style,
                            boolean multiline, String rightAlign, String separator, int padding) {
            this.name = name;
            this.description = description;
            this.template = template;
            this.showPath = showPath;
            this.showTime = showTime;
            this.showGit = showGit;
            this.useEmoji = useEmoji;
            this.backgroundColor = backgroundColor != null ? backgroundColor : "";
            this.foregroundColor = foregroundColor != null ? foregroundColor : "";
            this.style = style != null ? style : "";
            this.multiline = multiline;
            this.rightAlign = rightAlign != null ? rightAlign : "";
            this.separator = separator != null ? separator : "";
            this.padding = Math.max(0, padding);
        }
        
        @Override
        public String toString() {
            return name + " - " + description;
        }
    }
}
