package org.lucee.lucli.cli.commands;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.WindowsSupport;

import picocli.CommandLine.Command;

/**
 * REPL command - starts an interactive CFML read-eval-print loop.
 * 
 * This provides a focused CFML-only environment for quick experimentation,
 * simpler than the full terminal mode.
 */
@Command(
    name = "repl",
    mixinStandardHelpOptions = true,
    description = "Start an interactive CFML REPL (read-eval-print loop)",
    footer = {
        "",
        "Examples:",
        "  lucli repl                    # Start CFML REPL",
        "",
        "Inside the REPL:",
        "  cfml> now()                   # Execute CFML expression",
        "  cfml> 1 + 2                   # Simple arithmetic",
        "  cfml> arrayNew(1)             # Create array",
        "  cfml> exit                    # Exit REPL"
    }
)
public class ReplCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        Terminal terminal = null;
        
        try {
            // Configure Windows-friendly terminal environment
            WindowsSupport.configureTerminalEnvironment();
            
            terminal = TerminalBuilder.builder()
                .system(true)
                .name("CFML REPL")
                .build();
            
            // Set up history file for REPL
            Path homeDir = Paths.get(System.getProperty("user.home"));
            Path historyFile = homeDir.resolve(".lucli").resolve("repl_history");
            
            LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .variable(LineReader.HISTORY_FILE, historyFile)
                .variable(LineReader.HISTORY_SIZE, 500)
                .build();
            
            // Print welcome banner
            terminal.writer().println(WindowsSupport.Symbols.ROCKET + " CFML REPL - LuCLI " + LuCLI.getVersion());
            terminal.writer().println(WindowsSupport.Symbols.INFO + " Type CFML expressions to evaluate. Type 'exit' or Ctrl+D to quit.");
            terminal.writer().println();
            terminal.writer().flush();
            
            // Initialize Lucee engine
            LuCLI.verbose("Initializing Lucee CFML engine...");
            LuceeScriptEngine engine = LuceeScriptEngine.getInstance();
            LuCLI.verbose("Lucee engine ready.");
            
            // Main REPL loop
            while (true) {
                try {
                    String line = reader.readLine("cfml> ");
                    
                    if (line == null) {
                        break; // EOF
                    }
                    
                    String trimmed = line.trim();
                    
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    
                    // Exit commands
                    if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                        break;
                    }
                    
                    // Help command
                    if (trimmed.equalsIgnoreCase("help") || trimmed.equals("?")) {
                        printHelp(terminal);
                        continue;
                    }
                    
                    // Execute CFML expression
                    try {
                        Object result = engine.eval(trimmed);
                        if (result != null) {
                            terminal.writer().println(result);
                        }
                    } catch (Exception e) {
                        terminal.writer().println(WindowsSupport.Symbols.ERROR + " " + e.getMessage());
                        if (LuCLI.debug && e.getCause() != null) {
                            terminal.writer().println("  Cause: " + e.getCause().getMessage());
                        }
                    }
                    
                    terminal.writer().flush();
                    
                } catch (UserInterruptException e) {
                    // Ctrl-C: just print ^C and continue
                    terminal.writer().println("^C");
                    terminal.writer().flush();
                } catch (EndOfFileException e) {
                    // Ctrl-D: exit
                    break;
                }
            }
            
            terminal.writer().println();
            terminal.writer().println(WindowsSupport.Symbols.WAVE + " Goodbye!");
            terminal.writer().flush();
            
        } finally {
            if (terminal != null) {
                terminal.close();
            }
        }
        
        return 0;
    }
    
    /**
     * Print help information for the REPL
     */
    private void printHelp(Terminal terminal) {
        terminal.writer().println();
        terminal.writer().println(WindowsSupport.Symbols.INFO + " CFML REPL Help");
        terminal.writer().println("  Enter any CFML expression to evaluate it.");
        terminal.writer().println();
        terminal.writer().println("  Special commands:");
        terminal.writer().println("    help, ?     Show this help");
        terminal.writer().println("    exit, quit  Exit the REPL");
        terminal.writer().println("    Ctrl+C      Cancel current input");
        terminal.writer().println("    Ctrl+D      Exit the REPL");
        terminal.writer().println();
        terminal.writer().println("  Examples:");
        terminal.writer().println("    now()");
        terminal.writer().println("    dateFormat(now(), 'yyyy-mm-dd')");
        terminal.writer().println("    arrayAppend([1,2,3], 4)");
        terminal.writer().println("    structNew()");
        terminal.writer().println();
    }
}
