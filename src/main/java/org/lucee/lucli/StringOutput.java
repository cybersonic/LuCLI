package org.lucee.lucli;

import java.io.PrintStream;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
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
    
    // Internationalization support
    private Locale currentLocale;
    private ResourceBundle messages;
    private static final String MESSAGES_BUNDLE = "messages.messages";
    
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
        
        // Initialize internationalization
        initializeLocale();
        
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
        placeholderReplacements.put("CFLINT_JAR_PATH", () -> {
            try {
                // Use reflection to get CFLintDownloader.getCFLintJarPath() to avoid tight coupling
                Class<?> downloaderClass = Class.forName("org.lucee.lucli.cflint.CFLintDownloader");
                java.lang.reflect.Method method = downloaderClass.getDeclaredMethod("getCFLintJarPath");
                return method.invoke(null).toString();
            } catch (Exception e) {
                return "~/.lucli/cflint.jar";
            }
        });
        placeholderReplacements.put("CFLINT_STATUS", () -> {
            try {
                // Use reflection to get CFLintDownloader.getCFLintStatus() to avoid tight coupling
                Class<?> downloaderClass = Class.forName("org.lucee.lucli.cflint.CFLintDownloader");
                java.lang.reflect.Method method = downloaderClass.getDeclaredMethod("getCFLintStatus");
                return method.invoke(null).toString();
            } catch (Exception e) {
                return "unknown version";
            }
        });
        
        // Emoji placeholders - delegate to WindowsSupport
        initializeEmojiReplacements();
    }
    
    /**
     * Initialize emoji placeholder replacements
     */
    private void initializeEmojiReplacements() {
        placeholderReplacements.put("EMOJI_SUCCESS", () -> WindowsSupport.getEmoji("✅", "[OK]"));
        placeholderReplacements.put("EMOJI_ERROR", () -> WindowsSupport.getEmoji("❌", "[ERROR]"));
        placeholderReplacements.put("EMOJI_WARNING", () -> WindowsSupport.getEmoji("⚠️", "[WARNING]"));
        placeholderReplacements.put("EMOJI_INFO", () -> WindowsSupport.getEmoji("ℹ️", "[INFO]"));
        placeholderReplacements.put("EMOJI_ROCKET", () -> WindowsSupport.getEmoji("🚀", ""));
        placeholderReplacements.put("EMOJI_FOLDER", () -> WindowsSupport.getEmoji("📁", ""));
        placeholderReplacements.put("EMOJI_COMPUTER", () -> WindowsSupport.getEmoji("💻", "[CMD]"));
        placeholderReplacements.put("EMOJI_TOOL", () -> WindowsSupport.getEmoji("🔧", "[TOOL]"));
        placeholderReplacements.put("EMOJI_ART", () -> WindowsSupport.getEmoji("🎨", "[STYLE]"));
        placeholderReplacements.put("EMOJI_WAVE", () -> WindowsSupport.getEmoji("👋", "[BYE]"));
        placeholderReplacements.put("EMOJI_BULB", () -> WindowsSupport.getEmoji("💡", "[TIP]"));
        
        // Additional emoji placeholders
        placeholderReplacements.put("EMOJI_FIRE", () -> WindowsSupport.getEmoji("🔥", "[HOT]"));
        placeholderReplacements.put("EMOJI_STAR", () -> WindowsSupport.getEmoji("⭐", "[*]"));
        placeholderReplacements.put("EMOJI_HEART", () -> WindowsSupport.getEmoji("❤️", "<3"));
        placeholderReplacements.put("EMOJI_THUMBS_UP", () -> WindowsSupport.getEmoji("👍", "[+]"));
        placeholderReplacements.put("EMOJI_CLOCK", () -> WindowsSupport.getEmoji("🕐", "[TIME]"));
        placeholderReplacements.put("EMOJI_GEAR", () -> WindowsSupport.getEmoji("⚙️", "[CONFIG]"));
        placeholderReplacements.put("EMOJI_LIGHTNING", () -> WindowsSupport.getEmoji("⚡", "[FAST]"));
        placeholderReplacements.put("EMOJI_SHIELD", () -> WindowsSupport.getEmoji("🛡️", "[SECURE]"));
        placeholderReplacements.put("EMOJI_MAGNIFYING_GLASS", () -> WindowsSupport.getEmoji("🔍", "[SEARCH]"));
        placeholderReplacements.put("EMOJI_PACKAGE", () -> WindowsSupport.getEmoji("📦", "[PKG]"));
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
        
        // Fallback: treat ${FOO} as ${ENV_FOO} / ${FOO from env}
        String envValue = System.getenv(placeholder);
        if (envValue != null) {
            return envValue;
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
        
        return null; //this might need to return a "" rather than null
    }
    
    /**
     * Get dynamic emoji based on name
     */
    private String getDynamicEmoji(String emojiName) {
        switch (emojiName.toUpperCase()) {
            case "SUCCESS": case "OK": case "CHECK":
                return WindowsSupport.getEmoji("✅", "[OK]");
            case "ERROR": case "FAIL": case "X":
                return WindowsSupport.getEmoji("❌", "[ERROR]");
            case "WARNING": case "WARN":
                return WindowsSupport.getEmoji("⚠️", "[WARNING]");
            case "INFO": case "INFORMATION":
                return WindowsSupport.getEmoji("ℹ️", "[INFO]");
            case "QUESTION": case "HELP":
                return WindowsSupport.getEmoji("❓", "[?]");
            default:
                return WindowsSupport.getEmoji("❔", "[" + emojiName + "]");
        }
    }
    
    /**
     * Process legacy emoji patterns for backward compatibility
     */
    private String processLegacyEmojis(String input) {
        String result = input;
        
        // Replace WindowsSupport.Symbols references if they appear in strings
        result = result.replace("${SUCCESS_EMOJI}", WindowsSupport.getEmoji("✅", "[OK]"));
        result = result.replace("${ERROR_EMOJI}", WindowsSupport.getEmoji("❌", "[ERROR]"));
        result = result.replace("${WARNING_EMOJI}", WindowsSupport.getEmoji("⚠️", "[WARNING]"));
        result = result.replace("${INFO_EMOJI}", WindowsSupport.getEmoji("ℹ️", "[INFO]"));
        
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
     * Initialize the locale from system properties, environment variables, or user settings
     * Priority order:
     * 1. System property: -Dlucli.locale=xx
     * 2. Environment variable: LUCLI_LOCALE=xx
     * 3. User settings file: ~/.lucli/settings.json
     * 4. Environment variable: LANG=xx
     * 5. System default
     */
    private void initializeLocale() {
        // Check for explicit locale setting
        String localeStr = System.getProperty("lucli.locale");
        if (localeStr == null) {
            localeStr = System.getenv("LUCLI_LOCALE");
        }
        
        // Check user settings file if no override specified
        if (localeStr == null) {
            try {
                Settings settings = new Settings();
                localeStr = settings.getLanguage();
            } catch (Exception e) {
                // Ignore settings loading errors, continue with other fallbacks
            }
        }
        
        if (localeStr == null) {
            localeStr = System.getenv("LANG");
        }
        
        if (localeStr != null) {
            try {
                // Parse locale string (e.g., "en_US", "es", "fr_FR")
                if (localeStr.contains("_")) {
                    String[] parts = localeStr.split("_");
                    currentLocale = new Locale(parts[0], parts[1]);
                } else {
                    currentLocale = new Locale(localeStr);
                }
            } catch (Exception e) {
                currentLocale = Locale.getDefault();
            }
        } else {
            currentLocale = Locale.getDefault();
        }
        
        // Load the resource bundle
        loadMessages();
    }
    
    /**
     * Load the messages resource bundle for the current locale
     */
    private void loadMessages() {
        try {
            messages = ResourceBundle.getBundle(MESSAGES_BUNDLE, currentLocale);
        } catch (MissingResourceException e) {
            // Fallback to default locale
            try {
                messages = ResourceBundle.getBundle(MESSAGES_BUNDLE, Locale.ENGLISH);
            } catch (MissingResourceException e2) {
                // Create empty bundle if none found
                messages = null;
            }
        }
    }
    
    /**
     * Get a localized message by key
     * @param key The message key
     * @return The localized message, or the key itself if not found
     */
    public String getMessage(String key) {
        return getMessage(key, (Object[]) null);
    }
    
    /**
     * Get a localized message by key with arguments
     * @param key The message key
     * @param args Arguments to substitute in the message
     * @return The localized message with arguments substituted, or the key itself if not found
     */
    public String getMessage(String key, Object... args) {
        if (messages == null) {
            // Fallback: return key with args if no bundle available
            return args != null && args.length > 0 ? 
                key + " [" + String.join(", ", java.util.Arrays.stream(args).map(String::valueOf).toArray(String[]::new)) + "]"
                : key;
        }
        
        try {
            String message = messages.getString(key);
            if (args != null && args.length > 0) {
                return MessageFormat.format(message, args);
            } else {
                return message;
            }
        } catch (MissingResourceException e) {
            // Return key as fallback
            return args != null && args.length > 0 ? 
                key + " [" + String.join(", ", java.util.Arrays.stream(args).map(String::valueOf).toArray(String[]::new)) + "]"
                : key;
        }
    }
    
    /**
     * Print a localized message by key
     * @param key The message key
     */
    public void printlnMessage(String key) {
        println(process(getMessage(key)));
    }
    
    /**
     * Print a localized message by key with arguments
     * @param key The message key
     * @param args Arguments to substitute in the message
     */
    public void printlnMessage(String key, Object... args) {
        println(process(getMessage(key, args)));
    }
    
    /**
     * Get the current locale
     * @return The current locale
     */
    public Locale getCurrentLocale() {
        return currentLocale;
    }
    
    /**
     * Set the locale and reload messages
     * @param locale The new locale
     */
    public void setLocale(Locale locale) {
        this.currentLocale = locale;
        loadMessages();
    }
    
    /**
     * Set the language preference in user settings and reload messages
     * @param languageCode Language code (e.g., "es", "fr"), null or empty string for system default
     */
    public void setLanguagePreference(String languageCode) {
        try {
            Settings settings = new Settings();
            // Treat empty string as null for clearing preference
            String lang = (languageCode != null && languageCode.trim().isEmpty()) ? null : languageCode;
            settings.setLanguage(lang);
            
            // Reinitialize locale to pick up the new setting
            initializeLocale();
        } catch (Exception e) {
            // If settings update fails, continue without error
            System.err.println("Warning: Could not update language preference: " + e.getMessage());
        }
    }
    
    /**
     * Load text content from a resource file and process it through StringOutput
     * @param resourcePath Path to the resource file (e.g., "/text/cflint-help.txt")
     * @return Processed text content
     * @throws Exception if the resource cannot be found or loaded
     */
    public String loadTextFromFile(String resourcePath) throws Exception {
        try (java.io.InputStream is = StringOutput.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new java.io.FileNotFoundException("Text resource not found: " + resourcePath);
            }
            String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return process(content);
        }
    }
    
    /**
     * Static convenience method to load and process text from a resource file
     * @param resourcePath Path to the resource file (e.g., "/text/cflint-help.txt")
     * @return Processed text content, or an error message if loading fails
     */
    public static String loadText(String resourcePath) {
        try {
            return getInstance().loadTextFromFile(resourcePath);
        } catch (Exception e) {
            return "[ERROR: Could not load text from " + resourcePath + ": " + e.getMessage() + "]";
        }
    }
    
    /**
     * Static convenience method to get a localized message
     * @param key The message key
     * @return The localized message
     */
    public static String msg(String key) {
        return getInstance().getMessage(key);
    }
    
    /**
     * Static convenience method to get a localized message with arguments
     * @param key The message key
     * @param args Arguments to substitute in the message
     * @return The localized message with arguments substituted
     */
    public static String msg(String key, Object... args) {
        return getInstance().getMessage(key, args);
    }
    
    /**
     * Load text from a resource file and apply custom placeholder values
     * @param resourcePath Path to the resource file
     * @param customPlaceholders Map of custom placeholder key-value pairs
     * @return Processed text content with custom placeholders applied
     */
    public static String loadTextWithPlaceholders(String resourcePath, java.util.Map<String, String> customPlaceholders) {
        try {
            String content = getInstance().loadTextFromFile(resourcePath);
            
            // Apply custom placeholders after standard processing
            for (java.util.Map.Entry<String, String> entry : customPlaceholders.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                content = content.replace(placeholder, entry.getValue());
            }
            
            return content;
        } catch (Exception e) {
            return "[ERROR: Could not load text from " + resourcePath + ": " + e.getMessage() + "]";
        }
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
