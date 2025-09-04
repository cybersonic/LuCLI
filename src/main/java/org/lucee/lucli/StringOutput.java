package org.lucee.lucli;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized output post-processor for LuCLI that handles:
 * - Emoji replacements based on terminal capabilities
 * - Placeholder substitutions (timestamps, environment variables, etc.)
 * - Output formatting and consistency
 * - Routing to appropriate output streams
 */
public class StringOutput {
    
    private static StringOutput instance;
    private final Settings settings;
    private final Map<String, PlaceholderSupplier> placeholderReplacements;
    private final Pattern placeholderPattern;
    private PrintStream outputStream = System.out;
    private PrintStream errorStream = System.err;
    private boolean enablePostProcessing = true;
    private final Object lock = new Object();
    
    // Placeholder patterns
    private static final String EMOJI_PREFIX = "EMOJI_";
    private static final String TIME_PREFIX = "TIME_";
    private static final String ENV_PREFIX = "ENV_";
    
    /**
     * Private constructor for singleton
     */
    private StringOutput() {
        this.settings = null; // Settings not used currently, can be added later if needed
        this.placeholderReplacements = new HashMap<>();
        this.placeholderPattern = Pattern.compile("\\$\\{([^}]+)\\}");
        
        // Initialize default placeholder replacements
        initializeDefaultReplacements();
    }
    
    /**
     * Get the singleton instance
     */
    public static StringOutput getInstance() {
        if (instance == null) {
            synchronized (StringOutput.class) {
                if (instance == null) {
                    instance = new StringOutput();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize default placeholder replacements
     */
    private void initializeDefaultReplacements() {
        // Time-based placeholders
        placeholderReplacements.put("NOW", () -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        placeholderReplacements.put("DATE", () -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        placeholderReplacements.put("TIME", () -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
        placeholderReplacements.put("TIMESTAMP", () -> String.valueOf(System.currentTimeMillis()));
        
        // System information placeholders
        placeholderReplacements.put("USER_HOME", () -> System.getProperty("user.home"));
        placeholderReplacements.put("USER_NAME", () -> System.getProperty("user.name"));
        placeholderReplacements.put("WORKING_DIR", () -> System.getProperty("user.dir"));
        placeholderReplacements.put("OS_NAME", () -> System.getProperty("os.name"));
        placeholderReplacements.put("JAVA_VERSION", () -> System.getProperty("java.version"));
        
        // LuCLI-specific placeholders
        placeholderReplacements.put("LUCLI_VERSION", () -> LuCLI.getVersion());
        placeholderReplacements.put("LUCLI_HOME", () -> {
            String home = System.getProperty("lucli.home");
            if (home == null) home = System.getenv("LUCLI_HOME");
            if (home == null) home = System.getProperty("user.home") + "/.lucli";
            return home;
        });
        
        // Emoji placeholders - delegate to WindowsCompatibility
        initializeEmojiReplacements();
    }
    
    /**
     * Initialize emoji placeholder replacements
     */
    private void initializeEmojiReplacements() {
        placeholderReplacements.put("EMOJI_SUCCESS", () -> WindowsCompatibility.getEmoji("✅", "[OK]"));
        placeholderReplacements.put("EMOJI_ERROR", () -> WindowsCompatibility.getEmoji("❌", "[ERROR]"));
        placeholderReplacements.put("EMOJI_WARNING", () -> WindowsCompatibility.getEmoji("⚠️", "[WARNING]"));
        placeholderReplacements.put("EMOJI_INFO", () -> WindowsCompatibility.getEmoji("ℹ️", "[INFO]"));
        placeholderReplacements.put("EMOJI_ROCKET", () -> WindowsCompatibility.getEmoji("🚀", ""));
        placeholderReplacements.put("EMOJI_FOLDER", () -> WindowsCompatibility.getEmoji("📁", ""));
        placeholderReplacements.put("EMOJI_COMPUTER", () -> WindowsCompatibility.getEmoji("💻", "[CMD]"));
        placeholderReplacements.put("EMOJI_TOOL", () -> WindowsCompatibility.getEmoji("🔧", "[TOOL]"));
        placeholderReplacements.put("EMOJI_ART", () -> WindowsCompatibility.getEmoji("🎨", "[STYLE]"));
        placeholderReplacements.put("EMOJI_WAVE", () -> WindowsCompatibility.getEmoji("👋", "[BYE]"));
        placeholderReplacements.put("EMOJI_BULB", () -> WindowsCompatibility.getEmoji("💡", "[TIP]"));
        
        // Additional emoji placeholders
        placeholderReplacements.put("EMOJI_FIRE", () -> WindowsCompatibility.getEmoji("🔥", "[HOT]"));
        placeholderReplacements.put("EMOJI_STAR", () -> WindowsCompatibility.getEmoji("⭐", "[*]"));
        placeholderReplacements.put("EMOJI_HEART", () -> WindowsCompatibility.getEmoji("❤️", "<3"));
        placeholderReplacements.put("EMOJI_THUMBS_UP", () -> WindowsCompatibility.getEmoji("👍", "[+]"));
        placeholderReplacements.put("EMOJI_CLOCK", () -> WindowsCompatibility.getEmoji("🕐", "[TIME]"));
        placeholderReplacements.put("EMOJI_GEAR", () -> WindowsCompatibility.getEmoji("⚙️", "[CONFIG]"));
        placeholderReplacements.put("EMOJI_LIGHTNING", () -> WindowsCompatibility.getEmoji("⚡", "[FAST]"));
        placeholderReplacements.put("EMOJI_SHIELD", () -> WindowsCompatibility.getEmoji("🛡️", "[SECURE]"));
        placeholderReplacements.put("EMOJI_MAGNIFYING_GLASS", () -> WindowsCompatibility.getEmoji("🔍", "[SEARCH]"));
        placeholderReplacements.put("EMOJI_PACKAGE", () -> WindowsCompatibility.getEmoji("📦", "[PKG]"));
    }
    
    /**
     * Process a string through the post-processor
     */
    public String process(String input) {
        if (!enablePostProcessing || input == null) {
            return input;
        }
        
        synchronized (lock) {
            String result = input;
            
            // Process placeholders
            result = processPlaceholders(result);
            
            // Process legacy emoji patterns (for backward compatibility)
            result = processLegacyEmojis(result);
            
            return result;
        }
    }
    
    /**
     * Process placeholder substitutions
     */
    private String processPlaceholders(String input) {
        Matcher matcher = placeholderPattern.matcher(input);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = getPlaceholderValue(placeholder);
            
            if (replacement != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            } else {
                // Keep the original placeholder if no replacement found
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Get the value for a placeholder
     */
    private String getPlaceholderValue(String placeholder) {
        // Direct lookup first
        PlaceholderSupplier supplier = placeholderReplacements.get(placeholder);
        if (supplier != null) {
            try {
                return supplier.get();
            } catch (Exception e) {
                return "[ERROR: " + placeholder + "]";
            }
        }
        
        // Handle environment variable placeholders
        if (placeholder.startsWith(ENV_PREFIX)) {
            String envVar = placeholder.substring(ENV_PREFIX.length());
            return System.getenv(envVar);
        }
        
        // Handle time format placeholders
        if (placeholder.startsWith(TIME_PREFIX)) {
            String format = placeholder.substring(TIME_PREFIX.length());
            try {
                return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
            } catch (Exception e) {
                return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        }
        
        // Handle dynamic emoji placeholders
        if (placeholder.startsWith(EMOJI_PREFIX)) {
            String emojiName = placeholder.substring(EMOJI_PREFIX.length());
            return getDynamicEmoji(emojiName);
        }
        
        return null;
    }
    
    /**
     * Get dynamic emoji based on name
     */
    private String getDynamicEmoji(String emojiName) {
        switch (emojiName.toUpperCase()) {
            case "SUCCESS": case "OK": case "CHECK":
                return WindowsCompatibility.getEmoji("✅", "[OK]");
            case "ERROR": case "FAIL": case "X":
                return WindowsCompatibility.getEmoji("❌", "[ERROR]");
            case "WARNING": case "WARN":
                return WindowsCompatibility.getEmoji("⚠️", "[WARNING]");
            case "INFO": case "INFORMATION":
                return WindowsCompatibility.getEmoji("ℹ️", "[INFO]");
            case "QUESTION": case "HELP":
                return WindowsCompatibility.getEmoji("❓", "[?]");
            default:
                return WindowsCompatibility.getEmoji("❔", "[" + emojiName + "]");
        }
    }
    
    /**
     * Process legacy emoji patterns for backward compatibility
     */
    private String processLegacyEmojis(String input) {
        String result = input;
        
        // Replace WindowsCompatibility.Symbols references if they appear in strings
        result = result.replace("${SUCCESS_EMOJI}", WindowsCompatibility.getEmoji("✅", "[OK]"));
        result = result.replace("${ERROR_EMOJI}", WindowsCompatibility.getEmoji("❌", "[ERROR]"));
        result = result.replace("${WARNING_EMOJI}", WindowsCompatibility.getEmoji("⚠️", "[WARNING]"));
        result = result.replace("${INFO_EMOJI}", WindowsCompatibility.getEmoji("ℹ️", "[INFO]"));
        
        return result;
    }
    
    /**
     * Print a message to the configured output stream with post-processing
     */
    public void println(String message) {
        String processed = process(message);
        outputStream.println(processed);
    }
    
    /**
     * Print an empty line to the configured output stream
     */
    public void println() {
        outputStream.println();
    }
    
    /**
     * Print a message to the configured output stream with post-processing (no newline)
     */
    public void print(String message) {
        String processed = process(message);
        outputStream.print(processed);
    }
    
    /**
     * Print an error message to the error stream with post-processing
     */
    public void printlnError(String message) {
        String processed = process(message);
        errorStream.println(processed);
    }
    
    /**
     * Print an error message to the error stream with post-processing (no newline)
     */
    public void printError(String message) {
        String processed = process(message);
        errorStream.print(processed);
    }
    
    /**
     * Print a formatted message with post-processing
     */
    public void printf(String format, Object... args) {
        String formatted = String.format(format, args);
        String processed = process(formatted);
        outputStream.print(processed);
    }
    
    /**
     * Add a custom placeholder replacement
     */
    public void addPlaceholder(String key, PlaceholderSupplier supplier) {
        synchronized (lock) {
            placeholderReplacements.put(key, supplier);
        }
    }
    
    /**
     * Add a custom placeholder replacement with a static value
     */
    public void addPlaceholder(String key, String value) {
        addPlaceholder(key, () -> value);
    }
    
    /**
     * Remove a placeholder replacement
     */
    public void removePlaceholder(String key) {
        synchronized (lock) {
            placeholderReplacements.remove(key);
        }
    }
    
    /**
     * Set the output stream
     */
    public void setOutputStream(PrintStream stream) {
        this.outputStream = stream;
    }
    
    /**
     * Set the error stream
     */
    public void setErrorStream(PrintStream stream) {
        this.errorStream = stream;
    }
    
    /**
     * Enable or disable post-processing
     */
    public void setPostProcessingEnabled(boolean enabled) {
        this.enablePostProcessing = enabled;
    }
    
    /**
     * Check if post-processing is enabled
     */
    public boolean isPostProcessingEnabled() {
        return enablePostProcessing;
    }
    
    /**
     * Get the current output stream
     */
    public PrintStream getOutputStream() {
        return outputStream;
    }
    
    /**
     * Get the current error stream
     */
    public PrintStream getErrorStream() {
        return errorStream;
    }
    
    /**
     * Functional interface for placeholder suppliers
     */
    @FunctionalInterface
    public interface PlaceholderSupplier {
        String get() throws Exception;
    }
    
    /**
     * Convenience methods for common output patterns
     */
    public static class Quick {
        private static final StringOutput output = StringOutput.getInstance();
        
        public static void success(String message) {
            output.println("${EMOJI_SUCCESS} " + message);
        }
        
        public static void error(String message) {
            output.printlnError("${EMOJI_ERROR} " + message);
        }
        
        public static void warning(String message) {
            output.println("${EMOJI_WARNING} " + message);
        }
        
        public static void info(String message) {
            output.println("${EMOJI_INFO} " + message);
        }
        
        public static void tip(String message) {
            output.println("${EMOJI_BULB} " + message);
        }
        
        public static void status(String type, String message) {
            String placeholder = "${EMOJI_" + type.toUpperCase() + "}";
            output.println(placeholder + " " + message);
        }
    }
}
