package org.lucee.lucli;

import java.io.IOException;
import java.util.Locale;

/**
 * Utility class to handle Windows-specific terminal and display compatibility issues
 */
public class WindowsCompatibility {
    
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    private static final boolean IS_WINDOWS_TERMINAL = System.getenv("WT_SESSION") != null;
    private static final boolean IS_CONSOLE_HOST = IS_WINDOWS && !IS_WINDOWS_TERMINAL;
    
    /**
     * Check if the current environment supports emojis
     */
    public static boolean supportsEmojis() {
        // Windows Terminal and newer PowerShell versions support emojis
        if (IS_WINDOWS_TERMINAL) {
            return true;
        }
        
        // Check for other modern terminals on Windows that support emojis
        if (IS_WINDOWS) {
            // VS Code integrated terminal
            if (System.getenv("VSCODE_INJECTION") != null) {
                return true;
            }
            
            // Check TERM_PROGRAM for known good terminals
            String termProgram = System.getenv("TERM_PROGRAM");
            if (termProgram != null) {
                switch (termProgram.toLowerCase()) {
                    case "vscode":
                        return true;
                    case "hyper":
                        return true;
                    case "iterm.app":
                        return true;
                    case "warpterminal":
                        // Warp terminal on Windows has inconsistent emoji support
                        // Be conservative and disable emojis by default
                        return false;
                    default:
                        // Unknown TERM_PROGRAM, be conservative
                        break;
                }
            }
            
            // Check for ConEmu/cmder
            if (System.getenv("ConEmuPID") != null || System.getenv("CMDER_ROOT") != null) {
                return true;
            }
            
            // Check for Git Bash/MSYS2/Cygwin - but be more specific
            String term = System.getenv("TERM");
            if (term != null) {
                // Only trust these if we're actually in a Unix-like environment
                // Check for MSYSTEM (MSYS2) or presence of Unix-like paths
                String msystem = System.getenv("MSYSTEM");
                String path = System.getenv("PATH");
                
                if ((term.startsWith("cygwin") || term.startsWith("msys")) && 
                    (msystem != null || (path != null && path.contains("/usr/bin")))) {
                    return true;
                }
                
                // For xterm, be very conservative - only if we detect actual Unix environment
                if (term.startsWith("xterm") && msystem != null) {
                    return true;
                }
            }
            
            // Check for newer PowerShell versions that support Unicode
            String psVersion = System.getenv("PSVersion");
            if (psVersion != null) {
                try {
                    // PowerShell 6+ generally supports emojis
                    if (Integer.parseInt(psVersion.split("\\.")[0]) >= 6) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            
            // Check Windows version and console capabilities (be more conservative)
            return false; // Default to false for Windows unless explicitly detected
        }
        
        // Non-Windows systems generally support emojis
        return true;
    }
    
    /**
     * Check if Windows console supports modern features like Unicode/emojis
     */
    private static boolean supportsModernConsole() {
        // Check Windows version - Windows 10 1903+ has better Unicode support
        String version = System.getProperty("os.version", "");
        try {
            String[] versionParts = version.split("\\.");
            if (versionParts.length >= 2) {
                int major = Integer.parseInt(versionParts[0]);
                int minor = Integer.parseInt(versionParts[1]);
                
                // Windows 10 (version 10.0) and later
                if (major >= 10) {
                    // Check if we're in a "dumb" terminal
                    String term = System.getenv("TERM");
                    if ("dumb".equals(term)) {
                        return false;
                    }
                    
                    // Check for ANSI support environment variable
                    String ansiSupport = System.getenv("ANSICON");
                    if (ansiSupport != null) {
                        return true;
                    }
                    
                    // Conservatively return true for Windows 10+ unless explicitly dumb
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            // If we can't parse version, be conservative
        }
        
        return false;
    }
    
    /**
     * Get emoji or fallback text based on Windows compatibility
     */
    public static String getEmoji(String emoji, String fallback) {
        return supportsEmojis() ? emoji : fallback;
    }
    
    /**
     * Check if the current terminal supports colors
     */
    public static boolean supportsColors() {
        if (IS_WINDOWS_TERMINAL) {
            return true;
        }
        
        String term = System.getenv("TERM");
        String colorTerm = System.getenv("COLORTERM");
        
        // Check for color-capable terminals
        if (colorTerm != null && (colorTerm.contains("truecolor") || colorTerm.contains("24bit"))) {
            return true;
        }
        
        if (term != null) {
            return term.contains("color") || term.contains("xterm") || term.contains("screen");
        }
        
        // Default to false on Windows Console Host
        return !IS_CONSOLE_HOST;
    }
    
    /**
     * Get the best terminal type for JLine configuration
     */
    public static String getTerminalType() {
        if (IS_WINDOWS_TERMINAL) {
            return "windows-terminal";
        } else if (IS_CONSOLE_HOST) {
            return "windows";
        } else {
            String term = System.getenv("TERM");
            return term != null ? term : "dumb";
        }
    }
    
    /**
     * Configure environment variables for better JLine compatibility
     */
    public static void configureTerminalEnvironment() {
        // Set TERM if not already set on Windows
        if (IS_WINDOWS && System.getenv("TERM") == null) {
            if (IS_WINDOWS_TERMINAL) {
                System.setProperty("TERM", "xterm-256color");
            } else {
                System.setProperty("TERM", "windows-ansi");
            }
        }
        
        // Disable JLine's native library on problematic Windows setups
        if (IS_CONSOLE_HOST) {
            System.setProperty("jline.terminal", "jline.terminal.impl.DumbTerminal");
        }
    }
    
    /**
     * Get Windows-friendly status symbols
     */
    public static class Symbols {
        public static final String SUCCESS = getEmoji("✅", "[OK]");
        public static final String ERROR = getEmoji("❌", "[ERROR]");
        public static final String WARNING = getEmoji("⚠️", "[WARNING]");
        public static final String INFO = getEmoji("ℹ️", "[INFO]");
        public static final String ROCKET = getEmoji("🚀", "");
        public static final String FOLDER = getEmoji("📁", "");
        public static final String COMPUTER = getEmoji("💻", "[CMD]");
        public static final String TOOL = getEmoji("🔧", "[TOOL]");
        public static final String ART = getEmoji("🎨", "[STYLE]");
        public static final String WAVE = getEmoji("👋", "[BYE]");
        public static final String BULB = getEmoji("💡", "[TIP]");
    }
    
    /**
     * Print a message with Windows-compatible formatting
     */
    public static void printMessage(String message) {
        System.out.println(message);
    }
    
    /**
     * Print a status message with appropriate symbol
     */
    public static void printStatus(String type, String message) {
        String symbol;
        switch (type.toLowerCase()) {
            case "success": symbol = Symbols.SUCCESS; break;
            case "error": symbol = Symbols.ERROR; break;
            case "warning": symbol = Symbols.WARNING; break;
            case "info": symbol = Symbols.INFO; break;
            default: symbol = Symbols.INFO; break;
        }
        System.out.println(symbol + " " + message);
    }
    
    /**
     * Terminal capability detection utilities
     */
    public static class TerminalCapabilities {
        
        /**
         * Detect if we're running in Windows Terminal
         */
        public static boolean isWindowsTerminal() {
            return IS_WINDOWS_TERMINAL;
        }
        
        /**
         * Detect if we're running in VS Code integrated terminal
         */
        public static boolean isVSCodeTerminal() {
            return System.getenv("VSCODE_INJECTION") != null || 
                   "vscode".equals(System.getenv("TERM_PROGRAM"));
        }
        
        /**
         * Detect if we're running in ConEmu or cmder
         */
        public static boolean isConEmu() {
            return System.getenv("ConEmuPID") != null || 
                   System.getenv("CMDER_ROOT") != null;
        }
        
        /**
         * Detect if we're running in Git Bash/MSYS2/Cygwin
         */
        public static boolean isUnixLikeTerminal() {
            String term = System.getenv("TERM");
            return term != null && (term.startsWith("xterm") || 
                                  term.startsWith("cygwin") || 
                                  term.startsWith("msys"));
        }
        
        /**
         * Detect if we're running in old Windows Console Host
         */
        public static boolean isLegacyConsoleHost() {
            return IS_CONSOLE_HOST;
        }
        
        /**
         * Detect PowerShell version and capabilities
         */
        public static int getPowerShellMajorVersion() {
            String psVersion = System.getenv("PSVersion");
            if (psVersion != null) {
                try {
                    return Integer.parseInt(psVersion.split("\\.")[0]);
                } catch (Exception ignored) {}
            }
            return 0;
        }
        
        /**
         * Check if current terminal is considered "dumb" (no advanced features)
         */
        public static boolean isDumbTerminal() {
            String term = System.getenv("TERM");
            return "dumb".equals(term);
        }
        
        /**
         * Get a human-readable terminal type description
         */
        public static String getTerminalDescription() {
            if (isWindowsTerminal()) {
                return "Windows Terminal";
            } else if (isVSCodeTerminal()) {
                return "VS Code Integrated Terminal";
            } else if (isConEmu()) {
                return "ConEmu/cmder";
            } else if (isDumbTerminal()) {
                return "Dumb Terminal";
            } else if (isLegacyConsoleHost()) {
                return "Windows Console Host (Legacy)";
            } else {
                // Check for specific TERM_PROGRAM values
                String termProgram = System.getenv("TERM_PROGRAM");
                if (termProgram != null) {
                    switch (termProgram.toLowerCase()) {
                        case "warpterminal":
                            return "Warp Terminal";
                        case "hyper":
                            return "Hyper Terminal";
                        case "iterm.app":
                            return "iTerm2";
                        default:
                            return termProgram + " Terminal";
                    }
                }
                
                // Check TERM variable
                String term = System.getenv("TERM");
                if (term != null) {
                    if (isUnixLikeTerminal()) {
                        return "Unix-like Terminal (" + term + ")";
                    } else {
                        return "Terminal (" + term + ")";
                    }
                }
                
                return "Unknown Terminal";
            }
        }
        
        /**
         * Get capability score (0-10) for emoji/Unicode support
         */
        public static int getEmojiSupportScore() {
            if (isDumbTerminal()) return 0;
            if (isWindowsTerminal()) return 10;
            if (isVSCodeTerminal()) return 9;
            if (isConEmu()) return 8;
            if (isUnixLikeTerminal()) return 9;
            if (getPowerShellMajorVersion() >= 7) return 7;
            if (getPowerShellMajorVersion() >= 6) return 6;
            if (isLegacyConsoleHost() && supportsModernConsole()) return 4;
            if (isLegacyConsoleHost()) return 1;
            return 5; // Unknown but probably decent
        }
        
        /**
         * Get a diagnostic report of terminal capabilities
         */
        public static String getCapabilityReport() {
            StringBuilder report = new StringBuilder();
            report.append("Terminal: ").append(getTerminalDescription()).append("\n");
            report.append("Emoji Support: ").append(supportsEmojis() ? "Yes" : "No");
            if (supportsEmojis()) {
                report.append(" (Score: ").append(getEmojiSupportScore()).append("/10)");
            }
            report.append("\n");
            report.append("Color Support: ").append(supportsColors() ? "Yes" : "No").append("\n");
            report.append("OS: ").append(System.getProperty("os.name")).append("\n");
            report.append("OS Version: ").append(System.getProperty("os.version")).append("\n");
            
            String term = System.getenv("TERM");
            if (term != null) {
                report.append("TERM: ").append(term).append("\n");
            }
            
            String colorTerm = System.getenv("COLORTERM");
            if (colorTerm != null) {
                report.append("COLORTERM: ").append(colorTerm).append("\n");
            }
            
            if (getPowerShellMajorVersion() > 0) {
                report.append("PowerShell Version: ").append(getPowerShellMajorVersion()).append("\n");
            }
            
            return report.toString();
        }
    }
}
