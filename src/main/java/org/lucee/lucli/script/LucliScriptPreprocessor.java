package org.lucee.lucli.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.secrets.SecretStore;
import org.lucee.lucli.secrets.SecretStoreException;

/**
 * Preprocessor for .lucli and .luc script files.
 * 
 * Handles all text transformations before script execution:
 * 1. Line continuation (backslash at end of line)
 * 2. Comment stripping (lines starting with #, except directives)
 * 3. Environment blocks (#@env:dev ... #@end)
 * 4. Secret resolution (${secret:NAME})
 * 5. Placeholder substitution (${VAR}, ${_}, etc.)
 * 
 * All stages preserve line numbers by replacing removed content with empty strings.
 */
public class LucliScriptPreprocessor {

    // Pattern for secret placeholders: ${secret:NAME}
    private static final Pattern SECRET_PATTERN = Pattern.compile("\\$\\{secret:([^}]+)\\}");
    
    // Common environment shorthand aliases
    private static final Set<String> SHORTHAND_ENVS = new HashSet<>(Arrays.asList(
        "dev", "development",
        "staging", "stage",
        "prod", "production",
        "test", "testing",
        "local"
    ));

    /**
     * Exception thrown when preprocessing fails due to syntax errors.
     */
    public static class PreprocessorException extends RuntimeException {
        private final int lineNumber;
        
        public PreprocessorException(String message, int lineNumber) {
            super(message + " (line " + lineNumber + ")");
            this.lineNumber = lineNumber;
        }
        
        public int getLineNumber() {
            return lineNumber;
        }
    }

    /**
     * Run the full preprocessing pipeline on script lines.
     * 
     * @param lines Raw lines from the script file
     * @param currentEnv Current environment (from --env flag or LUCLI_ENV), may be null
     * @param stringOutput StringOutput instance for placeholder resolution
     * @param secrets SecretStore for secret resolution, may be null if no secrets used
     * @return Preprocessed lines ready for execution
     * @throws PreprocessorException if syntax errors are found
     * @throws SecretStoreException if secret resolution fails
     */
    public static List<String> preprocess(List<String> lines, String currentEnv,
                                          StringOutput stringOutput, SecretStore secrets) 
            throws PreprocessorException, SecretStoreException {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> result = new ArrayList<>(lines);
        
        // Pipeline stages in order
        result = joinContinuationLines(result);
        result = stripComments(result);
        result = processEnvBlocks(result, currentEnv);
        
        if (secrets != null) {
            result = resolveSecrets(result, secrets);
        }
        
        if (stringOutput != null) {
            result = resolvePlaceholders(result, stringOutput);
        }
        
        return result;
    }

    /**
     * Stage 1: Join lines ending with backslash with the following line.
     * 
     * Example:
     *   server start \
     *     --version 6.2.2.91 \
     *     --env prod
     * Becomes:
     *   server start --version 6.2.2.91 --env prod
     *   (empty line)
     *   (empty line)
     */
    public static List<String> joinContinuationLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> result = new ArrayList<>();
        StringBuilder accumulated = new StringBuilder();
        int continuationCount = 0;
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmedRight = rtrim(line);
            
            if (trimmedRight.endsWith("\\")) {
                // Remove the backslash and accumulate
                String withoutBackslash = trimmedRight.substring(0, trimmedRight.length() - 1);
                if (accumulated.length() > 0) {
                    accumulated.append(" ");
                }
                accumulated.append(withoutBackslash.trim());
                continuationCount++;
            } else {
                // End of continuation or standalone line
                if (accumulated.length() > 0) {
                    accumulated.append(" ").append(line.trim());
                    result.add(accumulated.toString());
                    accumulated = new StringBuilder();
                    
                    // Add empty lines to preserve line numbers
                    for (int j = 0; j < continuationCount; j++) {
                        result.add("");
                    }
                    continuationCount = 0;
                } else {
                    result.add(line);
                }
            }
        }
        
        // Handle trailing continuation (incomplete)
        if (accumulated.length() > 0) {
            result.add(accumulated.toString());
            for (int j = 0; j < continuationCount; j++) {
                result.add("");
            }
        }
        
        return result;
    }

    /**
     * Stage 2: Strip comment lines (replace with empty string to preserve line numbers).
     * 
     * Lines starting with # are comments, EXCEPT:
     * - #@env:... (environment block start)
     * - #@end (environment block end)
     * - #@dev, #@prod, etc. (shorthand environment blocks)
     */
    public static List<String> stripComments(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> result = new ArrayList<>();
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.startsWith("#")) {
                // Check if it's a directive we should preserve
                if (isEnvDirective(trimmed)) {
                    result.add(line);
                } else {
                    // Regular comment - replace with empty to preserve line numbers
                    result.add("");
                }
            } else {
                result.add(line);
            }
        }
        
        return result;
    }

    /**
     * Stage 3: Process environment conditional blocks.
     * 
     * Syntax:
     *   #@env:dev           - Include if env is "dev"
     *   #@env:dev,staging   - Include if env is "dev" OR "staging"
     *   #@env:!prod         - Include if env is NOT "prod"
     *   #@dev               - Shorthand for #@env:dev
     *   #@end               - End of block
     * 
     * Non-matching blocks are replaced with empty lines to preserve line numbers.
     */
    public static List<String> processEnvBlocks(List<String> lines, String currentEnv) 
            throws PreprocessorException {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> result = new ArrayList<>();
        boolean inBlock = false;
        boolean blockMatches = false;
        int blockStartLine = -1;
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            int lineNum = i + 1; // 1-indexed for error messages
            
            // Check for block start
            if (isEnvBlockStart(trimmed)) {
                if (inBlock) {
                    throw new PreprocessorException(
                        "Nested #@env blocks are not allowed (block started at line " + blockStartLine + ")",
                        lineNum
                    );
                }
                
                inBlock = true;
                blockStartLine = lineNum;
                blockMatches = doesEnvMatch(trimmed, currentEnv);
                
                // Replace directive line with empty
                result.add("");
                continue;
            }
            
            // Check for block end
            if (trimmed.equalsIgnoreCase("#@end")) {
                if (!inBlock) {
                    throw new PreprocessorException(
                        "#@end without matching #@env block",
                        lineNum
                    );
                }
                
                inBlock = false;
                blockStartLine = -1;
                
                // Replace directive line with empty
                result.add("");
                continue;
            }
            
            // Inside a block
            if (inBlock) {
                if (blockMatches) {
                    result.add(line);
                } else {
                    // Non-matching block content - replace with empty
                    result.add("");
                }
            } else {
                // Outside any block - keep as is
                result.add(line);
            }
        }
        
        // Check for unclosed block
        if (inBlock) {
            throw new PreprocessorException(
                "Unclosed #@env block (started at line " + blockStartLine + ")",
                lines.size()
            );
        }
        
        return result;
    }

    /**
     * Stage 4: Resolve secret placeholders.
     * 
     * Replaces ${secret:NAME} with the actual secret value from the store.
     */
    public static List<String> resolveSecrets(List<String> lines, SecretStore secrets) 
            throws SecretStoreException {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        if (secrets == null) {
            return new ArrayList<>(lines);
        }
        
        List<String> result = new ArrayList<>();
        
        for (String line : lines) {
            if (line == null || line.isEmpty() || !line.contains("${secret:")) {
                result.add(line);
                continue;
            }
            
            Matcher matcher = SECRET_PATTERN.matcher(line);
            StringBuffer sb = new StringBuffer();
            
            while (matcher.find()) {
                String secretName = matcher.group(1).trim();
                java.util.Optional<char[]> value = secrets.get(secretName);
                
                if (value.isEmpty()) {
                    throw new SecretStoreException("Secret '" + secretName + "' not found");
                }
                
                String replacement = new String(value.get());
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            
            matcher.appendTail(sb);
            result.add(sb.toString());
        }
        
        return result;
    }

    /**
     * Stage 5: Resolve general placeholders via StringOutput.
     * 
     * Handles ${VAR}, ${_}, ${ENV_*}, and other StringOutput placeholders.
     */
    public static List<String> resolvePlaceholders(List<String> lines, StringOutput stringOutput) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        if (stringOutput == null) {
            return new ArrayList<>(lines);
        }
        
        List<String> result = new ArrayList<>();
        
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                result.add(line);
            } else {
                result.add(stringOutput.process(line));
            }
        }
        
        return result;
    }

    // ========================================
    // Helper methods
    // ========================================

    /**
     * Check if a line is an environment directive that should not be treated as a regular comment.
     */
    private static boolean isEnvDirective(String trimmed) {
        if (!trimmed.startsWith("#@")) {
            return false;
        }
        
        String directive = trimmed.substring(2).toLowerCase();
        
        // #@end
        if (directive.equals("end")) {
            return true;
        }
        
        // #@env:...
        if (directive.startsWith("env:")) {
            return true;
        }
        
        // #@dev, #@prod, etc. (shorthand)
        return SHORTHAND_ENVS.contains(directive);
    }

    /**
     * Check if a line starts an environment block.
     */
    private static boolean isEnvBlockStart(String trimmed) {
        if (!trimmed.startsWith("#@")) {
            return false;
        }
        
        String directive = trimmed.substring(2).toLowerCase();
        
        // #@env:...
        if (directive.startsWith("env:")) {
            return true;
        }
        
        // #@dev, #@prod, etc. (shorthand)
        return SHORTHAND_ENVS.contains(directive);
    }

    /**
     * Check if the current environment matches the block directive.
     */
    private static boolean doesEnvMatch(String trimmed, String currentEnv) {
        String directive = trimmed.substring(2); // Remove #@
        String envSpec;
        
        if (directive.toLowerCase().startsWith("env:")) {
            envSpec = directive.substring(4); // Remove "env:"
        } else {
            // Shorthand like #@dev
            envSpec = directive;
        }
        
        // Handle negation: !prod
        if (envSpec.startsWith("!")) {
            String negatedEnv = envSpec.substring(1).trim().toLowerCase();
            // If currentEnv is null, we're "not in prod" (or any other env), so negation matches
            // i.e., !prod means "run when NOT in prod", which includes the default/unset case
            if (currentEnv == null) {
                return true;
            }
            return !currentEnv.equalsIgnoreCase(negatedEnv);
        }
        
        // Handle multiple environments: dev,staging
        String[] envs = envSpec.split(",");
        for (String env : envs) {
            String trimmedEnv = env.trim().toLowerCase();
            if (trimmedEnv.isEmpty()) {
                continue;
            }
            if (currentEnv != null && currentEnv.equalsIgnoreCase(trimmedEnv)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Trim whitespace from the right side of a string only.
     */
    private static String rtrim(String s) {
        if (s == null) {
            return null;
        }
        int len = s.length();
        while (len > 0 && Character.isWhitespace(s.charAt(len - 1))) {
            len--;
        }
        return s.substring(0, len);
    }
}
