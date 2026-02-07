package org.lucee.lucli.cli.commands.logic;

import picocli.CommandLine.Parameters;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.lucee.lucli.CommandProcessor;
import org.lucee.lucli.ExternalCommandProcessor;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.cli.LuCLICommand;

@Command(name = "xset", description = "Experimental variable assigbment command xset name=value", hidden = true)

public class XSetCommand implements Callable<Integer> {

    @Parameters(index = "0..*", paramLabel = "ASSIGNMENT", description = "Variable assignment in the form name=value")
    private List<String> assignments = new ArrayList<>();

    @Override
    public Integer call() throws Exception {

        for (String assignment : assignments) {
            int eq = assignment.indexOf("=");
            if (eq <= 0) {
                StringOutput.Quick.error("xset: invalid assignment: " + assignment + " (expected name=value)");
                continue;
            }

            String name = assignment.substring(0, eq).trim();
            String value = assignment.substring(eq + 1).trim();

            if (name.isEmpty()) {
                StringOutput.Quick.error("xset: invalid variable name in assignment: " + assignment);
                continue;
            }

            // Strip surrounding quotes from the raw value ("foo" -> foo), but
            // leave inner quotes intact so CFML/commands still see them.
            if (value.length() >= 2) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    value = value.substring(1, value.length() - 1);
                }
            }

            String resolvedValue = value;

            // Case 1: command substitution: VAR=$(command ...)
            if (isExecEvaluation(value)) {
                String innerCommand = getExecEvaluationInnerCommand(value);
                resolvedValue = executeAndCapture(innerCommand);
            }
            // Case 2: plain value. In .lucli scripts, secrets/placeholders will
            // already have been resolved before XSetCommand is invoked. In
            // terminal/CLI mode, we leave the value as-is for now.

            // Register in LuCLI's script environment + StringOutput placeholders
            LuCLI.scriptEnvironment.put(name, resolvedValue);
            StringOutput.getInstance().addPlaceholder(name, resolvedValue);
            System.out.println("Set " + name + " = " + resolvedValue);
        }
        return 0;
    }

    private boolean isExecEvaluation(String value) {
        return value != null && value.startsWith("$(") && value.endsWith(")");
    }

    private String getExecEvaluationInnerCommand(String value) {
        return value.substring(2, value.length() - 1).trim();
    }

    /**
     * Execute an inner command line and capture its output as a string.
     * - Picocli subcommands (cfml, modules, server, run, etc.) are executed
     * via a local CommandLine instance with System.out/err captured.
     * - All other commands are delegated to ExternalCommandProcessor.
     */
    private String executeAndCapture(String commandLine) {
        try {
            CommandProcessor commandProcessor = new CommandProcessor();
            ExternalCommandProcessor external = new ExternalCommandProcessor(commandProcessor,
                    commandProcessor.getSettings());
            CommandLine picocli = new CommandLine(new LuCLICommand());

            String[] parts = commandProcessor.parseCommand(commandLine);
            if (parts.length == 0) {
                return "";
            }

            String command = parts[0].toLowerCase();

            // First preference: Picocli subcommands (cfml, modules, server, run, etc.)
            if (picocli.getSubcommands().containsKey(command)) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.io.PrintStream originalOut = System.out;
                java.io.PrintStream originalErr = System.err;
                try {
                    System.setOut(new java.io.PrintStream(baos));
                    System.setErr(new java.io.PrintStream(baos));
                    picocli.execute(parts);
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
                return baos.toString().trim();
            }

            // Fallback: delegate to ExternalCommandProcessor (filesystem + external
            // commands)
            String ext = external.executeCommand(commandLine);
            return ext != null ? ext.trim() : "";

        } catch (Exception e) {
            StringOutput.Quick.error("xset: error executing command in $(...): " + e.getMessage());
            LuCLI.printDebugStackTrace(e);
            return "";
        }
    }
}
