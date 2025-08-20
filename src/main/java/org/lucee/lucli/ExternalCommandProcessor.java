package org.lucee.lucli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced command processor that supports external commands like git, npm, etc.
 */
public class ExternalCommandProcessor {
    private final CommandProcessor fileSystemProcessor;
    private final Settings settings;
    private final Set<String> knownExternalCommands;
    private final Map<String, CommandIntegration> integrations;
    
    public ExternalCommandProcessor(CommandProcessor fileSystemProcessor, Settings settings) {
        this.fileSystemProcessor = fileSystemProcessor;
        this.settings = settings;
        this.knownExternalCommands = new HashSet<>();
        this.integrations = new HashMap<>();
        
        initializeKnownCommands();
        initializeIntegrations();
    }
    
    private void initializeKnownCommands() {
        // Version control
        knownExternalCommands.addAll(Arrays.asList(
            "git", "svn", "hg", "bzr"
        ));
        
        // Package managers
        knownExternalCommands.addAll(Arrays.asList(
            "npm", "yarn", "pip", "composer", "maven", "gradle"
        ));
        
        // Build tools
        knownExternalCommands.addAll(Arrays.asList(
            "make", "cmake", "ant", "gulp", "webpack"
        ));
        
        // Docker & containers
        knownExternalCommands.addAll(Arrays.asList(
            "docker", "docker-compose", "kubectl", "podman"
        ));
        
        // Cloud tools
        knownExternalCommands.addAll(Arrays.asList(
            "aws", "gcloud", "az", "terraform", "ansible"
        ));
        
        // Text processing
        knownExternalCommands.addAll(Arrays.asList(
            "grep", "sed", "awk", "sort", "uniq", "cut"
        ));
        
        // System commands
        knownExternalCommands.addAll(Arrays.asList(
            "ps", "top", "htop", "kill", "killall", "which", "whereis"
        ));
    }
    
    private void initializeIntegrations() {
        // Git integration with enhanced features
        integrations.put("git", new GitIntegration());
        
        // Node.js/NPM integration
        integrations.put("npm", new NpmIntegration());
        integrations.put("yarn", new NpmIntegration());
        
        // Docker integration
        integrations.put("docker", new DockerIntegration());
    }
    
    /**
     * Execute a command - routes to internal or external processor
     */
    public String executeCommand(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return "";
        }
        
        String[] parts = parseCommand(commandLine.trim());
        if (parts.length == 0) {
            return "";
        }
        
        String command = parts[0].toLowerCase();
        
        // Try internal file system commands first
        if (isInternalCommand(command)) {
            return fileSystemProcessor.executeCommand(commandLine);
        }
        
        // Check for specific integrations
        if (integrations.containsKey(command)) {
            return integrations.get(command).execute(parts, fileSystemProcessor.getFileSystemState());
        }
        
        // Try external command execution
        if (shouldTryExternal(command)) {
            return executeExternalCommand(parts);
        }
        
        // Fallback to internal processor for unknown commands
        return fileSystemProcessor.executeCommand(commandLine);
    }
    
    private boolean isInternalCommand(String command) {
        Set<String> internalCommands = Set.of(
            "ls", "dir", "cd", "pwd", "mkdir", "rmdir", "rm", "cp", "mv",
            "cat", "touch", "find", "wc", "head", "tail", "prompt", "run"
        );
        return internalCommands.contains(command);
    }
    
    private boolean shouldTryExternal(String command) {
        // Try external if it's a known command or if the executable exists
        return knownExternalCommands.contains(command) || isExecutableAvailable(command);
    }
    
    private boolean isExecutableAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            
            // Use 'which' on Unix-like systems, 'where' on Windows
            String checkCommand = System.getProperty("os.name").toLowerCase().contains("windows") 
                ? "where" : "which";
                
            pb.command(checkCommand, command);
            Process process = pb.start();
            
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
            
            process.destroyForcibly();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Execute external command with proper output handling
     */
    private String executeExternalCommand(String[] commandParts) {
        try {
            ProcessBuilder pb = new ProcessBuilder(commandParts);
            pb.directory(fileSystemProcessor.getFileSystemState().getCurrentWorkingDirectory().toFile());
            pb.redirectErrorStream(true); // Merge stderr with stdout
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Wait for process to complete with timeout
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return "⚠️ Command timed out after 30 seconds: " + String.join(" ", commandParts);
            }
            
            int exitCode = process.exitValue();
            String result = output.toString().trim();
            
            if (exitCode != 0 && result.isEmpty()) {
                return "❌ Command failed with exit code " + exitCode + ": " + String.join(" ", commandParts);
            }
            
            return result;
            
        } catch (IOException e) {
            return "❌ Failed to execute command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "❌ Command execution interrupted";
        }
    }
    
    private String[] parseCommand(String commandLine) {
        // Use the existing parser from CommandProcessor
        return fileSystemProcessor.parseCommand(commandLine);
    }
    
    /**
     * Base class for command integrations
     */
    abstract static class CommandIntegration {
        abstract String execute(String[] args, FileSystemState fileSystemState);
        
        protected String executeWithOutput(String[] command, Path workingDir) {
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(workingDir.toFile());
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                
                process.waitFor(10, TimeUnit.SECONDS);
                return output.toString().trim();
                
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }
    }
    
    /**
     * Enhanced Git integration with LuCLI-specific features
     */
    static class GitIntegration extends CommandIntegration {
        @Override
        String execute(String[] args, FileSystemState fileSystemState) {
            Path currentDir = fileSystemState.getCurrentWorkingDirectory();
            
            // Check if we're in a git repository
            if (!isGitRepository(currentDir)) {
                if (args.length > 1 && args[1].equals("init")) {
                    // Allow git init
                    return executeWithOutput(args, currentDir);
                } else {
                    return "💡 Not a git repository. Use 'git init' to initialize one.";
                }
            }
            
            // Enhanced git status with emoji
            if (args.length > 1 && args[1].equals("status")) {
                return enhancedGitStatus(args, currentDir);
            }
            
            // Enhanced git log
            if (args.length > 1 && args[1].equals("log")) {
                return enhancedGitLog(args, currentDir);
            }
            
            // Default: pass through to git
            return executeWithOutput(args, currentDir);
        }
        
        private boolean isGitRepository(Path dir) {
            Path current = dir;
            while (current != null) {
                if (Files.exists(current.resolve(".git"))) {
                    return true;
                }
                current = current.getParent();
            }
            return false;
        }
        
        private String enhancedGitStatus(String[] args, Path workingDir) {
            String result = executeWithOutput(args, workingDir);
            
            // Add emoji enhancements
            result = result.replace("Changes not staged", "🔄 Changes not staged")
                          .replace("Changes to be committed", "✅ Changes to be committed")
                          .replace("Untracked files", "❓ Untracked files")
                          .replace("nothing to commit", "✨ nothing to commit")
                          .replace("Your branch is up to date", "🟢 Your branch is up to date")
                          .replace("Your branch is ahead", "🔼 Your branch is ahead")
                          .replace("Your branch is behind", "🔽 Your branch is behind");
                          
            return result;
        }
        
        private String enhancedGitLog(String[] args, Path workingDir) {
            // Use a nice format by default if no format specified
            List<String> newArgs = new ArrayList<>(Arrays.asList(args));
            
            boolean hasFormat = false;
            for (String arg : args) {
                if (arg.contains("--format") || arg.contains("--pretty") || arg.contains("--oneline")) {
                    hasFormat = true;
                    break;
                }
            }
            
            if (!hasFormat) {
                newArgs.add("--pretty=format:%C(yellow)%h%C(reset) %C(green)%ad%C(reset) %C(blue)%an%C(reset) %s");
                newArgs.add("--date=short");
                newArgs.add("-10"); // Limit to 10 commits by default
            }
            
            return executeWithOutput(newArgs.toArray(new String[0]), workingDir);
        }
    }
    
    /**
     * NPM/Yarn integration with project detection
     */
    static class NpmIntegration extends CommandIntegration {
        @Override
        String execute(String[] args, FileSystemState fileSystemState) {
            Path currentDir = fileSystemState.getCurrentWorkingDirectory();
            
            // Check for package.json
            if (!Files.exists(currentDir.resolve("package.json"))) {
                if (args.length > 1 && args[1].equals("init")) {
                    return executeWithOutput(args, currentDir);
                } else {
                    return "💡 No package.json found. Use 'npm init' to create a new project.";
                }
            }
            
            return executeWithOutput(args, currentDir);
        }
    }
    
    /**
     * Docker integration with container management
     */
    static class DockerIntegration extends CommandIntegration {
        @Override
        String execute(String[] args, FileSystemState fileSystemState) {
            Path currentDir = fileSystemState.getCurrentWorkingDirectory();
            
            // Enhanced docker ps with formatting
            if (args.length > 1 && args[1].equals("ps")) {
                return enhancedDockerPs(args, currentDir);
            }
            
            return executeWithOutput(args, currentDir);
        }
        
        private String enhancedDockerPs(String[] args, Path workingDir) {
            List<String> newArgs = new ArrayList<>(Arrays.asList(args));
            
            // Add nice formatting if not specified
            boolean hasFormat = Arrays.stream(args).anyMatch(arg -> arg.contains("--format"));
            if (!hasFormat) {
                newArgs.add("--format");
                newArgs.add("table {{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.Ports}}");
            }
            
            return executeWithOutput(newArgs.toArray(new String[0]), workingDir);
        }
    }
}
