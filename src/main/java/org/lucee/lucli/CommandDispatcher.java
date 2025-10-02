package org.lucee.lucli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lucee.lucli.commands.UnifiedCommandExecutor;
import org.lucee.lucli.cflint.CFLintCommand;
import org.lucee.lucli.modules.ModuleCommand;

/**
 * Unified command dispatcher that handles all command routing logic.
 * This eliminates duplication between CLI mode and terminal mode.
 */
public class CommandDispatcher {
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_ERROR = 1;
    
    private final boolean interactive;
    private final Path currentDirectory;
    private final UnifiedCommandExecutor unifiedExecutor;
    private final CFLintCommand cfLintCommand;
    
    public CommandDispatcher(boolean interactive, Path currentDirectory) {
        this.interactive = interactive;
        this.currentDirectory = currentDirectory;
        this.unifiedExecutor = new UnifiedCommandExecutor(interactive, currentDirectory);
        this.cfLintCommand = new CFLintCommand();
    }
    
    /**
     * Dispatch a command with arguments
     * @param command The command to execute
     * @param args Arguments for the command
     * @return CommandResult containing output and whether to exit
     */
    public CommandResult dispatch(String command, String[] args) {
        Timer.start("Command Dispatch: " + command);
        
        try {
            switch (command.toLowerCase()) {
                case "--version":
                case "version":
                    String versionOutput = LuCLI.getVersionInfo();
                    return new CommandResult(versionOutput, !interactive);
                    
                case "--lucee-version":
                case "lucee-version":
                    return handleLuceeVersion();
                    
                case "help":
                case "--help":
                case "-h":
                    String helpOutput = loadHelp();
                    return new CommandResult(helpOutput, !interactive);
                    
                case "server":
                case "servers":
                    return handleServerCommand(args);
                    
                case "module":
                case "modules":
                    return handleModulesCommand(args);
                    
                case "cflint":
                    return handleCFLintCommand(command, args);
                    
                default:
                    return handleDefaultCommand(command, args);
            }
        } finally {
            Timer.stop("Command Dispatch: " + command);
        }
    }
    
    private CommandResult handleLuceeVersion() {
        try {
            Timer.start("Lucee Version Check");
            
            LuceeScriptEngine engine = LuceeScriptEngine.getInstance(true, false);
            
            if (engine == null) {
                String error = "LuceeScriptEngine instance is null. Lucee may not be properly initialized.";
                return new CommandResult(error, true);
            }

            engine.eval("version = SERVER.LUCEE.version");
            String version = "Lucee Version: " + engine.getEngine().get("version");
            
            Timer.stop("Lucee Version Check");
            
            // In non-interactive mode, we need to force exit due to Lucee background threads
            return new CommandResult(version, !interactive);
            
        } catch (Exception e) {
            Timer.stop("Lucee Version Check");
            String error = "Error getting Lucee version: " + e.getMessage();
            return new CommandResult(error, true);
        }
    }
    
    private CommandResult handleServerCommand(String[] args) {
        Timer.start("Server Command");
        try {
            String result = unifiedExecutor.executeCommand("server", args);
            Timer.stop("Server Command");
            
            // Check if this is a monitor command which exits
            boolean shouldExit = args.length > 0 && "monitor".equals(args[0].toLowerCase());
            
            return new CommandResult(result, shouldExit);
        } catch (Exception e) {
            Timer.stop("Server Command");
            String error = "Error executing server command: " + e.getMessage();
            return new CommandResult(error, false);
        }
    }
    
    private CommandResult handleModulesCommand(String[] args) {
        Timer.start("Module Command");
        try {
            // Capture module command output
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream originalErr = System.err;
            
            System.setOut(new java.io.PrintStream(baos));
            System.setErr(new java.io.PrintStream(baos));
            
            ModuleCommand.executeModule(args);
            
            // Restore streams
            System.setOut(originalOut);
            System.setErr(originalErr);
            
            Timer.stop("Module Command");
            
            String output = baos.toString().trim();
            return new CommandResult(output, !interactive);
            
        } catch (Exception e) {
            Timer.stop("Module Command");
            String error = "Error executing module command: " + e.getMessage();
            return new CommandResult(error, false);
        }
    }
    
    private CommandResult handleCFLintCommand(String command, String[] args) {
        Timer.start("Lint Command");
        try {
            // Reconstruct command line
            StringBuilder commandLine = new StringBuilder(command);
            for (String arg : args) {
                commandLine.append(" ").append(arg);
            }
            
            // Capture CFLint output
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream originalErr = System.err;
            
            System.setOut(new java.io.PrintStream(baos));
            System.setErr(new java.io.PrintStream(baos));
            
            boolean result = cfLintCommand.handleLintCommand(commandLine.toString());
            
            // Restore streams
            System.setOut(originalOut);
            System.setErr(originalErr);
            
            Timer.stop("Lint Command");
            
            String output = baos.toString().trim();
            boolean shouldExit = !interactive && !result;
            
            return new CommandResult(output.isEmpty() ? "✅ Lint command completed" : output, shouldExit);
            
        } catch (Exception e) {
            Timer.stop("Lint Command");
            String error = "Error executing lint command: " + e.getMessage();
            return new CommandResult(error, false);
        }
    }
    
    private CommandResult handleDefaultCommand(String command, String[] args) {
        // Check if this is a module command first
        Timer.start("Module Check");
        if (ModuleCommand.moduleExists(command)) {
            Timer.stop("Module Check");
            Timer.start("Module Execution");
            
            try {
                // Capture module output
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.io.PrintStream originalOut = System.out;
                java.io.PrintStream originalErr = System.err;
                
                System.setOut(new java.io.PrintStream(baos));
                System.setErr(new java.io.PrintStream(baos));
                
                ModuleCommand.executeModuleByName(command, args);
                
                // Restore streams
                System.setOut(originalOut);
                System.setErr(originalErr);
                
                Timer.stop("Module Execution");
                
                String output = baos.toString().trim();
                return new CommandResult(output, !interactive);
                
            } catch (Exception e) {
                Timer.stop("Module Execution");
                String error = "Error executing module '" + command + "': " + e.getMessage();
                return new CommandResult(error, false);
            }
        }
        Timer.stop("Module Check");
        
        // Finally, try to execute as a CFML script
        Timer.start("Script Execution");
        try {
            // Capture script output
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream originalErr = System.err;
            
            System.setOut(new java.io.PrintStream(baos));
            System.setErr(new java.io.PrintStream(baos));
            
            LuceeScriptEngine.getInstance(LuCLI.verbose, LuCLI.debug)
                    .executeScript(command, args);
            
            // Restore streams
            System.setOut(originalOut);
            System.setErr(originalErr);
            
            Timer.stop("Script Execution");
            
            String output = baos.toString().trim();
            return new CommandResult(output, !interactive);
            
        } catch (Exception e) {
            Timer.stop("Script Execution");
            String error = "Error executing script '" + command + "': " + e.getMessage();
            return new CommandResult(error, !interactive);
        }
    }
    
    private String loadHelp() {
        return StringOutput.loadText("/text/main-help.txt");
    }
    
    /**
     * Result of command execution
     */
    public static class CommandResult {
        public final String output;
        public final boolean shouldExit;
        
        public CommandResult(String output, boolean shouldExit) {
            this.output = output;
            this.shouldExit = shouldExit;
        }
    }
}