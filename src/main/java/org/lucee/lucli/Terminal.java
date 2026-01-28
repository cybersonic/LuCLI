package org.lucee.lucli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.terminal.TerminalBuilder;
import org.lucee.lucli.cli.LuCLICommand;
import org.lucee.lucli.cli.completion.PicocliStyledCompleter;
import org.lucee.lucli.modules.ModuleCommand;

import picocli.CommandLine;

/**
 * Terminal - Interactive REPL for LuCLI
 * 
 * Integrates PicocLI commands with JLine3 for a full-featured terminal experience.
 * CLI and terminal modes use the same command implementations.
 * 
 * Terminal-specific commands (cd, ls, pwd, etc.) are handled via CommandProcessor.
 * All PicocLI commands are automatically available.
 */
public class Terminal {
    
    private static LuceeScriptEngine luceeEngine;
    private static org.jline.terminal.Terminal terminal;
    private static CommandProcessor commandProcessor;
    private static ExternalCommandProcessor externalCommandProcessor;
    private static CommandLine picocliCommandLine;
    
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
     * This delegates directly to the main LuCLI entry point
     */
    private static void executeOneShotCommand(String[] args) throws Exception {
        // For one-shot commands, delegate to the main Picocli entry point
        // This ensures complete consistency between CLI and terminal modes
        LuCLI.main(args);
    }
    
    /**
     * Start interactive terminal mode with Picocli integration
     */
    private static void startInteractiveMode() throws Exception {
        // Configure Windows-friendly terminal environment
        WindowsSupport.configureTerminalEnvironment();
        
        // Initialize terminal
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .name("LuCLI Terminal")
                    .build();
        } catch (IOException e) {
            if (LuCLI.debug) {
                System.err.println("Failed to create system terminal, using dumb terminal: " + e.getMessage());
            }
            terminal = TerminalBuilder.builder()
                    .dumb(true)
                    .build();
        }
        
        // Initialize command processor for terminal-specific commands
        commandProcessor = new CommandProcessor();
        externalCommandProcessor = new ExternalCommandProcessor(commandProcessor, commandProcessor.getSettings());
        
        // Create Picocli CommandLine with root command
        LuCLICommand rootCommand = new LuCLICommand();
        picocliCommandLine = new CommandLine(rootCommand);
        // Treat subcommands as top-level commands in the shell
        // Note: We don't use setCommandName("") anymore as it causes IllegalStateException in SystemCompleter
        // Instead, we build a custom JLine completer that wraps Picocli.

        // Build a completer that combines styled picocli completion and LuCLI-specific completion
        Completer picocliCompleter = new PicocliStyledCompleter(picocliCommandLine);
        Completer lucliCompleter   = new LucliCompleter(commandProcessor);
        Completer completer        = new AggregateCompleter(picocliCompleter, lucliCompleter);
        
        // Configure Picocli for terminal mode (don't exit on errors)
        picocliCommandLine.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            terminal.writer().println(WindowsSupport.Symbols.ERROR + " " + ex.getMessage());
            if (LuCLI.verbose || LuCLI.debug) {
                ex.printStackTrace(terminal.writer());
            }
            return 0; // Don't exit terminal
        });
        
        // Set up history file
        Path homeDir = Paths.get(System.getProperty("user.home"));
        Path historyFile = homeDir.resolve(".lucli").resolve("history");
        
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .completer(completer)
                .variable(LineReader.HISTORY_FILE, historyFile)
                .variable(LineReader.HISTORY_SIZE, 1000)
                .variable(LineReader.HISTORY_FILE_SIZE, 2000)
                .build();
        
        // Print welcome message
        terminal.writer().println(WindowsSupport.Symbols.ROCKET + " LuCLI Terminal " + LuCLI.getVersion() + "  Type 'exit' or 'quit' to leave.");
        terminal.writer().println(WindowsSupport.Symbols.FOLDER + " Working Directory: " + commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
        
        if (LuCLI.verbose) {
            terminal.writer().println(WindowsSupport.Symbols.COMPUTER + " Use 'cfml <expression>' to execute CFML code, e.g., 'cfml now()'");
            terminal.writer().println(WindowsSupport.Symbols.FOLDER + " File system commands available: ls, cd, pwd, mkdir, cp, mv, rm, cat, etc.");
            terminal.writer().println(WindowsSupport.Symbols.TOOL + " Server commands: server start, server stop, server list, etc.");
            terminal.writer().println(WindowsSupport.Symbols.ART + " Type 'help' for more information!");
        }
        terminal.writer().flush();
        
        // Main REPL loop
        while (true) {
            try {
                // Generate dynamic prompt
                String prompt = commandProcessor.getPromptConfig().generatePrompt(commandProcessor.getFileSystemState());
                
                String line = reader.readLine(prompt);
                if (line == null) {
                    break; // EOF
                }
                
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                
                if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                    break;
                }
                
                // Dispatch command
                String result = dispatchCommand(trimmed);
                if (result != null && !result.isEmpty()) {
                    terminal.writer().println(result);
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
        
        terminal.writer().println(WindowsSupport.Symbols.WAVE + " Goodbye!");
        terminal.writer().flush();
        terminal.close();
    }
    
    /**
     * Dispatch a command from the terminal
     * 
     * This method determines whether to:
     * 1. Handle as terminal-specific command (cd, ls, pwd, etc.)
     * 2. Handle as CFML expression (cfml ...)
     * 3. Handle as module shortcut
     * 4. Delegate to Picocli for parsing
     * 5. Fall back to external command processor
     */
    private static String dispatchCommand(String commandLine) {
        try {
            // Normalize: if user types a full "lucli ..." command inside
            // the terminal, strip the leading "lucli" so we don't spawn
            // another LuCLI process from within an existing session.
            String effectiveLine = commandLine == null ? "" : commandLine.trim();
            if (!effectiveLine.isEmpty()) {
                String[] initial = parseCommandLine(effectiveLine);
                if (initial.length > 0 && "lucli".equalsIgnoreCase(initial[0])) {
                    if (initial.length == 1) {
                        return ""; // bare "lucli" -> no-op
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < initial.length; i++) {
                        if (i > 1) sb.append(' ');
                        sb.append(initial[i]);
                    }
                    effectiveLine = sb.toString();
                }
            }

            // Parse command line to get first word
            String[] parts = parseCommandLine(effectiveLine);
            if (parts.length == 0) {
                return "";
            }
            
            String command = parts[0].toLowerCase();
            
            // Handle special terminal commands
            switch (command) {
                case "help":
                case "--help":
                case "-h":
                    return showHelp();
                    
                case "version":
                case "--version":
                    return LuCLI.getVersionInfo(true);
                    
                case "lucee-version":
                case "--lucee-version":
                    return showLuceeVersion();
                    
                case "cfml":
                    // Handle CFML expression inline
                    if (parts.length > 1) {
                        String cfmlCode = commandLine.substring(5).trim();
                        return executeCFML(cfmlCode);
                    } else {
                        return WindowsSupport.Symbols.ERROR + " cfml command requires an expression. Example: cfml now()";
                    }
            }
            
            // Check if it's a terminal-only command (cd, ls, pwd, etc.)
            if (isTerminalOnlyCommand(command)) {
                return commandProcessor.executeCommand(effectiveLine);
            }
            
            // Check if it's a Picocli command
            if (isPicocliCommand(command)) {
                return executePicocliCommand(parts);
            }
            
            // Check if it's a module shortcut
            if (ModuleCommand.moduleExists(command)) {
                String[] moduleArgs = parts.length > 1 ? 
                    Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
                return executeModule(command, moduleArgs);
            }
            
            // Fall back to external command processor
            return externalCommandProcessor.executeCommand(effectiveLine);
            
        } catch (Exception e) {
            return WindowsSupport.Symbols.ERROR + " Error: " + e.getMessage();
        }
    }
    
    /**
     * Check if a command is terminal-only (not available in CLI mode)
     */
    private static boolean isTerminalOnlyCommand(String command) {
        return command.equals("ls") || command.equals("dir") ||
               command.equals("cd") || command.equals("pwd") ||
               command.equals("mkdir") || command.equals("rmdir") ||
               command.equals("rm") || command.equals("cp") ||
               command.equals("mv") || command.equals("cat") ||
               command.equals("touch") || command.equals("find") ||
               command.equals("wc") || command.equals("head") ||
               command.equals("tail") || command.equals("prompt") ||
               command.equals("edit") || command.equals("interactive") ||
               command.equals("cflint") || command.equals("run");
    }
    
    /**
     * Check if a command is a Picocli subcommand
     */
    private static boolean isPicocliCommand(String command) {
        return picocliCommandLine.getSubcommands().containsKey(command);
    }
    
    /**
     * Execute a command through Picocli
     * Captures output and returns it for display in terminal
     */
    private static String executePicocliCommand(String[] args) {
        // java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        // java.io.PrintStream originalOut = System.out;
        // java.io.PrintStream originalErr = System.err;
        
        // try {
        //     // Redirect System.out/err to capture output
        //     System.setOut(new java.io.PrintStream(baos));
        //     System.setErr(new java.io.PrintStream(baos));
            
            // Execute through Picocli
            picocliCommandLine.execute(args);
            
            // Get captured output
            // return baos.toString().trim();
            return ""; // Output already printed to terminal
            
        // } finally {
        //     // Always restore original streams
        //     System.setOut(originalOut);
        //     System.setErr(originalErr);
        // }
    }
    
    /**
     * Execute a module in terminal mode
     */
    private static String executeModule(String moduleName, String[] moduleArgs) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        
        try {
            System.setOut(new java.io.PrintStream(baos));
            System.setErr(new java.io.PrintStream(baos));
            
            ModuleCommand.executeModuleByName(moduleName, moduleArgs);
            
            return baos.toString().trim();
            
        } catch (Exception e) {
            return WindowsSupport.Symbols.ERROR + " Error executing module '" + moduleName + "': " + e.getMessage();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
    
    /**
     * Execute CFML code in terminal mode
     */
    private static String executeCFML(String cfmlCode) {
        try {
            // Initialize Lucee engine if not already done
            if (luceeEngine == null) {
                if (LuCLI.verbose) {
                    terminal.writer().println(WindowsSupport.Symbols.TOOL + " Initializing Lucee CFML engine...");
                    terminal.writer().flush();
                }
                
                luceeEngine = LuceeScriptEngine.getInstance();
                
                if (LuCLI.verbose) {
                    terminal.writer().println(WindowsSupport.Symbols.SUCCESS + " Lucee engine ready.");
                }
            }
            
            // Wrap the expression to capture and return the result
            String wrappedScript = createOutputScript(cfmlCode);
            
            // Capture output
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.PrintStream originalOut = System.out;
            
            try {
                System.setOut(new java.io.PrintStream(baos));
                
                // Execute the CFML code
                luceeEngine.evalWithBuiltinVariables(wrappedScript);
                
                System.out.println();
                return baos.toString().trim();
                
            } finally {
                System.setOut(originalOut);
            }
            
        } catch (Exception e) {
            return WindowsSupport.Symbols.ERROR + " Error executing CFML: " + e.getMessage();
        }
    }
    
    /**
     * Create CFML output script wrapper
     */
    private static String createOutputScript(String cfmlExpression) {
        try {
            String scriptTemplate = readScriptTemplate("/script_engine/cfmlOutput.cfs");
            
            try {
                BuiltinVariableManager variableManager = 
                    BuiltinVariableManager.getInstance(LuCLI.verbose, LuCLI.debug);
                String builtinSetup = variableManager.createVariableSetupScript(null, null);
                
                String result = scriptTemplate
                    .replace("${builtinVariablesSetup}", builtinSetup)
                    .replace("${cfmlExpression}", cfmlExpression);
                
                return StringOutput.getInstance().process(result);
            } catch (Exception e) {
                String result = scriptTemplate.replace("${cfmlExpression}", cfmlExpression);
                return StringOutput.getInstance().process(result);
            }
            
        } catch (Exception e) {
            // Fallback to simple wrapper
            return "result = " + cfmlExpression + "; if (isDefined('result')) writeOutput(result);";
        }
    }
    
    /**
     * Read a script template from resources
     */
    private static String readScriptTemplate(String templatePath) throws Exception {
        try (java.io.InputStream is = Terminal.class.getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new java.io.FileNotFoundException("Script template not found: " + templatePath);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Show Lucee version
     */
    private static String showLuceeVersion() {
        try {
            String luceeVersion = LuceeScriptEngine.getInstance().getVersion();
            return "Lucee Version: " + luceeVersion;
        } catch (Exception e) {
            return WindowsSupport.Symbols.ERROR + " Error getting Lucee version: " + e.getMessage();
        }
    }
    
    /**
     * Show help for terminal mode
     */
    private static String showHelp() {
        StringBuilder help = new StringBuilder();
        help.append("\nLuCLI Terminal Commands:\n\n");
        help.append("CFML Execution:\n");
        help.append("  cfml <expression>   Execute CFML expression (e.g., cfml now())\n\n");
        
        help.append("Server Management:\n");
        help.append("  server start        Start a Lucee server\n");
        help.append("  server stop         Stop a server\n");
        help.append("  server list         List all servers\n");
        help.append("  server status       Show server status\n");
        help.append("  server monitor      Monitor server via JMX\n\n");
        
        help.append("Module Management:\n");
        help.append("  modules list        List available modules\n");
        help.append("  modules run <name>  Run a module\n");
        help.append("  <module-name>       Module shortcut (e.g., lint)\n\n");
        
        help.append("File System:\n");
        help.append("  ls, cd, pwd         Navigate directories\n");
        help.append("  mkdir, rm, cp, mv   File operations\n");
        help.append("  cat, head, tail     View files\n\n");
        
        help.append("Terminal:\n");
        help.append("  help                Show this help\n");
        help.append("  version             Show version\n");
        help.append("  exit, quit          Exit terminal\n");
        help.append("  Ctrl-C              Interrupt command\n");
        help.append("  Ctrl-D              Exit terminal\n");
        
        return help.toString();
    }
    
    /**
     * Parse command line into array, respecting quotes
     */
    private static String[] parseCommandLine(String commandLine) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '"';
        
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            
            if ((c == '"' || c == '\'') && (i == 0 || commandLine.charAt(i-1) != '\\')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        
        return parts.toArray(new String[0]);
    }
}
