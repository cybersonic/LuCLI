package org.lucee.lucli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.lucee.lucli.BuiltinVariableManager;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.cli.LuCLICommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Command to execute a script file.
 *
 * Primary use is executing CFML files (.cfm, .cfc, .cfs) as well as delegating
 * to the LuCLI script engine for `.lucli` batch scripts. This centralizes the
 * CFML file execution logic so that it is available as a first-class Picocli
 * command ("lucli run") and can also be used by the root-command shortcut
 * handling (e.g. "lucli somefile.cfm").
 */
@Command(
    name = "run",
    description = "Execute a CFML script (.cfm, .cfc, .cfs) or LuCLI script (.lucli)",
    footer = {
        "",
        "Examples:",
        "  lucli run hello.cfm                 # Execute CFML template",
        "  lucli run SomeComponent.cfc arg1   # Execute component entry point",
        "  lucli run script.cfs arg1 arg2     # Execute CFML script file",
        "  lucli run script.lucli             # Execute LuCLI batch script",
        "  lucli hello.cfm                    # Shortcut for 'lucli run hello.cfm'",
        "  lucli SomeComponent.cfc arg1       # Shortcut for 'lucli run SomeComponent.cfc arg1'",
        "  lucli script.lucli                 # Shortcut for 'lucli run script.lucli'"
    }
)
public class RunCommand implements Callable<Integer> {

    @ParentCommand
    private LuCLICommand parent;

    @Parameters(
        index = "0",
        paramLabel = "SCRIPT",
        description = "Path to CFML script file (.cfm, .cfc, .cfs)"
    )
    private String scriptPath;

    @Parameters(
        index = "1",
        arity = "0..*",
        paramLabel = "ARGS",
        description = "Arguments to pass to the script"
    )
    private String[] scriptArgs = new String[0];

    @Override
    public Integer call() throws Exception {
        return executeScriptFile();
    }

    private Integer executeScriptFile() {
        if (scriptPath == null || scriptPath.trim().isEmpty()) {
            StringOutput.Quick.error("Script path is required");
            return 1;
        }

        Path path = Paths.get(scriptPath);
        if (!Files.exists(path)) {
            StringOutput.Quick.error("File not found: " + scriptPath);
            return 1;
        }

        if (Files.isDirectory(path)) {
            StringOutput.Quick.error("'" + scriptPath + "' is a directory");
            return 1;
        }

        String fileName = path.getFileName().toString().toLowerCase();

        // Handle .lucli batch scripts by delegating to LuCLI's script engine
        if (fileName.endsWith(".lucli")) {
            try {
                return LuCLI.executeLucliScript(path.toString());
            } catch (Exception e) {
                StringOutput.Quick.error("Error executing LuCLI script '" + scriptPath + "': " + e.getMessage());
                LuCLI.printDebugStackTrace(e);
                return 1;
            }
        }

        // Otherwise, require a CFML file extension
        if (!fileName.endsWith(".cfm") && !fileName.endsWith(".cfc") && !fileName.endsWith(".cfs")) {
            StringOutput.Quick.error("'" + scriptPath + "' is not a CFML file (.cfm, .cfc, or .cfs)");
            return 1;
        }

        try {
            // Get or create the LuceeScriptEngine instance
            LuceeScriptEngine luceeEngine = LuceeScriptEngine.getInstance(LuCLI.verbose, LuCLI.debug);
            BuiltinVariableManager variableManager = BuiltinVariableManager.getInstance(LuCLI.verbose, LuCLI.debug);

            // For .cfs files, we need to inject built-in variables and ARGS manually
            if (fileName.endsWith(".cfs")) {
                String fileContent = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);

                StringBuilder scriptWithArgs = new StringBuilder();

                try {
                    String builtinSetup = variableManager.createVariableSetupScript(scriptPath, scriptArgs);
                    scriptWithArgs.append(builtinSetup);
                    scriptWithArgs.append("\n");
                } catch (Exception e) {
                    LuCLI.printDebug("RunCommand", "Warning: Failed to inject built-in variables: " + e.getMessage());
                }
                
                // ARGS array setup for backward compatibility
                scriptWithArgs.append("// Auto-generated ARGS array setup\\n");
                scriptWithArgs.append("ARGS = ['" + scriptPath + "'");
                if (scriptArgs != null && scriptArgs.length > 0) {
                    for (String arg : scriptArgs) {
                        scriptWithArgs.append(", '" + arg.replace("'", "''") + "'");
                    }
                }
                scriptWithArgs.append("];\n\n");
                scriptWithArgs.append(fileContent);

                LuCLI.printDebug("RunCommand", "Script with ARGS setup:\\n" + scriptWithArgs + "\\n[DEBUG] End of script");

                luceeEngine.evalWithBuiltinVariables(scriptWithArgs.toString(), scriptPath, scriptArgs);

            } else {
                // For .cfm and .cfc files, execute directly via LuceeScriptEngine
                luceeEngine.executeScript(path.toAbsolutePath().toString(), scriptArgs);
            }

            return 0;
        } catch (Exception e) {
            StringOutput.Quick.error("Error executing CFML script '" + scriptPath + "': " + e.getMessage());
            LuCLI.printDebugStackTrace(e);
            return 1;
        }
    }
}
