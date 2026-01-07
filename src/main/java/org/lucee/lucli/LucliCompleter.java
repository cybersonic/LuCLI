package org.lucee.lucli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.lucee.lucli.modules.ModuleCommand;

import lucee.runtime.exp.PageException;
import lucee.runtime.type.Array;
import lucee.runtime.type.Struct;

/**
 * Tab completion for LuCLI internal terminal commands and file paths
 * Note: PicoCLI provides completion for: server, modules, cfml, help
 * This completer handles: internal terminal commands (ls, cd, pwd, etc.) and context-specific completions
 */
public class LucliCompleter implements Completer {
    private final CommandProcessor commandProcessor;
    
    // Internal terminal commands and high-level LuCLI commands available in interactive mode
    private final String[] internalCommands = {
        // Shell-like file system commands
        "ls", "dir", "cd", "pwd", "mkdir", "rmdir", "rm", "cp", "mv", 
        "cat", "edit", "touch", "find", "wc", "head", "tail", "run", "prompt", 
        // Terminal/session commands
        "exit", "quit", "clear", "history", "env", "echo",
        // LuCLI top-level commands that unifiedExecutor handles
        // "server", "modules", "cfml", "lint", "help"
    };
    
    public LucliCompleter(CommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
    }
    
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        
        if (buffer.trim().isEmpty() || line.words().size() == 1) {
            // Complete internal terminal commands (PicoCLI handles server, modules, cfml, help)
            String partial = line.words().isEmpty() ? "" : line.words().get(0);
            String lowerPartial = partial.toLowerCase();
            
            // 1) Built-in LuCLI / internal commands
            for (String command : internalCommands) {
                if (command.startsWith(lowerPartial)) {
                    candidates.add(new Candidate(command, command, "internal-commands", "LuCLI command", null, null, true));
                }
            }
            
            // 2) Top-level LuCLI modules (installed under modules directory)
            //    These behave like first-class commands in the terminal (e.g. `lint`).
            Map<String, Path> modules = listModules();
            for (String moduleName : modules.keySet()) {
                if (moduleName.startsWith(lowerPartial) && !isInternalCommand(moduleName)) {
                    String displayValue = moduleName;
                    String description = "LuCLI module";
                    
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = "📦 " + moduleName;
                    }
                    
                    candidates.add(new Candidate(moduleName, displayValue, "modules", description, null, null, true));
                }
            }
        } else {
            String command = line.words().get(0).toLowerCase();
            
            // Handle CFML function completion (only for internal 'cfml' usage, not PicoCLI's CfmlCommand)
            if ("cfml".equals(command)) {
                completeCFMLFunctions(line, candidates);
            }
            // Handle server command completion (context-specific like server names, versions, config keys)
            // PicoCLI handles basic structure, this adds context-aware completions
            else if ("server".equals(command)) {
                completeServerCommand(line, candidates);
            }
            // Handle lint command completion (loaded as a module)
            // else if ("lint".equals(command)) {
            //     completeLintCommand(line, candidates);
            // }
            // Complete file paths for commands that take file arguments
            else if (isModule(command)) {
                completeModuleCommand(line, candidates, command);
                // TODO: Add modules with isModule() for it and then we can get the functions and then arguments for that function
            }
            else if (isFileCommand(command)) {
                String partial = line.words().size() > 1 ? line.words().get(line.words().size() - 1) : "";
                completeFilePaths(partial, candidates, command);
            }
        }
    }
    

    private void completeModuleCommand(ParsedLine line, List<Candidate> candidates, String command){
        Array metadata = null;
        try {
            metadata = LuceeScriptEngine.getInstance(false, false).getComponentMetadata("modules." + command + ".Module" );
            // System.out.println("Metadata: " + metadata.toString());lint
      
            if(line.toString().isEmpty() || line.words().size() == 1) {
                // Get the module functions
                addFunctionCandidatesFromMetadata(candidates, metadata);
            }
      
            
            return;
        }
        catch(Exception e) {
            System.err.println(e.getMessage());
            return;   // Ignore completion errors
        }
       // Need to be smarter here but let's start as we mean to go on. WE can get teh methods if the list is just the command. 
        // We are 
        // if(line.toString().isEmpty() || line.words().size() ==1) {
        //     // Get the module functions
        //     Map<String, Object> functions = (Map<String, Object>) getModuleFunctions(command);
        //     for(String functionName: functions.keySet()) {
        //         candidates.add(new Candidate(functionName, functionName, null, null, null, null, true));
        //     }
        // } else if(line.words().size() >=2) {
        //     // Get the function arguments
        //     String functionName = line.words().get(1);
        //     Map<String, Object> arguments = (Map<String, Object>) getFunctionArguments(command, functionName);
        //     for(String argumentName: arguments.keySet()) {
        //         candidates.add(new Candidate(argumentName, argumentName, null, null, null, null, true));
        //     }
        // }

        
    }

    private void addFunctionCandidatesFromMetadata(List<Candidate> candidates, Array metadata) throws PageException {
        for (int i = 1; i <= metadata.size(); i++) {
            Struct item = (Struct) metadata.get(i, null);
            if (item != null) {
                // System.out.println("Item: " + item.toString());
                // Process each metadata item (likely a Struct containing function info)
                candidates.add(
                    new Candidate(
                        item.get("name").toString(),
                        item.get("name").toString(),
                        "module-commands",
                         null, null, null, true
                    )
                );
            }
        }
    }

    /**
     * List installed modules (name -> path) in lowercase for easy lookup
    */
    private static Map<String, Path> listModules(){
        // Build map of installed modules (name -> description/status)
        Map<String, Path> installed = new java.util.TreeMap<>();
        
        try (Stream<Path> stream = Files.list(ModuleCommand.getModulesDirectory())) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                installed.put(dir.getFileName().toString().toLowerCase(), dir);
            });
        } catch (IOException e) {
            // Ignore errors
        }
        return installed;
    }
    /**
     * Check if a module is installed (case-insensitive)
     */
    private static boolean isModule(String moduleName) {
        Map<String, Path> modules = listModules();
        return modules.containsKey(moduleName.toLowerCase());
    }

    public static Object getModuleFunctions(String moduleName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getModuleFunctions'");
    }

    public static Object getFunctionArguments(String moduleName, String functionName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFunctionArguments'");
    }


    
    /**
     * Check whether a command name is one of the built-in internal commands
     * (used to avoid duplicating candidates when suggesting module names).
     */
    private boolean isInternalCommand(String command) {
        for (String internal : internalCommands) {
            if (internal.equalsIgnoreCase(command)) {
                return true;
            }
        }
        return false;
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
     * Complete server command and subcommands
     */
    private void completeServerCommand(ParsedLine line, List<Candidate> candidates) {
        List<String> words = line.words();
        
        if (words.size() == 2) {
            // Complete server subcommands
            String[] serverSubcommands = {"start", "stop", "status", "list", "prune", "config", "monitor", "log", "debug"};
            String partial = words.get(1);
            
            for (String subcommand : serverSubcommands) {
                if (subcommand.startsWith(partial.toLowerCase())) {
                    String displayValue = subcommand;
                    String description = getServerSubcommandDescription(subcommand);
                    
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = getServerSubcommandEmoji(subcommand) + " " + subcommand;
                    }
                    
                    candidates.add(new Candidate(subcommand, displayValue, "server-commands", description, null, null, true));
                }
            }
        } else if (words.size() >= 3) {
            // Complete server command options and server names
            String subcommand = words.get(1).toLowerCase();
            
            // For commands that support --name, complete with server names
            if (("stop".equals(subcommand) || "status".equals(subcommand) || "prune".equals(subcommand) || "list".equals(subcommand)) && 
                words.size() >= 3) {
                
                String lastWord = words.get(words.size() - 1);
                String prevWord = words.size() > 3 ? words.get(words.size() - 2) : "";
                
                // If previous word was --name, complete with server names
                if ("--name".equals(prevWord) || "-n".equals(prevWord)) {
                    completeServerNames(lastWord, candidates);
                } else if (!lastWord.startsWith("-")) {
                    // Complete with common flags for these commands
                    completeServerFlags(subcommand, lastWord, candidates);
                }
            }
            // For start command, complete with version, webroot, and option flags
            else if ("start".equals(subcommand) && words.size() >= 3) {
                String lastWord = words.get(words.size() - 1);
                String prevWord = words.size() > 3 ? words.get(words.size() - 2) : "";
                
                // Complete version numbers after --version
                if ("--version".equals(prevWord) || "-v".equals(prevWord)) {
                    completeServerVersions(lastWord, candidates);
                }
                // Complete server names after --name
                else if ("--name".equals(prevWord) || "-n".equals(prevWord)) {
                    // For start command, suggest unique names rather than existing ones
                    String suggestion = "my_server";
                    candidates.add(new Candidate(suggestion, suggestion, "suggested-names", "Suggested server name", null, null, true));
                }
                // Directory-aware completion for --webroot values
                else if ("--webroot".equals(prevWord)) {
                    // Reuse file completion but force directory-only behaviour by pretending this is a 'cd' command
                    completeFilePaths(lastWord, candidates, "cd");
                }
                else if (!lastWord.startsWith("-")) {
                    // Complete with start command flags
                    completeServerFlags(subcommand, lastWord, candidates);
                }
            }
            // For run command, complete with version, webroot, and option flags
            else if ("run".equals(subcommand) && words.size() >= 3) {
                String lastWord = words.get(words.size() - 1);
                String prevWord = words.size() > 3 ? words.get(words.size() - 2) : "";

                if ("--version".equals(prevWord) || "-v".equals(prevWord)) {
                    completeServerVersions(lastWord, candidates);
                } else if ("--name".equals(prevWord) || "-n".equals(prevWord)) {
                    completeServerNames(lastWord, candidates);
                } else if ("--webroot".equals(prevWord)) {
                    completeFilePaths(lastWord, candidates, "cd");
                } else if (!lastWord.startsWith("-")) {
                    completeServerFlags(subcommand, lastWord, candidates);
                }
            }
            // For prune command, handle --all flag
            else if ("prune".equals(subcommand) && words.size() == 3) {
                String partial = words.get(2);
                if ("--all".startsWith(partial.toLowerCase())) {
                    String displayValue = "--all";
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = "🗑️ --all";
                    }
                    candidates.add(new Candidate("--all", displayValue, "server-flags", "Remove all stopped servers", null, null, true));
                }
            }
            // For config command, handle get/set subcommands and config keys
            else if ("config".equals(subcommand)) {
                completeServerConfigCommand(words, candidates);
            }
        }
    }
    
    /**
     * Get description for server subcommands
     */
    private String getServerSubcommandDescription(String subcommand) {
        switch (subcommand.toLowerCase()) {
            case "start":
                return "Start a Lucee server";
            case "stop":
                return "Stop a running server";
            case "status":
                return "Show server status";
            case "list":
                return "List all server instances";
            case "prune":
                return "Remove stopped server instances";
            case "config":
                return "Get or set server configuration";
            case "monitor":
                return "Monitor server via JMX";
            case "log":
                return "View server logs";
            case "debug":
                return "Debug server configuration";
            default:
                return "Server subcommand";
        }
    }
    
    /**
     * Get emoji for server subcommands
     */
    private String getServerSubcommandEmoji(String subcommand) {
        switch (subcommand.toLowerCase()) {
            case "start":
                return "🚀";
            case "stop":
                return "🛑";
            case "status":
                return "📊";
            case "list":
                return "📋";
            case "prune":
                return "🗑️";
            case "config":
                return "⚙️";
            case "monitor":
                return "📈";
            case "log":
                return "📄";
            case "debug":
                return "🔧";
            default:
                return "⚙️";
        }
    }
    
    /**
     * Complete server names for --name flag
     */
    private void completeServerNames(String partial, List<Candidate> candidates) {
        try {
            // We'd need to integrate with LuceeServerManager to get actual server names
            // For now, we'll add a simple implementation that could be enhanced later
            
            // You could integrate with the actual LuceeServerManager like this:
            // org.lucee.lucli.server.LuceeServerManager serverManager = new org.lucee.lucli.server.LuceeServerManager();
            // List<org.lucee.lucli.server.LuceeServerManager.ServerInfo> servers = serverManager.listServers();
            
            // For now, we'll suggest common server name patterns
            String[] commonNames = {"my_server", "dev_server", "test_server", "local_server"};
            
            for (String name : commonNames) {
                if (name.startsWith(partial.toLowerCase())) {
                    String displayValue = name;
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = "🖥️ " + name;
                    }
                    candidates.add(new Candidate(name, displayValue, "server-names", "Server instance", null, null, true));
                }
            }
        } catch (Exception e) {
            // Ignore errors in completion
        }
    }
    
    /**
     * Complete server command flags based on subcommand
     */
    private void completeServerFlags(String subcommand, String partial, List<Candidate> candidates) {
        String[] flags = {};
        
        switch (subcommand.toLowerCase()) {
            case "start":
                flags = new String[]{"--version", "--name", "--force", "--webroot", "-v", "-n", "-f"};
                break;
            case "stop":
            case "status":
                flags = new String[]{"--name", "-n"};
                break;
            case "prune":
                flags = new String[]{"--name", "--all", "-n", "-a"};
                break;
            case "list":
                flags = new String[]{"--running", "-r"};
                break;
        }
        
        for (String flag : flags) {
            if (flag.startsWith(partial.toLowerCase())) {
                String description = getServerFlagDescription(flag);
                String displayValue = flag;
                
                if (commandProcessor.getSettings().showEmojis()) {
                    displayValue = "🏃 " + flag;
                }
                
                candidates.add(new Candidate(flag, displayValue, "server-flags", description, null, null, true));
            }
        }
    }
    
    /**
     * Complete Lucee version numbers
     */
    private void completeServerVersions(String partial, List<Candidate> candidates) {
        // Common Lucee versions
        String[] versions = {"6.2.2.91", "6.1.0.243", "6.0.3.1", "5.4.6.9"};
        
        for (String version : versions) {
            if (version.startsWith(partial)) {
                String displayValue = version;
                if (commandProcessor.getSettings().showEmojis()) {
                    displayValue = "⚡ " + version;
                }
                candidates.add(new Candidate(version, displayValue, "lucee-versions", "Lucee version", null, null, true));
            }
        }
    }
    
    /**
     * Get description for server command flags
     */
    private String getServerFlagDescription(String flag) {
        switch (flag) {
            case "--version":
            case "-v":
                return "Specify Lucee version";
            case "--name":
            case "-n":
                return "Specify server name";
            case "--force":
            case "-f":
                return "Force replace existing server";
            case "--all":
            case "-a":
                return "Apply to all servers";
            case "--running":
            case "-r":
                return "Show only running servers";
            default:
                return "Server option";
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
            List<Candidate> cfmlCandidates = CfmlCompleter.getCompletions(currentFunction);
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
    
    /**
     * Complete server config command with get/set subcommands and configuration keys
     */
    private void completeServerConfigCommand(List<String> words, List<Candidate> candidates) {
        if (words.size() == 3) {
            // Complete config subcommands: get, set
            String[] configSubcommands = {"get", "set"};
            String partial = words.get(2);
            
            for (String configCmd : configSubcommands) {
                if (configCmd.startsWith(partial.toLowerCase())) {
                    String displayValue = configCmd;
                    String description = getConfigSubcommandDescription(configCmd);
                    
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = getConfigSubcommandEmoji(configCmd) + " " + configCmd;
                    }
                    
                    candidates.add(new Candidate(configCmd, displayValue, "config-commands", description, null, null, true));
                }
            }
            
            // Also complete --no-cache flag
            if ("--no-cache".startsWith(partial.toLowerCase())) {
                String displayValue = "--no-cache";
                if (commandProcessor.getSettings().showEmojis()) {
                    displayValue = "🗑️ --no-cache";
                }
                candidates.add(new Candidate("--no-cache", displayValue, "config-flags", "Clear Lucee version cache", null, null, true));
            }
        } else if (words.size() == 4) {
            String configCmd = words.get(2).toLowerCase();
            String partial = words.get(3);
            
            if ("get".equals(configCmd)) {
                // Complete configuration keys for get command, or handle key=value syntax
                if (partial.contains("=")) {
                    // Handle key=value completion for get command too
                    String[] parts = partial.split("=", 2);
                    String key = parts[0];
                    String valuePartial = parts[1];
                    
                    completeConfigValues(key, valuePartial, partial, candidates);
                } else {
                    // Complete configuration keys normally
                    completeConfigKeys(partial, candidates);
                }
            } else if ("set".equals(configCmd)) {
                // For set command, complete key= patterns and --no-cache
                if ("--no-cache".startsWith(partial.toLowerCase())) {
                    String displayValue = "--no-cache";
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = "🗑️ --no-cache";
                    }
                    candidates.add(new Candidate("--no-cache", displayValue, "config-flags", "Clear version cache and refresh", null, null, true));
                } else {
                    // Complete key= patterns for set command
                    completeConfigKeyValuePairs(partial, candidates);
                }
            }
        } else if (words.size() > 4) {
            String configCmd = words.get(2).toLowerCase();
            
            // For set command, handle additional --no-cache flag
            if ("set".equals(configCmd)) {
                String lastWord = words.get(words.size() - 1);
                if ("--no-cache".startsWith(lastWord.toLowerCase())) {
                    String displayValue = "--no-cache";
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = "🗑️ --no-cache";
                    }
                    candidates.add(new Candidate("--no-cache", displayValue, "config-flags", "Clear version cache and refresh", null, null, true));
                }
            }
        }
    }
    
    /**
     * Get description for config subcommands
     */
    private String getConfigSubcommandDescription(String subcommand) {
        switch (subcommand.toLowerCase()) {
            case "get":
                return "Get configuration value";
            case "set":
                return "Set configuration value";
            default:
                return "Config subcommand";
        }
    }
    
    /**
     * Get emoji for config subcommands
     */
    private String getConfigSubcommandEmoji(String subcommand) {
        switch (subcommand.toLowerCase()) {
            case "get":
                return "📖";
            case "set":
                return "✏️";
            default:
                return "⚙️";
        }
    }
    
    /**
     * Complete configuration keys
     */
    private void completeConfigKeys(String partial, List<Candidate> candidates) {
        try {
            // Use ServerConfigHelper to get available keys
            org.lucee.lucli.commands.ServerConfigHelper configHelper = new org.lucee.lucli.commands.ServerConfigHelper();
            List<String> availableKeys = configHelper.getAvailableKeys();
            
            for (String key : availableKeys) {
                if (key.startsWith(partial.toLowerCase())) {
                    String displayValue = key;
                    String description = getConfigKeyDescription(key);
                    
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = getConfigKeyEmoji(key) + " " + key;
                    }
                    
                    candidates.add(new Candidate(key, displayValue, "config-keys", description, null, null, true));
                }
            }
        } catch (Exception e) {
            // Fallback to static list if ServerConfigHelper fails
            String[] keys = {
                "version", "port", "name", "jvm.maxMemory", "jvm.minMemory", "jvm.args",
                "lucee.adminPassword", "lucee.preserveCase", "lucee.suppressWhitespace",
                "monitoring.enabled", "monitoring.jmx.enabled", "monitoring.jmx.port", "monitoring.jmx.host",
                "ssl.enabled", "ssl.port", "ssl.keystore", "ssl.keystorePassword"
            };
            
            for (String key : keys) {
                if (key.startsWith(partial.toLowerCase())) {
                    String displayValue = key;
                    String description = getConfigKeyDescription(key);
                    
                    if (commandProcessor.getSettings().showEmojis()) {
                        displayValue = getConfigKeyEmoji(key) + " " + key;
                    }
                    
                    candidates.add(new Candidate(key, displayValue, "config-keys", description, null, null, true));
                }
            }
        }
    }
    
    /**
     * Complete configuration key=value pairs for set command
     */
    private void completeConfigKeyValuePairs(String partial, List<Candidate> candidates) {
        // Check if partial already contains '='
        if (partial.contains("=")) {
            // Complete values for specific keys
            String[] parts = partial.split("=", 2);
            String key = parts[0];
            String valuePartial = parts[1];
            
            completeConfigValues(key, valuePartial, partial, candidates);
        } else {
            // Complete keys with '=' suffix
            try {
                org.lucee.lucli.commands.ServerConfigHelper configHelper = new org.lucee.lucli.commands.ServerConfigHelper();
                List<String> availableKeys = configHelper.getAvailableKeys();
                
                for (String key : availableKeys) {
                    if (key.startsWith(partial.toLowerCase())) {
                        String completion = key + "=";
                        String displayValue = completion;
                        String description = getConfigKeyDescription(key);
                        
                        if (commandProcessor.getSettings().showEmojis()) {
                            displayValue = getConfigKeyEmoji(key) + " " + completion;
                        }
                        
                        candidates.add(new Candidate(completion, displayValue, "config-key-equals", description, null, null, false));
                    }
                }
            } catch (Exception e) {
                // Fallback to static list
                String[] keys = {
                    "version", "port", "name", "jvm.maxMemory", "jvm.minMemory", "jvm.args",
                    "lucee.adminPassword", "lucee.preserveCase", "lucee.suppressWhitespace",
                    "monitoring.enabled", "monitoring.jmx.enabled", "monitoring.jmx.port", "monitoring.jmx.host",
                    "ssl.enabled", "ssl.port", "ssl.keystore", "ssl.keystorePassword"
                };
                
                for (String key : keys) {
                    if (key.startsWith(partial.toLowerCase())) {
                        String completion = key + "=";
                        String displayValue = completion;
                        String description = getConfigKeyDescription(key);
                        
                        if (commandProcessor.getSettings().showEmojis()) {
                            displayValue = getConfigKeyEmoji(key) + " " + completion;
                        }
                        
                        candidates.add(new Candidate(completion, displayValue, "config-key-equals", description, null, null, false));
                    }
                }
            }
        }
    }
    
    /**
     * Complete configuration values for specific keys
     */
    private void completeConfigValues(String key, String valuePartial, String fullPartial, List<Candidate> candidates) {
        switch (key.toLowerCase()) {
            case "version":
                // Complete Lucee versions
                try {
                    org.lucee.lucli.commands.ServerConfigHelper configHelper = new org.lucee.lucli.commands.ServerConfigHelper();
                    List<String> versions = configHelper.getAvailableVersions();
                    
                    for (String version : versions) {
                        if (version.startsWith(valuePartial)) {
                            String completion = version;  // Just complete the version part
                            String displayValue = version;
                            
                            if (commandProcessor.getSettings().showEmojis()) {
                                displayValue = "⚡ " + version;
                            }
                            
                            candidates.add(new Candidate(completion, displayValue, "version-values", "Lucee version " + version, null, null, true));
                        }
                    }
                } catch (Exception e) {
                    // Fallback to common versions (including 7.x versions)
                    String[] versions = {"7.0.0.346", "7.0.0.145", "7.0.0.090", "6.2.2.91", "6.2.1.75", "6.2.0.66", "6.1.8.29", "6.1.7.25", "6.0.4.10"};
                    for (String version : versions) {
                        if (version.startsWith(valuePartial)) {
                            String completion = version;  // Just complete the version part
                            String displayValue = version;
                            
                            if (commandProcessor.getSettings().showEmojis()) {
                                displayValue = "⚡ " + version;
                            }
                            
                            candidates.add(new Candidate(completion, displayValue, "version-values", "Lucee version " + version, null, null, true));
                        }
                    }
                }
                break;
                
            case "port":
            case "monitoring.jmx.port":
            case "ssl.port":
                // Complete common port numbers
                String[] ports = {"8080", "8888", "9080", "8443", "443", "80"};
                for (String port : ports) {
                    if (port.startsWith(valuePartial)) {
                        String completion = port;  // Just complete the port part
                        String displayValue = port;
                        
                        if (commandProcessor.getSettings().showEmojis()) {
                            displayValue = "🔌 " + port;
                        }
                        
                        candidates.add(new Candidate(completion, displayValue, "port-values", "Port " + port, null, null, true));
                    }
                }
                break;
                
            case "jvm.maxmemory":
            case "jvm.minmemory":
                // Complete common memory values
                String[] memoryValues = {"512m", "1g", "2g", "4g", "8g", "256m", "128m"};
                for (String memory : memoryValues) {
                    if (memory.startsWith(valuePartial.toLowerCase())) {
                        String completion = memory;  // Just complete the memory part
                        String displayValue = memory;
                        
                        if (commandProcessor.getSettings().showEmojis()) {
                            displayValue = "💾 " + memory;
                        }
                        
                        candidates.add(new Candidate(completion, displayValue, "memory-values", "Memory " + memory, null, null, true));
                    }
                }
                break;
                
            case "lucee.preservecase":
            case "lucee.suppresswhitespace":
            case "monitoring.enabled":
            case "monitoring.jmx.enabled":
            case "ssl.enabled":
                // Complete boolean values
                String[] booleans = {"true", "false"};
                for (String bool : booleans) {
                    if (bool.startsWith(valuePartial.toLowerCase())) {
                        String completion = bool;  // Just complete the boolean part
                        String displayValue = bool;
                        
                        if (commandProcessor.getSettings().showEmojis()) {
                            displayValue = ("true".equals(bool) ? "✅ " : "❌ ") + bool;
                        }
                        
                        candidates.add(new Candidate(completion, displayValue, "boolean-values", "Boolean " + bool, null, null, true));
                    }
                }
                break;
        }
    }
    
    /**
     * Get description for configuration keys
     */
    private String getConfigKeyDescription(String key) {
        switch (key.toLowerCase()) {
            case "version":
                return "Lucee version";
            case "port":
                return "Server HTTP port";
            case "name":
                return "Server instance name";
            case "jvm.maxmemory":
                return "JVM maximum memory";
            case "jvm.minmemory":
                return "JVM minimum memory";
            case "jvm.args":
                return "Additional JVM arguments";
            case "lucee.adminpassword":
                return "Lucee admin password";
            case "lucee.preservecase":
                return "Preserve variable case";
            case "lucee.suppresswhitespace":
                return "Suppress whitespace output";
            case "monitoring.enabled":
                return "Enable monitoring";
            case "monitoring.jmx.enabled":
                return "Enable JMX monitoring";
            case "monitoring.jmx.port":
                return "JMX monitoring port";
            case "monitoring.jmx.host":
                return "JMX monitoring host";
            case "ssl.enabled":
                return "Enable SSL";
            case "ssl.port":
                return "SSL port";
            case "ssl.keystore":
                return "SSL keystore path";
            case "ssl.keystorepassword":
                return "SSL keystore password";
            default:
                return "Configuration key";
        }
    }
    
    /**
     * Get emoji for configuration keys
     */
    private String getConfigKeyEmoji(String key) {
        switch (key.toLowerCase()) {
            case "version":
                return "⚡";
            case "port":
            case "monitoring.jmx.port":
            case "ssl.port":
                return "🔌";
            case "name":
                return "🏷️";
            case "jvm.maxmemory":
            case "jvm.minmemory":
                return "💾";
            case "jvm.args":
                return "⚙️";
            case "lucee.adminpassword":
            case "ssl.keystorepassword":
                return "🔐";
            case "lucee.preservecase":
            case "lucee.suppresswhitespace":
            case "monitoring.enabled":
            case "monitoring.jmx.enabled":
            case "ssl.enabled":
                return "✅";
            case "monitoring.jmx.host":
                return "🖥️";
            case "ssl.keystore":
                return "🔑";
            default:
                return "🔧";
        }
    }
}
