/**
 * LuCLI Language Management Module
 * 
 * This module allows users to:
 * - List available languages
 * - View current language/locale
 * - Switch between languages
 * - Show language-specific information
 */
component {

    /**
     * Main entry point for the lang module
     */
    public void function main() {
        var args = __arguments ?: [];
        
        // If no arguments, show current language and available languages
        if (arrayLen(args) == 0) {
            showCurrentLanguage();
            writeOutput(chr(10)); // newline
            showAvailableLanguages();
            return;
        }
        
        var command = args[1];
        
        switch (command) {
            case "list":
            case "ls":
                showAvailableLanguages();
                break;
                
            case "current":
            case "show":
                showCurrentLanguage();
                break;
                
            case "set":
                if (arrayLen(args) < 2) {
                    writeOutput("❌ Error: Language code required" & chr(10));
                    writeOutput("Usage: lucli lang set <language-code>" & chr(10));
                    writeOutput("Example: lucli lang set es" & chr(10));
                    return;
                }
                setLanguage(args[2]);
                break;
                
            case "help":
            case "--help":
            case "-h":
                showHelp();
                break;
                
            default:
                writeOutput("❌ Unknown command: " & command & chr(10));
                writeOutput("Use 'lucli lang help' for available commands." & chr(10));
        }
        return true;
    }
    
    /**
     * Show current language/locale information
     */
    private void function showCurrentLanguage() {
        try {
            // Use Java Locale to get current locale
            var currentLocale = createObject("java", "java.util.Locale").getDefault();
            var language = currentLocale.getLanguage();
            var country = currentLocale.getCountry();
            var displayName = currentLocale.getDisplayName();
            
            writeOutput("🌐 Current Language Settings:" & chr(10));
            writeOutput("══════════════════════════════" & chr(10));
            writeOutput("Language Code: " & language & chr(10));
            
            if (len(country)) {
                writeOutput("Country Code:  " & country & chr(10));
                writeOutput("Locale:        " & language & "_" & country & chr(10));
            } else {
                writeOutput("Locale:        " & language & chr(10));
            }
            
            writeOutput("Display Name:  " & displayName & chr(10));
            
            // Check environment variables
            var lucliLocale = getSystemProperty("lucli.locale") ?: getEnvironmentVariable("LUCLI_LOCALE") ?: "";
            var langEnv = getEnvironmentVariable("LANG") ?: "";
            
            if (len(lucliLocale)) {
                writeOutput("LUCLI_LOCALE:  " & lucliLocale & chr(10));
            }
            if (len(langEnv)) {
                writeOutput("LANG:          " & langEnv & chr(10));
            }
            
        } catch (any e) {
            writeOutput("❌ Error getting current language: " & e.message & chr(10));
        }
    }
    
    /**
     * Show all available languages
     */
    private void function showAvailableLanguages() {
        writeOutput("🗣️  Available Languages:" & chr(10));
        writeOutput("═══════════════════════════" & chr(10));
        
        var languages = [
            {code: "en", name: "English", flag: "🇺🇸"},
            {code: "es", name: "Español (Spanish)", flag: "🇪🇸"},
            {code: "fr", name: "Français (French)", flag: "🇫🇷"}
        ];
        
        for (var lang in languages) {
            writeOutput(lang.flag & "  " & lang.code & " - " & lang.name & chr(10));
        }
        
        writeOutput(chr(10));
        writeOutput("💡 To switch language:" & chr(10));
        writeOutput("   lucli lang set <code>    # e.g., lucli lang set es" & chr(10));
        writeOutput("   LUCLI_LOCALE=<code>     # e.g., LUCLI_LOCALE=fr lucli ..." & chr(10));
    }
    
    /**
     * Set/switch language
     */
    private void function setLanguage(required string langCode) {
        var supportedLanguages = ["en", "es", "fr"];
        
        if (!arrayFind(supportedLanguages, langCode)) {
            writeOutput("❌ Unsupported language: " & langCode & chr(10));
            writeOutput("Supported languages: " & arrayToList(supportedLanguages, ", ") & chr(10));
            return;
        }
        
        try {
            // Show instructions for setting the language
            writeOutput("🌐 Setting language to: " & langCode & chr(10));
            writeOutput("════════════════════════════════════" & chr(10));
            
            var langNames = {
                "en": "English",
                "es": "Español (Spanish)", 
                "fr": "Français (French)"
            };
            
            writeOutput("Language: " & langNames[langCode] & chr(10));
            writeOutput(chr(10));
            
            writeOutput("💡 To use this language in LuCLI commands:" & chr(10));
            writeOutput(chr(10));
            writeOutput("Option 1 - Environment Variable (recommended):" & chr(10));
            writeOutput("   export LUCLI_LOCALE=" & langCode & chr(10));
            writeOutput("   lucli server start  # Will use " & langNames[langCode] & chr(10));
            writeOutput(chr(10));
            
            writeOutput("Option 2 - Per-command:" & chr(10));
            writeOutput("   LUCLI_LOCALE=" & langCode & " lucli server start" & chr(10));
            writeOutput(chr(10));
            
            writeOutput("Option 3 - System Property:" & chr(10));
            writeOutput("   java -Dlucli.locale=" & langCode & " -jar lucli.jar server start" & chr(10));
            writeOutput(chr(10));
            
            writeOutput("✅ Language preference noted! Use the commands above to apply it." & chr(10));
            
        } catch (any e) {
            writeOutput("❌ Error setting language: " & e.message & chr(10));
        }
    }
    
    /**
     * Show help information
     */
    private void function showHelp() {
        writeOutput("🌐 LuCLI Language Management" & chr(10));
        writeOutput("═══════════════════════════════" & chr(10));
        writeOutput(chr(10));
        writeOutput("DESCRIPTION:" & chr(10));
        writeOutput("  Manage language and locale settings for LuCLI" & chr(10));
        writeOutput(chr(10));
        writeOutput("USAGE:" & chr(10));
        writeOutput("  lucli lang [command] [options]" & chr(10));
        writeOutput(chr(10));
        writeOutput("COMMANDS:" & chr(10));
        writeOutput("  (no command)      Show current language and available languages" & chr(10));
        writeOutput("  list, ls          List all available languages" & chr(10));
        writeOutput("  current, show     Show current language settings" & chr(10));
        writeOutput("  set <code>        Show instructions to set language" & chr(10));
        writeOutput("  help, --help, -h  Show this help message" & chr(10));
        writeOutput(chr(10));
        writeOutput("EXAMPLES:" & chr(10));
        writeOutput("  lucli lang              # Show current and available languages" & chr(10));
        writeOutput("  lucli lang list         # List available languages" & chr(10));
        writeOutput("  lucli lang current      # Show current language" & chr(10));
        writeOutput("  lucli lang set es       # Get instructions to set Spanish" & chr(10));
        writeOutput("  lucli lang set fr       # Get instructions to set French" & chr(10));
        writeOutput(chr(10));
        writeOutput("SUPPORTED LANGUAGES:" & chr(10));
        writeOutput("  🇺🇸 en - English" & chr(10));
        writeOutput("  🇪🇸 es - Español (Spanish)" & chr(10));
        writeOutput("  🇫🇷 fr - Français (French)" & chr(10));
        writeOutput(chr(10));
        writeOutput("NOTE:" & chr(10));
        writeOutput("  Language settings affect user interface messages." & chr(10));
        writeOutput("  Help text and documentation remain in English." & chr(10));
    }
    
    /**
     * Helper function to get system property
     */
    private string function getSystemProperty(required string key) {
        try {
            return createObject("java", "java.lang.System").getProperty(key) ?: "";
        } catch (any e) {
            return "";
        }
    }
    
    /**
     * Helper function to get environment variable
     */
    private string function getEnvironmentVariable(required string key) {
        try {
            var env = createObject("java", "java.lang.System").getenv();
            return env.get(key) ?: "";
        } catch (any e) {
            return "";
        }
    }
}
