package org.lucee.lucli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.lucee.lucli.commands.UnifiedCommandExecutor;
import org.lucee.lucli.modules.ModuleCommand;

public class InteractiveTerminal {
    private static LuceeScriptEngine luceeEngine;
    private static org.jline.terminal.Terminal terminal;
    private static CommandProcessor commandProcessor;
    private static ExternalCommandProcessor externalCommandProcessor;
    private static UnifiedCommandExecutor unifiedExecutor;
    
    /**
     * Main entry point for terminal mode
     * @param args Command line arguments for one-shot mode, or empty for interactive mode
     */
    public static void main(String[] args) throws Exception {
        boolean oneShotMode = args.length > 0;
        
        if (oneShotMode) {
            executeOneShotCommand(args);
            return;
        }
        
        startInteractiveMode();
    }
    
    /**
     * Execute a single command and exit (CLI mode)
     */
    private static void executeOneShotCommand(String[] args) throws Exception {
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        unifiedExecutor = new UnifiedCommandExecutor(false, currentDir);
        
        String command = args[0];
        String[] commandArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        
        // Handle basic commands first
        switch (command.toLowerCase()) {
            case "--version":
            case "version":
                System.out.println(LuCLI.getVersionInfo());
                return;
            case "--lucee-version":
            case "lucee-version":
                showLuceeVersionNonInteractive();
                return;
            case "help":
            case "--help":
            case "-h":
                showHelpNonInteractive();
                return;
        }
        
        // Check if it's a command that UnifiedCommandExecutor handles
        if (isMainCommand(command)) {
            String result = unifiedExecutor.executeCommand(command, commandArgs);
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }
        } else {
            // For other commands like scripts, modules, etc., we need different handling
            // Check if it's a module
            if (ModuleCommand.moduleExists(command)) {
                try {
                    executeModule(command, commandArgs);
                } catch (Exception e) {
                    System.err.println("Error executing module '" + command + "': " + e.getMessage());
                    System.exit(1);
                }
            } else {
                // Try as CFML script
                try {
                    LuceeScriptEngine.getInstance(LuCLI.verbose, LuCLI.debug)
                            .executeScript(command, commandArgs);
                } catch (Exception e) {
                    System.err.println("Error executing script '" + command + "': " + e.getMessage());
                    System.exit(1);
                }
            }
        }
    }
    
    /**
     * Start interactive terminal mode
     */
    private static void startInteractiveMode() throws Exception {
        // Configure Windows-friendly terminal environment
        WindowsCompatibility.configureTerminalEnvironment();
        
        // Initialize unified command executor for interactive mode
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        unifiedExecutor = new UnifiedCommandExecutor(true, currentDir);
        
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
        } catch (IOException e) {
            // Fallback to dumb terminal on Windows if system terminal fails
            if (LuCLI.debug) {
                System.err.println("Failed to create system terminal, using dumb terminal: " + e.getMessage());
            }
            terminal = TerminalBuilder.builder()
                    .dumb(true)
                    .build();
        }
        
        // Initialize command processor
        commandProcessor = new CommandProcessor();
        
        // Initialize external command processor with enhanced features
        externalCommandProcessor = new ExternalCommandProcessor(commandProcessor, commandProcessor.getSettings());

        // Properly resolve history file path (expand ~ to user home directory)
        Path homeDir = Paths.get(System.getProperty("user.home"));
        Path historyFile = homeDir.resolve(".lucli").resolve("history");
        
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new LucliCompleter(commandProcessor))
                .variable(LineReader.HISTORY_FILE, historyFile)
                .variable(LineReader.HISTORY_SIZE, 1000) // Maximum entries in memory
                .variable(LineReader.HISTORY_FILE_SIZE, 2000) // Maximum entries in file
                .build();

        terminal.writer().println(WindowsCompatibility.Symbols.ROCKET + " LuCLI Terminal " + LuCLI.getVersion() +"  Type 'exit' or 'quit' to leave.");
        terminal.writer().println(WindowsCompatibility.Symbols.FOLDER + " Working Directory: " + commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
        if(LuCLI.verbose) {
            terminal.writer().println(WindowsCompatibility.Symbols.COMPUTER + " Use 'cfml <expression>' to execute CFML code, e.g., 'cfml now()')");
            terminal.writer().println(WindowsCompatibility.Symbols.FOLDER + " File system commands available: ls, cd, pwd, mkdir, cp, mv, rm, cat, etc.");
            terminal.writer().println(WindowsCompatibility.Symbols.TOOL + " External commands supported: git, npm, docker, grep, and more!");
            terminal.writer().println(WindowsCompatibility.Symbols.ART + " Type 'prompt' to change your prompt style!");
        }
        terminal.writer().flush();

        while (true) {
            try {
                // Generate dynamic prompt using PromptConfig
                String dynamicPrompt = commandProcessor.getPromptConfig().generatePrompt(commandProcessor.getFileSystemState());
                
                String line = reader.readLine(dynamicPrompt);
                if (line == null) {
                    break; // EOF
                }
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                    break;
                }
                
                // Handle CFML command
                if (trimmed.toLowerCase().startsWith("cfml ")) {
                    String cfmlCode = trimmed.substring(5).trim(); // Remove "cfml " prefix
                    executeCFML(cfmlCode);
                } else if (trimmed.isEmpty()) {
                    // Do nothing for empty lines
                    continue;
                } else {
                    // Parse command and arguments
                    String[] parts = trimmed.split("\\s+", 2);
                    String command = parts[0].toLowerCase();
                    String[] cmdArgs = new String[0];
                    
                    if (parts.length > 1) {
                        // Split arguments properly handling quoted strings
                        cmdArgs = parseArguments(parts[1]);
                    }
                    
                    // Try UnifiedCommandExecutor first for main commands (server, modules, etc.)
                    if (isMainCommand(command)) {
                        String result = unifiedExecutor.executeCommand(command, cmdArgs);
                        if (result != null && !result.isEmpty()) {
                            terminal.writer().println(result);
                        }
                    } else {
                        // Fall back to ExternalCommandProcessor for file system commands and external commands
                        String result = externalCommandProcessor.executeCommand(trimmed);
                        if (result != null && !result.isEmpty()) {
                            terminal.writer().println(result);
                        }
                    }
                }
                terminal.writer().flush();
            } catch (UserInterruptException e) {
                // Ctrl-C: just move to next prompt
                terminal.writer().println("^C");
                terminal.writer().flush();
                continue;
            } catch (EndOfFileException e) {
                // Ctrl-D / EOF: exit
                break;
            }
        }

        terminal.writer().println(WindowsCompatibility.Symbols.WAVE + " Goodbye!");
        terminal.writer().flush();

        terminal.close();
    }
    
    private static void executeCFML(String cfmlCode) {
        try {
            // Initialize Lucee engine if not already done
            if (luceeEngine == null) {
                if(LuCLI.verbose) {
                    terminal.writer().println(WindowsCompatibility.Symbols.TOOL + " Initializing Lucee CFML engine...");
                    terminal.writer().flush();
                }
                
                luceeEngine = LuceeScriptEngine.getInstance(LuCLI.verbose, LuCLI.debug); // non-verbose for cleaner output
                if(LuCLI.verbose) {
                    terminal.writer().println(WindowsCompatibility.Symbols.SUCCESS + " Lucee engine ready.");
                }
            }
            
            // Wrap the expression to capture and return the result
            String wrappedScript = createOutputScript(cfmlCode);
            
            //We can also directly add a variable. So we dont even need to replace.
            // Execute the CFML code
            Object result = luceeEngine.eval(wrappedScript);
            
            // The output should already be printed by writeOutput in the script
            // but we can also handle direct results if needed
            if (result != null && !result.toString().trim().isEmpty() && LuCLI.debug) {
                terminal.writer().println(result.toString());
            }
            terminal.writer().println("");

        } catch (Exception e) {
            terminal.writer().println(WindowsCompatibility.Symbols.ERROR + " Error executing CFML: " + e.getMessage());
            if (e.getCause() != null) {
                terminal.writer().println("Cause: " + e.getCause().getMessage());
            }
        }
        terminal.writer().flush();
    }
    
    private static String createOutputScript(String cfmlExpression) {
        try {
            // Read the external script template
            String scriptTemplate = readScriptTemplate("/script_engine/cfmlOutput.cfs");
            
            // Replace the placeholder with the actual expression
            String result = scriptTemplate.replace("${cfmlExpression}", cfmlExpression);
            
            // Post-process through StringOutput for emoji and placeholder handling
            return StringOutput.getInstance().process(result);
            
        } catch (Exception e) {
            // Fallback to inline generation if reading external script fails
            if (LuCLI.debug) {
                System.err.println("Warning: Failed to read external script template, using fallback: " + e.getMessage());
            }
            
            StringBuilder script = new StringBuilder();
            script.append("try {\n");
            script.append("  result = ").append(cfmlExpression).append(";\n");
            script.append("  if (isDefined('result')) {\n");
            script.append("    if (isSimpleValue(result)) {\n");
            script.append("      writeOutput(result);\n");
            script.append("    } else if (isArray(result)) {\n");
            script.append("      writeOutput('[' & arrayToList(result, ', ') & ']');\n");
            script.append("    } else if (isStruct(result)) {\n");
            script.append("      writeOutput(serializeJSON(result));\n");
            script.append("    } else {\n");
            script.append("      writeOutput(toString(result));\n");
            script.append("    }\n");
            script.append("  }\n");
            script.append("} catch (any e) {\n");
            script.append("  writeOutput('CFML Error: ' & e.message);\n");
            script.append("  if (len(e.detail)) {\n");
            script.append("    writeOutput(' - ' & e.detail);\n");
            script.append("  }\n");
            script.append("}\n");
            return script.toString();
        }
    }
    
    /**
     * Read a script template from resources
     */
    private static String readScriptTemplate(String templatePath) throws Exception {
        try (java.io.InputStream is = InteractiveTerminal.class.getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new java.io.FileNotFoundException("Script template not found: " + templatePath);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    private static void showLuceeVersion() {
        try {
            // Initialize Lucee engine if not already done
            if (luceeEngine == null) {
                terminal.writer().println(WindowsCompatibility.Symbols.TOOL + " Initializing Lucee CFML engine...");
                terminal.writer().flush();
                
                luceeEngine = LuceeScriptEngine.getInstance(true, false);
                terminal.writer().println(WindowsCompatibility.Symbols.SUCCESS + " Lucee engine ready.");
            }
            
            luceeEngine.eval("version = SERVER.LUCEE.version");
            Object version = luceeEngine.getEngine().get("version");
            terminal.writer().println("Lucee Version: " + version);
            
        } catch (Exception e) {
            terminal.writer().println(WindowsCompatibility.Symbols.ERROR + " Error getting Lucee version: " + e.getMessage());
        }
        terminal.writer().flush();
    }
    
    private static void showHelp() {
        terminal.writer().println("\nLuCLI Terminal Commands:");
        terminal.writer().println("  cfml <expression>   Execute CFML expression (e.g., cfml now())");
        terminal.writer().println("  help                Show this help message");
        terminal.writer().println("  version             Show LuCLI version");
        terminal.writer().println("  lucee-version       Show Lucee version");
        terminal.writer().println("  exit, quit          Exit the terminal");
        terminal.writer().println("  Ctrl-C              Interrupt current command");
        terminal.writer().println("  Ctrl-D              Exit the terminal");
        terminal.writer().println();
        
        // Show file system commands
        terminal.writer().println(commandProcessor.getAvailableCommands());
        
        terminal.writer().println("\nCFML Examples:");
        terminal.writer().println("  cfml now()");
        terminal.writer().println("  cfml dateFormat(now(), 'yyyy-mm-dd')");
        terminal.writer().println("  cfml listToArray('a,b,c')");
        terminal.writer().println("  cfml structKeyList({name: 'test', value: 123})");
        
        terminal.writer().println("\nFile System Examples:");
        terminal.writer().println("  ls -la");
        terminal.writer().println("  cd ..");
        terminal.writer().println("  mkdir mydir");
        terminal.writer().println("  cp file1.txt file2.txt");
        terminal.writer().println("  cat README.md");
        terminal.writer().println();
        
        terminal.writer().println("\nUnified Command Examples (CLI-Compatible):");
        terminal.writer().println("  server start --name myapp --version 7.0.0.123");
        terminal.writer().println("  server stop --name myapp");
        terminal.writer().println("  server status --name myapp");
        terminal.writer().println("  server list");
        terminal.writer().println("  modules list");
        terminal.writer().println("  modules init mymodule");
        terminal.writer().println();
        
        terminal.writer().println("\nModule Execution Examples:");
        terminal.writer().println("  cfml_parser           Execute module by name");
        terminal.writer().println("  test-module arg1 arg2 Execute module with arguments");
        terminal.writer().println("  utility-functions     Run utility functions module");
        terminal.writer().println();
    }
    
    /**
     * Execute a module in terminal mode (similar to CLI mode logic)
     */
    private static void executeModule(String moduleName, String[] moduleArgs) throws Exception {
        // Capture output from module execution and display in terminal
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        
        try {
            // Redirect System.out/err to capture module output
            System.setOut(new java.io.PrintStream(baos));
            System.setErr(new java.io.PrintStream(baos));
            
            // Execute module using ModuleCommand (same as CLI mode)
            ModuleCommand.executeModuleByName(moduleName, moduleArgs);
            
            // Get captured output and display in terminal
            String output = baos.toString().trim();
            if (!output.isEmpty()) {
                System.out.println(output);
            }
            
        } finally {
            // Always restore original streams
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
    
    /**
     * Check if a command should be handled by the UnifiedCommandExecutor
     */
    private static boolean isMainCommand(String command) {
        // List of commands handled by UnifiedCommandExecutor
        String[] mainCommands = {
            "server", "modules", "monitor"
        };
        
        for (String mainCommand : mainCommands) {
            if (command.equalsIgnoreCase(mainCommand)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Parse command arguments respecting quotes
     */
    private static String[] parseArguments(String argsString) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '"';
        
        for (int i = 0; i < argsString.length(); i++) {
            char c = argsString.charAt(i);
            
            // Handle quotes
            if ((c == '"' || c == '\'') && (i == 0 || argsString.charAt(i-1) != '\\')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    currentArg.append(c);
                }
            }
            // Handle spaces (argument delimiter unless in quotes)
            else if (Character.isWhitespace(c) && !inQuotes) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0); // Clear builder
                }
            }
            // All other characters are part of the argument
            else {
                currentArg.append(c);
            }
        }
        
        // Add the last argument if any
        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }
        
        return args.toArray(new String[0]);
    }
    
    /**
     * Show Lucee version for non-interactive mode
     */
    private static void showLuceeVersionNonInteractive() {
        try {
            LuceeScriptEngine engine = LuceeScriptEngine.getInstance(true, false);
            
            if (engine == null) {
                System.out.println("LuceeScriptEngine instance is null. Lucee may not be properly initialized.");
                return;
            }

            engine.eval("version = SERVER.LUCEE.version");
            System.out.println("Lucee Version: " + engine.getEngine().get("version"));
            
        } catch (Exception e) {
            System.err.println("Error getting Lucee version: " + e.getMessage());
        }
    }
    
    /**
     * Show help for non-interactive mode
     */
    private static void showHelpNonInteractive() {
        System.out.println(StringOutput.loadText("/text/main-help.txt"));
    }
}

