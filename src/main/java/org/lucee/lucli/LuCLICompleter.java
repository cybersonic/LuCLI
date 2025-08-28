package org.lucee.lucli;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tab completion for LuCLI commands and file paths
 */
public class LuCLICompleter implements Completer {
    private final CommandProcessor commandProcessor;
    private final String[] commands = {
        "ls", "dir", "cd", "pwd", "mkdir", "rmdir", "rm", "cp", "mv", 
        "cat", "edit", "touch", "find", "wc", "head", "tail", "cfml", "run", "prompt", 
        "help", "exit", "quit", "clear", "history", "env", "echo", "server", "lint"
    };
    
    public LuCLICompleter(CommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
    }
    
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        
        if (buffer.trim().isEmpty() || line.words().size() == 1) {
            // Complete commands
            String partial = line.words().isEmpty() ? "" : line.words().get(0);
            for (String command : commands) {
                if (command.startsWith(partial.toLowerCase())) {
                    candidates.add(new Candidate(command, command, null, null, null, null, true));
                }
            }
        } else {
            String command = line.words().get(0).toLowerCase();
            
            // Handle CFML function completion
            if ("cfml".equals(command)) {
                completeCFMLFunctions(line, candidates);
            }
            // Handle lint command completion
            else if ("lint".equals(command)) {
                completeLintCommand(line, candidates);
            }
            // Complete file paths for commands that take file arguments
            else if (isFileCommand(command)) {
                String partial = line.words().size() > 1 ? line.words().get(line.words().size() - 1) : "";
                completeFilePaths(partial, candidates, command);
            }
        }
    }
    
    private boolean isFileCommand(String command) {
        switch (command) {
            case "ls":
            case "dir":
            case "cd":
            case "cat":
            case "edit":
            case "cp":
            case "mv":
            case "rm":
            case "mkdir":
            case "rmdir":
            case "touch":
            case "find":
            case "wc":
            case "head":
            case "tail":
            case "run":
            case "lint":
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Context-aware file path completion based on the command being used
     */
    private void completeFilePaths(String partial, List<Candidate> candidates, String command) {
        try {
            Path currentDir = commandProcessor.getFileSystemState().getCurrentWorkingDirectory();
            Path basePath;
            String prefix = "";
            
            // Handle different path scenarios
            if (partial.isEmpty()) {
                basePath = currentDir;
            } else if (partial.equals("~")) {
                basePath = commandProcessor.getFileSystemState().getHomeDirectory();
                prefix = commandProcessor.getFileSystemState().getHomeDirectory().toString() + "/";
            } else if (partial.startsWith("~/")) {
                Path homePath = commandProcessor.getFileSystemState().getHomeDirectory();
                String relativePart = partial.substring(2);
                int lastSlash = relativePart.lastIndexOf('/');
                if (lastSlash >= 0) {
                    basePath = homePath.resolve(relativePart.substring(0, lastSlash));
                    prefix = homePath.toString() + "/" + relativePart.substring(0, lastSlash + 1);
                } else {
                    basePath = homePath;
                    prefix = homePath.toString() + "/";
                }
            } else if (partial.startsWith("/")) {
                // Absolute path
                int lastSlash = partial.lastIndexOf('/');
                if (lastSlash > 0) {
                    basePath = Path.of(partial.substring(0, lastSlash));
                    prefix = partial.substring(0, lastSlash + 1);
                } else {
                    basePath = Path.of("/");
                    prefix = "/";
                }
            } else {
                // Relative path
                int lastSlash = partial.lastIndexOf('/');
                if (lastSlash >= 0) {
                    basePath = currentDir.resolve(partial.substring(0, lastSlash));
                    prefix = partial.substring(0, lastSlash + 1);
                } else {
                    basePath = currentDir;
                }
            }
            
            if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
                return;
            }
            
            // Get the partial filename to match
            String filenamePartial = "";
            if (!partial.isEmpty()) {
                int lastSlash = partial.lastIndexOf('/');
                filenamePartial = lastSlash >= 0 ? partial.substring(lastSlash + 1) : partial;
                if (partial.startsWith("~/") && lastSlash < 0) {
                    filenamePartial = partial.substring(2);
                }
            }
            
            // Determine what types of files to show based on command
            boolean showDirectoriesOnly = "cd".equals(command) || "mkdir".equals(command) || "rmdir".equals(command);
            boolean showCFMLFilesOnly = "run".equals(command);
            
            // List files and directories
            final String finalFilenamePartial = filenamePartial;
            final String finalPrefix = prefix;
            
            try (Stream<Path> files = Files.list(basePath)) {
                files.filter(path -> {
                    String filename = path.getFileName().toString();
                    boolean nameMatches = filename.startsWith(finalFilenamePartial) && 
                           (finalFilenamePartial.startsWith(".") || !filename.startsWith("."));
                    
                    if (!nameMatches) return false;
                    
                    // Apply command-specific filters
                    if (showDirectoriesOnly) {
                        return Files.isDirectory(path);
                    }
                    
                    if (showCFMLFilesOnly) {
                        return Files.isDirectory(path) || 
                               filename.toLowerCase().endsWith(".cfm") ||
                               filename.toLowerCase().endsWith(".cfc") ||
                               filename.toLowerCase().endsWith(".cfs");
                    }
                    
                    return true;
                })
                .sorted((p1, p2) -> {
                    // Sort directories first, then files
                    boolean isDir1 = Files.isDirectory(p1);
                    boolean isDir2 = Files.isDirectory(p2);
                    
                    if (isDir1 && !isDir2) return -1;
                    if (!isDir1 && isDir2) return 1;
                    
                    return p1.getFileName().toString().compareToIgnoreCase(p2.getFileName().toString());
                })
                .forEach(path -> {
                    String filename = path.getFileName().toString();
                    String completion = finalPrefix + filename;
                    
                    if (Files.isDirectory(path)) {
                        completion += "/";
                        String displayValue = completion;
                        
                        // Add emoji for better visibility
                        if (commandProcessor.getSettings().showEmojis()) {
                            displayValue = "📁 " + completion;
                        }
                        
                        candidates.add(new Candidate(completion, displayValue, "directories", "Directory", null, null, false));
                    } else {
                        String displayValue = completion;
                        String description = "File";
                        
                        // Add emoji and description based on file type
                        if (commandProcessor.getSettings().showEmojis()) {
                            if (filename.toLowerCase().endsWith(".cfm") || 
                                filename.toLowerCase().endsWith(".cfc") ||
                                filename.toLowerCase().endsWith(".cfs")) {
                                displayValue = "⚡ " + completion;
                                description = "CFML File";
                            } else if (filename.toLowerCase().endsWith(".java")) {
                                displayValue = "☕ " + completion;
                                description = "Java File";
                            } else if (filename.toLowerCase().endsWith(".js")) {
                                displayValue = "🟨 " + completion;
                                description = "JavaScript File";
                            } else if (filename.toLowerCase().endsWith(".md")) {
                                displayValue = "📝 " + completion;
                                description = "Markdown File";
                            } else {
                                displayValue = "📄 " + completion;
                            }
                        }
                        
                        candidates.add(new Candidate(completion, displayValue, "files", description, null, null, true));
                    }
                });
            }
            
        } catch (IOException e) {
            // Ignore completion errors
        }
    }
    
    /**
     * Complete lint command and subcommands
     */
    private void completeLintCommand(ParsedLine line, List<Candidate> candidates) {
        List<String> words = line.words();
        
        if (words.size() == 2) {
            // Complete lint subcommands
            String[] lintSubcommands = {"check", "analyze", "rules", "list-rules", "config", "help"};
            String partial = words.get(1);
            
            for (String subcommand : lintSubcommands) {
                if (subcommand.startsWith(partial.toLowerCase())) {
                    String displayValue = subcommand;
                    String description = getLintSubcommandDescription(subcommand);
                    
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = getLintSubcommandEmoji(subcommand) + " " + subcommand;
                    }
                    
                    candidates.add(new Candidate(subcommand, displayValue, "lint-commands", description, null, null, true));
                }
            }
        } else if (words.size() >= 3) {
            // Complete files/directories for lint check commands
            String subcommand = words.get(1).toLowerCase();
            if ("check".equals(subcommand) || "analyze".equals(subcommand) || 
                (words.size() == 3 && !isLintSubcommand(words.get(1)))) {
                // Complete CFML files and directories for linting
                String partial = words.get(words.size() - 1);
                completeCFMLFilePaths(partial, candidates);
            } else if ("config".equals(subcommand) && words.size() == 3) {
                // Complete config subcommands
                String[] configSubcommands = {"init"};
                String partial = words.get(2);
                
                for (String configCmd : configSubcommands) {
                    if (configCmd.startsWith(partial.toLowerCase())) {
                        String displayValue = configCmd;
                        if (commandProcessor.getSettings().showEmojis()) {
                            displayValue = "🔧 " + configCmd;
                        }
                        candidates.add(new Candidate(configCmd, displayValue, "config-commands", "Initialize config file", null, null, true));
                    }
                }
            }
        }
    }
    
    /**
     * Check if a word is a lint subcommand
     */
    private boolean isLintSubcommand(String word) {
        String[] subcommands = {"check", "analyze", "rules", "list-rules", "config", "help"};
        for (String sub : subcommands) {
            if (sub.equals(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get description for lint subcommands
     */
    private String getLintSubcommandDescription(String subcommand) {
        switch (subcommand.toLowerCase()) {
            case "check":
            case "analyze":
                return "Lint CFML files or directories";
            case "rules":
            case "list-rules":
                return "List available linting rules";
            case "config":
                return "Show configuration help";
            case "help":
                return "Show lint command help";
            default:
                return "Lint subcommand";
        }
    }
    
    /**
     * Get emoji for lint subcommands
     */
    private String getLintSubcommandEmoji(String subcommand) {
        switch (subcommand.toLowerCase()) {
            case "check":
            case "analyze":
                return "🔍";
            case "rules":
            case "list-rules":
                return "📋";
            case "config":
                return "⚙️";
            case "help":
                return "❓";
            default:
                return "📝";
        }
    }
    
    /**
     * Complete CFML file paths for linting
     */
    private void completeCFMLFilePaths(String partial, List<Candidate> candidates) {
        try {
            Path currentDir = commandProcessor.getFileSystemState().getCurrentWorkingDirectory();
            Path basePath;
            String prefix = "";
            
            // Handle path parsing (similar to regular file completion)
            if (partial.isEmpty()) {
                basePath = currentDir;
            } else if (partial.startsWith("/")) {
                int lastSlash = partial.lastIndexOf('/');
                if (lastSlash > 0) {
                    basePath = Path.of(partial.substring(0, lastSlash));
                    prefix = partial.substring(0, lastSlash + 1);
                } else {
                    basePath = Path.of("/");
                    prefix = "/";
                }
            } else {
                int lastSlash = partial.lastIndexOf('/');
                if (lastSlash >= 0) {
                    basePath = currentDir.resolve(partial.substring(0, lastSlash));
                    prefix = partial.substring(0, lastSlash + 1);
                } else {
                    basePath = currentDir;
                }
            }
            
            if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
                return;
            }
            
            String filenamePartial = "";
            if (!partial.isEmpty()) {
                int lastSlash = partial.lastIndexOf('/');
                filenamePartial = lastSlash >= 0 ? partial.substring(lastSlash + 1) : partial;
            }
            
            final String finalFilenamePartial = filenamePartial;
            final String finalPrefix = prefix;
            
            try (Stream<Path> files = Files.list(basePath)) {
                files.filter(path -> {
                    String filename = path.getFileName().toString();
                    boolean nameMatches = filename.startsWith(finalFilenamePartial) && 
                           (finalFilenamePartial.startsWith(".") || !filename.startsWith("."));
                    
                    if (!nameMatches) return false;
                    
                    // Show directories and CFML files only
                    return Files.isDirectory(path) || 
                           filename.toLowerCase().endsWith(".cfm") ||
                           filename.toLowerCase().endsWith(".cfc") ||
                           filename.toLowerCase().endsWith(".cfs") ||
                           filename.toLowerCase().endsWith(".cfml");
                })
                .sorted((p1, p2) -> {
                    boolean isDir1 = Files.isDirectory(p1);
                    boolean isDir2 = Files.isDirectory(p2);
                    
                    if (isDir1 && !isDir2) return -1;
                    if (!isDir1 && isDir2) return 1;
                    
                    return p1.getFileName().toString().compareToIgnoreCase(p2.getFileName().toString());
                })
                .forEach(path -> {
                    String filename = path.getFileName().toString();
                    String completion = finalPrefix + filename;
                    
                    if (Files.isDirectory(path)) {
                        completion += "/";
                        String displayValue = completion;
                        
                        if (commandProcessor.getSettings().showEmojis()) {
                            displayValue = "📁 " + completion;
                        }
                        
                        candidates.add(new Candidate(completion, displayValue, "directories", "Directory", null, null, false));
                    } else {
                        String displayValue = completion;
                        
                        if (commandProcessor.getSettings().showEmojis()) {
                            displayValue = "⚡ " + completion;
                        }
                        
                        candidates.add(new Candidate(completion, displayValue, "cfml-files", "CFML File", null, null, true));
                    }
                });
            }
            
        } catch (IOException e) {
            // Ignore completion errors
        }
    }
    
    /**
     * Complete CFML functions for 'cfml' command
     */
    private void completeCFMLFunctions(ParsedLine line, List<Candidate> candidates) {
        // Get the CFML expression being typed
        String fullLine = line.line();
        
        // Extract everything after "cfml "
        if (fullLine.length() <= 5) { // "cfml " is 5 characters
            return;
        }
        
        String cfmlExpression = fullLine.substring(5); // Remove "cfml " prefix
        
        // Find the current function being typed (look for the last function call)
        String currentFunction = extractCurrentFunction(cfmlExpression);
        
        if (currentFunction != null && !currentFunction.trim().isEmpty()) {
            // Get CFML function completions
            List<Candidate> cfmlCandidates = CFMLCompleter.getCompletions(currentFunction);
            candidates.addAll(cfmlCandidates);
        }
    }
    
    /**
     * Extract the current function name being typed from a CFML expression
     */
    private String extractCurrentFunction(String cfmlExpression) {
        if (cfmlExpression == null || cfmlExpression.trim().isEmpty()) {
            return "";
        }
        
        // Simple approach: find the last word that could be a function name
        // This handles cases like:
        // "create" -> "create"
        // "dateFormat(now(), " -> "" (already complete function)
        // "listLen(myList) + create" -> "create"
        
        String trimmed = cfmlExpression.trim();
        
        // Split by common delimiters and operators
        String[] parts = trimmed.split("[\\s+\\-*/=<>!&|(),;\\[\\]{}.\"']+");
        
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1].trim();
            
            // Only return if it looks like a function name (starts with letter)
            if (!lastPart.isEmpty() && Character.isLetter(lastPart.charAt(0))) {
                return lastPart;
            }
        }
        
        return "";
    }
}
