package org.lucee.lucli.cli.commands;

import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.Timer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * CFML command for executing CFML expressions directly from command line
 */
@Command(
    name = "cfml",
    description = "Execute CFML expressions or code",
    footer = {
        "",
        "Examples:",
        "  lucli cfml 'now()'                    # Execute CFML expression",
        "  lucli cfml 'writeDump(server.lucee)'  # Dump server information",
        "  lucli cfml '1 + 2'                    # Simple expression",
        "  lucli cfml 'arrayNew(1)'              # Create array"
    }
)
public class CfmlCommand implements Callable<Object> {

    @Spec
    CommandSpec spec;

    // These are now in the root
    // @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    // private boolean verbose = false;

    // @Option(names = {"-d", "--debug"}, description = "Enable debug output")
    // private boolean debug = false;

    // @Option(names = {"-t", "--timing"}, description = "Enable timing output")
    // private boolean timing = false;
    @Parameters(
        paramLabel = "EXPRESSION", 
        description = "CFML expression or code to execute",
        arity = "1..*"
    )
    private String[] cfmlParts;


    public Object call() throws Exception {
      
        // Initialize timing if requested
        Timer.start("CFML Command Execution");
        Object result = null;
        try {
            if (cfmlParts == null || cfmlParts.length == 0) {
                System.err.println("Error: cfml command requires an expression. Example: cfml now()");
                return 1;
            }

            // Join all parts into a single CFML expression
            String cfmlCode = String.join(" ", cfmlParts);
            // We can capture output here if needed
            result = LuceeScriptEngine.getInstance().eval(cfmlCode);

        } 
        catch (Exception e) {
            System.err.println("Error in cfml command: " + e.getMessage());
            LuCLI.debugStack(e);
        }
        finally {
            Timer.stop("CFML Command Execution");
        }
        // Explicitly set the execution result so picocli.getExecutionResult() returns it
        if (spec != null && spec.commandLine() != null) {
            spec.commandLine().setExecutionResult(result);
        }
        return result;
    }

}