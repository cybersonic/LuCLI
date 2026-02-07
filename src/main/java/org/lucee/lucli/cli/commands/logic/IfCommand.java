package org.lucee.lucli.cli.commands.logic;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import org.lucee.lucli.CommandProcessor;
import org.lucee.lucli.ExternalCommandProcessor;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.cli.LuCLICommand;

/**
 * Experimental logical IF command.
 *
 * Usage (terminal and .lucli):
 *   if $(cfml fileExists("somefile")) $(echo "The file exists")
 *
 * Both arguments are expected to be exec-evaluation expressions of the form
 * $(command ...). The first is evaluated and interpreted as a boolean; if
 * truthy, the second expression is evaluated.
 */
@Command(
    name = "if",
    description = "Experimental logical if command: if $(cond) $(then)",
    hidden = true
)
public class IfCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "COND", description = "Condition expression: $(command ...)")
    private String conditionExpr;

    @Parameters(index = "1", paramLabel = "THEN", description = "Then expression: $(command ...)")
    private String thenExpr;

    @Override
    public Integer call() throws Exception {
        // Basic validation: both parameters must be present and of the form $(...)
        if (!isExecEvaluation(conditionExpr) || !isExecEvaluation(thenExpr)) {
            StringOutput.Quick.error(
                "if: both arguments must be exec expressions of the form $(command ...)");
            StringOutput.Quick.info(
                "Usage: if $(cfml fileExists(\"somefile\")) $(echo \"The file exists\")");
            return 1;
        }

        String condInner   = getExecEvaluationInnerCommand(conditionExpr);
        String condResult  = executeAndCapture(condInner);

        if (isTruthy(condResult)) {
            String thenInner = getExecEvaluationInnerCommand(thenExpr);
            executeAndCapture(thenInner); // ignore its return value by default
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
     * Simple truthiness: if output is exactly "true"/"false" (case-insensitive),
     * honor that; otherwise, non-empty is true, empty is false.
     */
    private boolean isTruthy(String output) {
        if (output == null) return false;
        String trimmed = output.trim();
        if (trimmed.isEmpty()) return false;

        String lower = trimmed.toLowerCase();
        if ("true".equals(lower))  return true;
        if ("false".equals(lower)) return false;

        // Fallback: any non-empty output is truthy
        return true;
    }

    /**
     * Execute an inner command line and capture its output as a string.
     * This mirrors the generic .lucli dispatch flow in a simplified form:
     * - Picocli subcommands (cfml, modules, server, run, etc.) are executed
     *   via a local CommandLine instance with System.out/err captured.
     * - All other commands are delegated to ExternalCommandProcessor.
     */
    private String executeAndCapture(String commandLine) {
        try {
            CommandProcessor commandProcessor = new CommandProcessor();
            ExternalCommandProcessor external =
                new ExternalCommandProcessor(commandProcessor, commandProcessor.getSettings());
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

            // Fallback: delegate to ExternalCommandProcessor (filesystem + external commands)
            String ext = external.executeCommand(commandLine);
            return ext != null ? ext.trim() : "";

        } catch (Exception e) {
            StringOutput.Quick.error("if: error executing command in $(...): " + e.getMessage());
            LuCLI.printDebugStackTrace(e);
            return "";
        }
    }
}