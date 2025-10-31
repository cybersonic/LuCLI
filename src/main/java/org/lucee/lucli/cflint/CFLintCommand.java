package org.lucee.lucli.cflint;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.lucee.lucli.StringOutput;

/**
 * CFLint command integration for LuCLI
 * 
 * This class provides CFML linting functionality by integrating with the CFLint library.
 * It follows the LuCLI command pattern: lucli lint <subcommand> [options] [files/directories]
 * 
 * CFLint is loaded dynamically on first use via CFLintDownloader.
 */
public class CFLintCommand {
    
    private static final String COMMAND_NAME = "cflint";
    
    /**
     * Main entry point for lint commands
     */
    public boolean handleLintCommand(String input) {
        String[] parts = input.trim().split("\\s+");
        
        if (parts.length < 2) {
            printLintHelp();
            return true;
        }
        
        String subCommand = parts[1].toLowerCase();
        
        switch (subCommand) {
            case "check":
            case "analyze":
                return handleLintCheck(Arrays.copyOfRange(parts, 2, parts.length));
            case "rules":
            case "list-rules":
                return handleListRules(Arrays.copyOfRange(parts, 2, parts.length));
            case "config":
                return handleConfig(Arrays.copyOfRange(parts, 2, parts.length));
            case "status":
                return handleStatus();
            case "help":
            case "-h":
            case "--help":
                printLintHelp();
                return true;
            default:
                // If no subcommand recognized, treat as file/directory to lint
                return handleLintCheck(Arrays.copyOfRange(parts, 1, parts.length));
        }
    }
    
    /**
     * Handle status command - show CFLint availability
     */
    private boolean handleStatus() {
        System.out.println("🔍 CFLint Status for LuCLI");
        System.out.println("═".repeat(50));
        System.out.println(CFLintDownloader.getCFLintStatus());
        System.out.println();
        System.out.println("📁 CFLint directory: " + CFLintDownloader.getCFLintLibsDir());
        System.out.println("📄 Expected JAR path: " + CFLintDownloader.getCFLintJarPath());
        
        if (CFLintDownloader.isCFLintLoaded()) {
            System.out.println("✅ CFLint is currently loaded in memory");
        } else {
            System.out.println("⏹️ CFLint is not currently loaded");
        }
        
        return true;
    }
    
    /**
     * Handle lint check command
     */
    private boolean handleLintCheck(String[] args) {
        try {
            // Ensure CFLint is available, downloading if necessary
            if (!ensureCFLintAvailable()) {
                return true;
            }
            
            LintOptions options = parseLintOptions(args);
            
            if (options.files.isEmpty()) {
                System.err.println("Error: No files or directories specified for linting");
                printLintUsage();
                return true;
            }
            
            // Get CFLint instance via dynamic loading
            Object cfLint = CFLintDownloader.getCFLintInstance();
            Class<?> cfLintClass = CFLintDownloader.getCFLintClass();
            
            // Check if we're using CFLintAPI or CFLint directly
            boolean usingAPI = cfLintClass.getName().equals("com.cflint.api.CFLintAPI");
            
            if (!usingAPI) {
                // Original CFLint class - apply configuration
                Class<?> cfConfigClass = cfLintClass.getClassLoader().loadClass("com.cflint.config.CFLintConfiguration");
                Object config = cfConfigClass.getDeclaredConstructor().newInstance();
                
                // Apply configuration options
                if (options.configFile != null) {
                    try {
                        Method createFromFile = cfConfigClass.getMethod("createFromFile", File.class);
                        config = createFromFile.invoke(null, new File(options.configFile));
                    } catch (Exception e) {
                        System.err.println("Warning: Could not load config file: " + options.configFile);
                        System.err.println("Using default configuration");
                    }
                }
                
                // Set configuration via reflection for CFLint
                try {
                    Class<?> configRuntimeClass = cfLintClass.getClassLoader().loadClass("com.cflint.config.ConfigRuntime");
                    Object configRuntime = configRuntimeClass.getDeclaredConstructor().newInstance();
                    Method setConfigMethod = cfLintClass.getMethod("setConfiguration", cfConfigClass, configRuntimeClass);
                    setConfigMethod.invoke(cfLint, config, configRuntime);
                } catch (Exception e) {
                    // Fallback: try setConfiguration with just CFLintConfiguration
                    try {
                        Method setConfigMethod = cfLintClass.getMethod("setConfiguration", cfConfigClass);
                        setConfigMethod.invoke(cfLint, config);
                    } catch (Exception e2) {
                        System.err.println("Warning: Could not set configuration: " + e2.getMessage());
                    }
                }
            } else {
                // CFLintAPI - configuration is handled internally
                if (options.configFile != null) {
                    System.err.println("Warning: Custom config files not supported with CFLintAPI. Using default configuration.");
                }
            }
            
            // Output results using reflection
            Object result;
            if (usingAPI) {
                // CFLintAPI uses scan() method which returns CFLintResult directly
                List<String> filesList = new ArrayList<>(options.files);
                Method scanMethod = cfLintClass.getMethod("scan", List.class);
                result = scanMethod.invoke(cfLint, filesList);
            } else {
                // Process each file/directory using reflection for CFLint
                for (String target : options.files) {
                    processTargetReflective(cfLint, cfLintClass, target, options);
                }
                
                // CFLint uses getResult() method
                Method getResultMethod = cfLintClass.getMethod("getResult");
                result = getResultMethod.invoke(cfLint);
            }
            outputResultsReflective(result, options);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Error during linting: " + e.getMessage());
            if (System.getProperty("lucli.debug") != null) {
                e.printStackTrace();
            }
            return true;
        }
    }
    
    /**
     * Ensure CFLint is available, with user-friendly error handling
     */
    private boolean ensureCFLintAvailable() {
        try {
            boolean available = CFLintDownloader.ensureCFLintAvailable();
            if (!available) {
                printCFLintUnavailableMessage();
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.println("❌ Error downloading or loading CFLint: " + e.getMessage());
            System.err.println("💡 Try running 'lucli lint status' to check CFLint availability");
            return false;
        }
    }
    
    /**
     * Process a file or directory target using reflection
     */
    private void processTargetReflective(Object cfLint, Class<?> cfLintClass, String target, LintOptions options) {
        Path path = Paths.get(target);
        
        if (!Files.exists(path)) {
            System.err.println("Warning: Path does not exist: " + target);
            return;
        }
        
        try {
            if (Files.isDirectory(path)) {
                processDirectoryReflective(cfLint, cfLintClass, path, options);
            } else if (Files.isRegularFile(path)) {
                processFileReflective(cfLint, cfLintClass, path, options);
            }
        } catch (IOException e) {
            System.err.println("Error processing " + target + ": " + e.getMessage());
        }
    }
    
    /**
     * Process a directory recursively using reflection
     */
    private void processDirectoryReflective(Object cfLint, Class<?> cfLintClass, Path directory, LintOptions options) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                 .filter(this::isCFMLFile)
                 .forEach(file -> {
                     try {
                         processFileReflective(cfLint, cfLintClass, file, options);
                     } catch (IOException e) {
                         System.err.println("Error processing file " + file + ": " + e.getMessage());
                     }
                 });
        }
    }
    
    /**
     * Process a single file using reflection
     */
    private void processFileReflective(Object cfLint, Class<?> cfLintClass, Path file, LintOptions options) throws IOException {
        if (!isCFMLFile(file)) {
            if (options.verbose) {
                System.out.println("Skipping non-CFML file: " + file);
            }
            return;
        }
        
        if (options.verbose) {
            System.out.println("Linting: " + file);
        }
        
        String content = Files.readString(file);
        String filename = file.toString();
        
        try {
            Method processMethod = cfLintClass.getMethod("process", String.class, String.class);
            if (filename.toLowerCase().endsWith(".cfm") || filename.toLowerCase().endsWith(".cfml") || 
                filename.toLowerCase().endsWith(".cfc") || filename.toLowerCase().endsWith(".cfs")) {
                processMethod.invoke(cfLint, content, filename);
            }
        } catch (Exception e) {
            Throwable cause = e;
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                cause = e.getCause();
            }
            System.err.println("Error linting " + filename + ": " + cause.getMessage());
        }
    }
    
    /**
     * Check if a file is a CFML file
     */
    private boolean isCFMLFile(Path file) {
        String filename = file.toString().toLowerCase();
        return filename.endsWith(".cfm") || 
               filename.endsWith(".cfml") || 
               filename.endsWith(".cfc") || 
               filename.endsWith(".cfs");
    }
    
    /**
     * Output lint results using reflection
     */
    private void outputResultsReflective(Object result, LintOptions options) {
        try {
            switch (options.outputFormat.toLowerCase()) {
                case "json":
                    outputJSONReflective(result);
                    break;
                case "xml":
                    outputXMLReflective(result);
                    break;
                case "text":
                case "console":
                default:
                    outputTextReflective(result, options);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error formatting output: " + e.getMessage());
        }
    }
    
    /**
     * Output results in text format using reflection
     */
    private void outputTextReflective(Object result, LintOptions options) {
        try {
            // Get issues list via reflection
            Method getIssuesMethod = result.getClass().getMethod("getIssues");
            Object issuesList = getIssuesMethod.invoke(result);
            
            // Get size of issues list
            Method sizeMethod = issuesList.getClass().getMethod("size");
            Integer totalIssues = (Integer) sizeMethod.invoke(issuesList);
            
            if (totalIssues == 0) {
                System.out.println("✅ No linting issues found!");
                return;
            }
            
            System.out.println("\n🔍 CFLint Analysis Results:");
            System.out.println("═".repeat(50));
            
            // Get iterator for issues
            try {
                Method iteratorMethod = issuesList.getClass().getMethod("iterator");
                Object iterator = iteratorMethod.invoke(issuesList);
                Method hasNextMethod = iterator.getClass().getMethod("hasNext");
                Method nextMethod = iterator.getClass().getMethod("next");
                
                while ((Boolean) hasNextMethod.invoke(iterator)) {
                    Object issue = nextMethod.invoke(iterator);
                    
                    // Extract issue details via reflection
                    Method getFileMethod = issue.getClass().getMethod("getFile");
                    Method getLineMethod = issue.getClass().getMethod("getLine");
                    Method getMessageMethod = issue.getClass().getMethod("getMessage");
                    Method getSeverityMethod = issue.getClass().getMethod("getSeverity");
                    Method getRuleMethod = issue.getClass().getMethod("getRule");
                    
                    String file = (String) getFileMethod.invoke(issue);
                    String line = String.valueOf(getLineMethod.invoke(issue));
                    String message = (String) getMessageMethod.invoke(issue);
                    String severity = (String) getSeverityMethod.invoke(issue);
                    String rule = (String) getRuleMethod.invoke(issue);
                    
                    String severityIcon = getSeverityIcon(severity);
                    
                    // Output issue
                    System.out.println(severityIcon + " " + severity + ": " + message);
                    System.out.println("   File: " + file + " (line " + line + ")");
                    if (options.showRule) {
                        System.out.println("   Rule: " + rule);
                    }
                    System.out.println();
                }
            } catch (Exception e) {
                // Fallback if we can't iterate through issues
                System.out.println("💡 Found " + totalIssues + " issues. Run with --format json for details.");
            }
            
            // Summary
            System.out.println("═".repeat(50));
            System.out.printf("📊 Total issues found: %d\n", totalIssues);
        } catch (Exception e) {
            System.err.println("Error processing results: " + e.getMessage());
        }
    }
    
    /**
     * Get icon for severity level
     */
    private String getSeverityIcon(String severity) {
        if (severity == null) return "📝";
        
        switch (severity.toUpperCase()) {
            case "ERROR": return "❌";
            case "WARNING": return "⚠️";
            case "INFO": return "ℹ️";
            default: return "📝";
        }
    }
    
    /**
     * Output results in JSON format using reflection
     */
    private void outputJSONReflective(Object result) {
        try {
            StringWriter writer = new StringWriter();
            Method writeJsonMethod = null;
            
            // Try different method signatures for writeJson
            try {
                writeJsonMethod = result.getClass().getMethod("writeJson", java.io.Writer.class);
            } catch (NoSuchMethodException e1) {
                try {
                    writeJsonMethod = result.getClass().getMethod("writeJSON", java.io.Writer.class);
                } catch (NoSuchMethodException e2) {
                    try {
                        writeJsonMethod = result.getClass().getMethod("outputJSON", java.io.Writer.class);
                    } catch (NoSuchMethodException e3) {
                        // Try methods that return String instead
                        try {
                            Method toJsonMethod = result.getClass().getMethod("toJson");
                            Object jsonResult = toJsonMethod.invoke(result);
                            System.out.println(jsonResult.toString());
                            return;
                        } catch (NoSuchMethodException e4) {
                            try {
                                Method toJsonStringMethod = result.getClass().getMethod("toJsonString");
                                Object jsonResult = toJsonStringMethod.invoke(result);
                                System.out.println(jsonResult.toString());
                                return;
                            } catch (NoSuchMethodException e5) {
                                // Fall back to manual JSON construction
                                outputManualJSON(result);
                                return;
                            }
                        }
                    }
                }
            }
            
            if (writeJsonMethod != null) {
                writeJsonMethod.invoke(result, writer);
                System.out.println(writer.toString());
            }
            
        } catch (Exception e) {
            System.err.println("Error outputting JSON: " + e.getMessage());
            // Fallback to manual JSON output
            try {
                outputManualJSON(result);
            } catch (Exception e2) {
                System.err.println("Also failed to create manual JSON: " + e2.getMessage());
                if (System.getProperty("lucli.debug") != null) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * Output results in JSON format manually by extracting data via reflection
     */
    private void outputManualJSON(Object result) {
        try {
            // Get issues list via reflection
            Method getIssuesMethod = result.getClass().getMethod("getIssues");
            Object issuesList = getIssuesMethod.invoke(result);
            
            // Get size of issues list
            Method sizeMethod = issuesList.getClass().getMethod("size");
            Integer totalIssues = (Integer) sizeMethod.invoke(issuesList);
            
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"issues\": [\n");
            
            // Get iterator for issues
            Method iteratorMethod = issuesList.getClass().getMethod("iterator");
            Object iterator = iteratorMethod.invoke(issuesList);
            Method hasNextMethod = iterator.getClass().getMethod("hasNext");
            Method nextMethod = iterator.getClass().getMethod("next");
            
            boolean first = true;
            while ((Boolean) hasNextMethod.invoke(iterator)) {
                Object issue = nextMethod.invoke(iterator);
                
                if (!first) {
                    json.append(",\n");
                }
                first = false;
                
                // Extract issue details via reflection
                Method getFileMethod = issue.getClass().getMethod("getFile");
                Method getLineMethod = issue.getClass().getMethod("getLine");
                Method getColumnMethod = null;
                try {
                    getColumnMethod = issue.getClass().getMethod("getColumn");
                } catch (NoSuchMethodException e) {
                    // Column might not be available
                }
                Method getMessageMethod = issue.getClass().getMethod("getMessage");
                Method getSeverityMethod = issue.getClass().getMethod("getSeverity");
                Method getRuleMethod = issue.getClass().getMethod("getRule");
                
                String file = (String) getFileMethod.invoke(issue);
                Object lineObj = getLineMethod.invoke(issue);
                String message = (String) getMessageMethod.invoke(issue);
                String severity = (String) getSeverityMethod.invoke(issue);
                String rule = (String) getRuleMethod.invoke(issue);
                
                json.append("    {\n");
                json.append("      \"file\": \"").append(escapeJson(file)).append("\",\n");
                json.append("      \"line\": ").append(lineObj).append(",\n");
                
                if (getColumnMethod != null) {
                    try {
                        Object columnObj = getColumnMethod.invoke(issue);
                        json.append("      \"column\": ").append(columnObj).append(",\n");
                    } catch (Exception e) {
                        // Ignore column if not available
                    }
                }
                
                json.append("      \"message\": \"").append(escapeJson(message)).append("\",\n");
                json.append("      \"severity\": \"").append(escapeJson(severity)).append("\",\n");
                json.append("      \"rule\": \"").append(escapeJson(rule)).append("\"\n");
                json.append("    }");
            }
            
            json.append("\n  ],\n");
            json.append("  \"summary\": {\n");
            json.append("    \"totalIssues\": ").append(totalIssues).append("\n");
            json.append("  }\n");
            json.append("}\n");
            
            System.out.println(json.toString());
            
        } catch (Exception e) {
            System.err.println("Error creating manual JSON output: " + e.getMessage());
            if (System.getProperty("lucli.debug") != null) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Escape JSON string values
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Output results in XML format using reflection
     */
    private void outputXMLReflective(Object result) {
        try {
            Method writeXmlMethod = result.getClass().getMethod("writeXml", java.io.Writer.class);
            StringWriter writer = new StringWriter();
            writeXmlMethod.invoke(result, writer);
            System.out.println(writer.toString());
        } catch (Exception e) {
            System.err.println("Error outputting XML: " + e.getMessage());
        }
    }
    
    /**
     * Handle list rules command
     */
    private boolean handleListRules(String[] args) {
        try {
            // Try to ensure CFLint is available
            if (!ensureCFLintAvailable()) {
                System.out.println(StringOutput.loadText("/text/cflint-rules-unavailable.txt"));
                return true;
            }
            
            try {
                // This is a placeholder - the actual rule listing would depend on CFLint's API
                System.out.println(StringOutput.loadText("/text/cflint-rules-available.txt"));
            } catch (Exception e) {
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("ERROR_MESSAGE", e.getMessage());
                System.out.println(StringOutput.loadTextWithPlaceholders("/text/cflint-rules-error.txt", placeholders));
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Error listing rules: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * Handle config command
     */
    private boolean handleConfig(String[] args) {
        if (args.length == 0) {
            System.out.println(StringOutput.loadText("/text/cflint-config-help.txt"));
        } else if ("init".equals(args[0])) {
            // Create a basic .cflintrc file
            return createDefaultConfig();
        }
        
        return true;
    }
    
    /**
     * Create a default .cflintrc configuration file
     */
    private boolean createDefaultConfig() {
        try {
            Path configPath = Paths.get(".cflintrc");
            
            if (Files.exists(configPath)) {
                System.out.println("⚠️  .cflintrc already exists. Use --force to overwrite.");
                return true;
            }
            
            String defaultConfig = StringOutput.loadText("/text/cflint-default-config.json");
            
            Files.write(configPath, defaultConfig.getBytes());
            System.out.println("✅ Created .cflintrc configuration file");
            return true;
            
        } catch (IOException e) {
            System.err.println("Error creating configuration file: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * Parse lint command options
     */
    private LintOptions parseLintOptions(String[] args) {
        LintOptions options = new LintOptions();
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.equals("--format") || arg.equals("-f")) {
                if (i + 1 < args.length) {
                    options.outputFormat = args[++i];
                }
            } else if (arg.equals("--config") || arg.equals("-c")) {
                if (i + 1 < args.length) {
                    options.configFile = args[++i];
                }
            } else if (arg.equals("--verbose") || arg.equals("-v")) {
                options.verbose = true;
            } else if (arg.equals("--show-rule") || arg.equals("-r")) {
                options.showRule = true;
            } else if (arg.equals("--quiet") || arg.equals("-q")) {
                options.quiet = true;
            } else if (!arg.startsWith("-")) {
                options.files.add(arg);
            }
        }
        
        return options;
    }
    
    /**
     * Print lint help
     */
    private void printLintHelp() {
        System.out.println(StringOutput.loadText("/text/cflint-help.txt"));
    }
    
    /**
     * Print lint usage
     */
    private void printLintUsage() {
        System.out.println(StringOutput.loadText("/text/cflint-usage.txt"));
    }
    
    /**
     * Print CFLint unavailable message
     */
    private void printCFLintUnavailableMessage() {
        System.out.println(StringOutput.loadText("/text/cflint-unavailable.txt"));
    }
    
    /**
     * Options class for lint command
     */
    private static class LintOptions {
        String outputFormat = "text";
        String configFile = null;
        boolean verbose = false;
        boolean quiet = false;
        boolean showRule = false;
        List<String> files = new ArrayList<>();
    }
}
