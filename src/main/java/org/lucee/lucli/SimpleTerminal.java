package org.lucee.lucli;

import java.nio.file.Paths;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class SimpleTerminal {
    private static LuceeScriptEngine luceeEngine;
    private static Terminal terminal;
    private static CommandProcessor commandProcessor;
    private static ExternalCommandProcessor externalCommandProcessor;
    
    public static void main(String[] args) throws Exception {
        terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        
        // Initialize command processor
        commandProcessor = new CommandProcessor();
        
        // Initialize external command processor with enhanced features
        externalCommandProcessor = new ExternalCommandProcessor(commandProcessor, commandProcessor.getSettings());

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new LuCLICompleter(commandProcessor))
                .variable(LineReader.HISTORY_FILE, Paths.get("~/.lucli/history"))
                .variable(LineReader.HISTORY_SIZE, 1000) // Maximum entries in memory
                .variable(LineReader.HISTORY_FILE_SIZE, 2000) // Maximum entries in file
                .build();

        terminal.writer().println("🚀 LuCLI Terminal " + LuCLI.getVersion() +"  Type 'exit' or 'quit' to leave.");
        terminal.writer().println("📁 Working Directory: " + commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
        if(LuCLI.verbose) {
            terminal.writer().println("💻 Use 'cfml <expression>' to execute CFML code, e.g., 'cfml now()'");
            terminal.writer().println("📁 File system commands available: ls, cd, pwd, mkdir, cp, mv, rm, cat, etc.");
            terminal.writer().println("🔧 External commands supported: git, npm, docker, grep, and more!");
            terminal.writer().println("🎨 Type 'prompt' to change your prompt style!");
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
                } else if (trimmed.equalsIgnoreCase("help")) {
                    showHelp();
                } else if (trimmed.equalsIgnoreCase("--version") || trimmed.equalsIgnoreCase("version")) {
                    terminal.writer().println(LuCLI.getVersionInfo());
                } else if (trimmed.equalsIgnoreCase("--lucee-version") || trimmed.equalsIgnoreCase("lucee-version")) {
                    showLuceeVersion();
                } else if (trimmed.isEmpty()) {
                    // Do nothing for empty lines
                    continue;
                } else {
                    // Try to execute command using ExternalCommandProcessor (handles both internal and external)
                    String result = externalCommandProcessor.executeCommand(trimmed);
                    if (result != null && !result.isEmpty()) {
                        terminal.writer().println(result);
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

        terminal.writer().println("👋 Goodbye!");
        terminal.writer().flush();

        terminal.close();
    }
    
    private static void executeCFML(String cfmlCode) {
        try {
            // Initialize Lucee engine if not already done
            if (luceeEngine == null) {
                if(LuCLI.verbose) {
                    terminal.writer().println("🔧 Initializing Lucee CFML engine...");
                    terminal.writer().flush();
                }
                
                luceeEngine = LuceeScriptEngine.getInstance(LuCLI.verbose, LuCLI.debug); // non-verbose for cleaner output
                if(LuCLI.verbose) {
                    terminal.writer().println("✅ Lucee engine ready.");
                }
            }
            
            // Wrap the expression to capture and return the result
            String wrappedScript = createOutputScript(cfmlCode);
            
            // Execute the CFML code
            Object result = luceeEngine.eval(wrappedScript);
            
            // The output should already be printed by writeOutput in the script
            // but we can also handle direct results if needed
            if (result != null && !result.toString().trim().isEmpty() && LuCLI.debug) {
                terminal.writer().println(result.toString());
            }
            terminal.writer().println("");

        } catch (Exception e) {
            terminal.writer().println("❌ Error executing CFML: " + e.getMessage());
            if (e.getCause() != null) {
                terminal.writer().println("Cause: " + e.getCause().getMessage());
            }
        }
        terminal.writer().flush();
    }
    
    private static String createOutputScript(String cfmlExpression) {
        StringBuilder script = new StringBuilder();
        
        // Wrap the expression in a try-catch block and output the result
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
    
    private static void showLuceeVersion() {
        try {
            // Initialize Lucee engine if not already done
            if (luceeEngine == null) {
                terminal.writer().println("🔧 Initializing Lucee CFML engine...");
                terminal.writer().flush();
                
                luceeEngine = LuceeScriptEngine.getInstance(true, false);
                terminal.writer().println("✅ Lucee engine ready.");
            }
            
            luceeEngine.eval("version = SERVER.LUCEE.version");
            Object version = luceeEngine.getEngine().get("version");
            terminal.writer().println("Lucee Version: " + version);
            
        } catch (Exception e) {
            terminal.writer().println("❌ Error getting Lucee version: " + e.getMessage());
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
    }
}

