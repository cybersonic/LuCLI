package org.lucee.lucli;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Basic CFML syntax highlighting for terminal display
 * Provides ANSI color codes for different CFML language elements
 */
public class CFMLSyntaxHighlighter {
    
    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String FUNCTION = "\u001B[36m";     // Cyan for functions
    public static final String KEYWORD = "\u001B[35m";      // Magenta for keywords
    public static final String STRING = "\u001B[32m";       // Green for strings
    public static final String NUMBER = "\u001B[33m";       // Yellow for numbers
    public static final String OPERATOR = "\u001B[31m";     // Red for operators
    public static final String COMMENT = "\u001B[90m";      // Gray for comments
    public static final String VARIABLE = "\u001B[37m";     // White for variables
    
    // CFML keywords
    private static final Set<String> KEYWORDS = new HashSet<>();
    static {
        KEYWORDS.add("if");
        KEYWORDS.add("else");
        KEYWORDS.add("elseif");
        KEYWORDS.add("for");
        KEYWORDS.add("while");
        KEYWORDS.add("do");
        KEYWORDS.add("switch");
        KEYWORDS.add("case");
        KEYWORDS.add("default");
        KEYWORDS.add("break");
        KEYWORDS.add("continue");
        KEYWORDS.add("return");
        KEYWORDS.add("try");
        KEYWORDS.add("catch");
        KEYWORDS.add("finally");
        KEYWORDS.add("throw");
        KEYWORDS.add("function");
        KEYWORDS.add("var");
        KEYWORDS.add("local");
        KEYWORDS.add("variables");
        KEYWORDS.add("arguments");
        KEYWORDS.add("this");
        KEYWORDS.add("super");
        KEYWORDS.add("true");
        KEYWORDS.add("false");
        KEYWORDS.add("null");
        KEYWORDS.add("component");
        KEYWORDS.add("interface");
        KEYWORDS.add("extends");
        KEYWORDS.add("implements");
        KEYWORDS.add("import");
        KEYWORDS.add("include");
        KEYWORDS.add("public");
        KEYWORDS.add("private");
        KEYWORDS.add("package");
        KEYWORDS.add("remote");
        KEYWORDS.add("required");
        KEYWORDS.add("new");
        KEYWORDS.add("query");
        KEYWORDS.add("struct");
        KEYWORDS.add("array");
    }
    
    // Common CFML functions (subset for highlighting)
    private static final Set<String> COMMON_FUNCTIONS = new HashSet<>();
    static {
        COMMON_FUNCTIONS.add("writeOutput");
        COMMON_FUNCTIONS.add("echo");
        COMMON_FUNCTIONS.add("now");
        COMMON_FUNCTIONS.add("dateFormat");
        COMMON_FUNCTIONS.add("timeFormat");
        COMMON_FUNCTIONS.add("createObject");
        COMMON_FUNCTIONS.add("structNew");
        COMMON_FUNCTIONS.add("arrayNew");
        COMMON_FUNCTIONS.add("queryNew");
        COMMON_FUNCTIONS.add("listToArray");
        COMMON_FUNCTIONS.add("arrayToList");
        COMMON_FUNCTIONS.add("structKeyExists");
        COMMON_FUNCTIONS.add("isDefined");
        COMMON_FUNCTIONS.add("isStruct");
        COMMON_FUNCTIONS.add("isArray");
        COMMON_FUNCTIONS.add("len");
        COMMON_FUNCTIONS.add("trim");
        COMMON_FUNCTIONS.add("ucase");
        COMMON_FUNCTIONS.add("lcase");
        COMMON_FUNCTIONS.add("replace");
        COMMON_FUNCTIONS.add("find");
        COMMON_FUNCTIONS.add("findNoCase");
    }
    
    // Patterns for syntax elements
    private static final Pattern STRING_PATTERN = Pattern.compile("([\"'])[^\"']*\\1");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\b\\w+(?=\\s*\\()");
    private static final Pattern OPERATOR_PATTERN = Pattern.compile("[+\\-*/=<>!&|]+");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("//.*$|/\\*.*?\\*/");
    
    /**
     * Apply basic syntax highlighting to CFML code
     * @param cfmlCode The CFML code to highlight
     * @param enabled Whether highlighting is enabled
     * @return Highlighted code with ANSI color codes
     */
    public static String highlight(String cfmlCode, boolean enabled) {
        if (!enabled || cfmlCode == null || cfmlCode.trim().isEmpty()) {
            return cfmlCode;
        }
        
        String result = cfmlCode;
        
        try {
            // Highlight strings first to avoid interfering with other patterns
            result = STRING_PATTERN.matcher(result).replaceAll(STRING + "$0" + RESET);
            
            // Highlight comments
            result = COMMENT_PATTERN.matcher(result).replaceAll(COMMENT + "$0" + RESET);
            
            // Highlight numbers
            result = NUMBER_PATTERN.matcher(result).replaceAll(NUMBER + "$0" + RESET);
            
            // Highlight operators
            result = OPERATOR_PATTERN.matcher(result).replaceAll(OPERATOR + "$0" + RESET);
            
            // Highlight functions
            result = FUNCTION_PATTERN.matcher(result).replaceAll(match -> {
                String functionName = match.group();
                if (COMMON_FUNCTIONS.contains(functionName) || CFMLCompleter.hasFunction(functionName)) {
                    return FUNCTION + functionName + RESET;
                }
                return functionName;
            });
            
            // Highlight keywords (do this last to avoid conflicts)
            for (String keyword : KEYWORDS) {
                Pattern keywordPattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE);
                result = keywordPattern.matcher(result).replaceAll(KEYWORD + keyword + RESET);
            }
            
        } catch (Exception e) {
            // If highlighting fails, return original code
            return cfmlCode;
        }
        
        return result;
    }
    
    /**
     * Remove all ANSI color codes from text
     * @param text Text with ANSI codes
     * @return Plain text without color codes
     */
    public static String stripColors(String text) {
        if (text == null) return null;
        return text.replaceAll("\u001B\\[[;\\d]*m", "");
    }
    
    /**
     * Check if syntax highlighting is supported in the current terminal
     * @return true if highlighting should be enabled
     */
    public static boolean isHighlightingSupported() {
        String term = System.getenv("TERM");
        String colorTerm = System.getenv("COLORTERM");
        
        return (term != null && (term.contains("color") || term.contains("256") || term.contains("xterm"))) ||
               (colorTerm != null && !colorTerm.isEmpty());
    }
}
