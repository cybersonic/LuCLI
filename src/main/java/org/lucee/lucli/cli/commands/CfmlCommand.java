package org.lucee.lucli.cli.commands;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.Timer;

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
public class CfmlCommand implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose = false;

    @Option(names = {"-d", "--debug"}, description = "Enable debug output")
    private boolean debug = false;

    @Parameters(
        paramLabel = "EXPRESSION", 
        description = "CFML expression or code to execute",
        arity = "1..*"
    )
    private String[] cfmlParts;

    @Override
    public Integer call() throws Exception {
        // Set global flags for backward compatibility
        LuCLI.verbose = LuCLI.verbose || verbose;
        LuCLI.debug = LuCLI.debug || debug;
        
        if (cfmlParts == null || cfmlParts.length == 0) {
            System.err.println("Error: cfml command requires an expression. Example: cfml now()");
            return 1;
        }

        // Join all parts into a single CFML expression
        String cfmlCode = String.join(" ", cfmlParts);
        
        // Debug output
        if (LuCLI.debug) {
            System.err.println("[DEBUG CfmlCommand] cfmlParts: " + java.util.Arrays.toString(cfmlParts));
            System.err.println("[DEBUG CfmlCommand] cfmlCode: '" + cfmlCode + "'");
        }
        
        // Execute the CFML code
        executeCFMLNonInteractive(cfmlCode);
        
        return 0;
    }

    /**
     * Execute CFML code in non-interactive mode (one-shot command)
     * This is adapted from InteractiveTerminal.executeCFMLNonInteractive()
     */
    private void executeCFMLNonInteractive(String cfmlCode) {
        Timer.start("CFML Execution");
        
        try {
            LuceeScriptEngine luceeEngine;
            
            // Initialize Lucee engine
            Timer.start("Lucee Engine Initialization");
            if (LuCLI.verbose) {
                System.out.println("Initializing Lucee CFML engine...");
            }
            
            luceeEngine = LuceeScriptEngine.getInstance(LuCLI.verbose, LuCLI.debug);
            if (LuCLI.verbose) {
                System.out.println("Lucee engine ready.");
            }
            Timer.stop("Lucee Engine Initialization");
            
            // Wrap the expression to capture and return the result
            Timer.start("Script Generation");
            String wrappedScript = createOutputScript(cfmlCode);
            
            // Debug output
            if (LuCLI.debug) {
                System.err.println("[DEBUG CfmlCommand] Wrapped script:");
                System.err.println(wrappedScript);
                System.err.println("[DEBUG CfmlCommand] End wrapped script");
            }
            
            Timer.stop("Script Generation");
            
            // Execute the CFML code
            Timer.start("Script Execution");
            Object result = luceeEngine.eval(wrappedScript);
            Timer.stop("Script Execution");
            
            // The output should already be printed by writeOutput in the script
            // but we can also handle direct results if needed
            if (result != null && !result.toString().trim().isEmpty() && LuCLI.debug) {
                System.out.println(result.toString());
            }

        } catch (Exception e) {
            System.err.println("Error executing CFML: " + e.getMessage());
            if (e.getCause() != null && (LuCLI.verbose || LuCLI.debug)) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            if (LuCLI.debug) {
                e.printStackTrace();
            }
        } finally {
            Timer.stop("CFML Execution");
        }
    }
    
    /**
     * Create a CFML script that wraps the expression for output
     * This is adapted from InteractiveTerminal.createOutputScript()
     */
    private String createOutputScript(String cfmlExpression) {
        try {
            // Read the external script template
            String scriptTemplate = readScriptTemplate("/script_engine/cfmlOutput.cfs");
            
            // Replace the placeholder with the actual expression
            String result = scriptTemplate.replace("${cfmlExpression}", cfmlExpression);
            
            // Post-process through StringOutput for emoji and placeholder handling
            return org.lucee.lucli.StringOutput.getInstance().process(result);
            
        } catch (Exception e) {
            // Fallback to inline generation if reading external script fails
            if (LuCLI.debug) {
                System.err.println("Warning: Failed to read external script template, using fallback: " + e.getMessage());
            }
            
            StringBuilder script = new StringBuilder();
            script.append("try {\\n");
            script.append("  result = ").append(cfmlExpression).append(";\\n");
            script.append("  if (isDefined('result')) {\\n");
            script.append("    if (isSimpleValue(result)) {\\n");
            script.append("      writeOutput(result);\\n");
            script.append("    } else if (isArray(result)) {\\n");
            script.append("      writeOutput('[' & arrayToList(result, ', ') & ']');\\n");
            script.append("    } else if (isStruct(result)) {\\n");
            script.append("      writeOutput(serializeJSON(result));\\n");
            script.append("    } else {\\n");
            script.append("      writeOutput(toString(result));\\n");
            script.append("    }\\n");
            script.append("  }\\n");
            script.append("} catch (any e) {\\n");
            script.append("  writeOutput('CFML Error: ' & e.message);\\n");
            script.append("  if (len(e.detail)) {\\n");
            script.append("    writeOutput(' - ' & e.detail);\\n");
            script.append("  }\\n");
            script.append("}\\n");
            return script.toString();
        }
    }
    
    /**
     * Read a script template from resources
     */
    private String readScriptTemplate(String templatePath) throws Exception {
        try (java.io.InputStream is = CfmlCommand.class.getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new java.io.FileNotFoundException("Script template not found: " + templatePath);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}