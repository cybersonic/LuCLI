package org.lucee.lucli.cli.commands;

import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.Timer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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

    @Option(names = {"-t", "--timing"}, description = "Enable timing output")
    private boolean timing = false;

    @Override
    public Integer call() throws Exception {
        // Set global flags for backward compatibility
        LuCLI.verbose = LuCLI.verbose || verbose;
        LuCLI.debug = LuCLI.debug || debug;
        LuCLI.timing = LuCLI.timing || timing;
        
        // Initialize timing if requested
        Timer.setEnabled(LuCLI.timing);
        Timer.start("CFML Command Execution");
        
        try {
            if (cfmlParts == null || cfmlParts.length == 0) {
                System.err.println("Error: cfml command requires an expression. Example: cfml now()");
                return 1;
            }

            // Join all parts into a single CFML expression
            String cfmlCode = String.join(" ", cfmlParts);
            
            // Debug output
            LuCLI.printDebug("CfmlCommand", "cfmlParts: " + java.util.Arrays.toString(cfmlParts));
            LuCLI.printDebug("CfmlCommand", "cfmlCode: '" + cfmlCode + "'");
            
            // Execute the CFML code
            executeCFMLNonInteractive(cfmlCode);
            
            return 0;
        } finally {
            Timer.stop("CFML Command Execution");
            Timer.printResults();
        }
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
            LuCLI.printVerbose("Initializing Lucee CFML engine...");
            
            luceeEngine = LuceeScriptEngine.getInstance();
            LuCLI.printVerbose("Lucee engine ready.");
            Timer.stop("Lucee Engine Initialization");
            
            // Wrap the expression to capture and return the result
            Timer.start("Script Generation");
            String wrappedScript = createOutputScript(cfmlCode);
            
            // Debug output
            LuCLI.printDebug("CfmlCommand", "Wrapped script:\n" + wrappedScript + "\n[DEBUG CfmlCommand] End wrapped script");
            
            Timer.stop("Script Generation");
            
            // Execute the CFML code with built-in variables
            Timer.start("Script Execution");
            Object result = luceeEngine.evalWithBuiltinVariables(wrappedScript);
            Timer.stop("Script Execution");
            
            // The output should already be printed by writeOutput in the script
            // but we can also handle direct results if needed
            LuCLI.printDebug("CfmlCommand", "Result: " + (result != null ? result.toString() : "null"));

        } catch (Exception e) {
            System.err.println("Error executing CFML: " + e.getMessage());
            if (e.getCause() != null && (LuCLI.verbose || LuCLI.debug)) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            LuCLI.printDebugStackTrace(e);
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
            
            // Get built-in variables setup (no script file or args for CLI mode)
            try {
                org.lucee.lucli.BuiltinVariableManager variableManager = org.lucee.lucli.BuiltinVariableManager.getInstance(LuCLI.verbose, LuCLI.debug);
                String builtinSetup = variableManager.createVariableSetupScript(null, null);
                
                // Replace placeholders with the actual expression and built-in variables
                String result = scriptTemplate
                    .replace("${builtinVariablesSetup}", builtinSetup)
                    .replace("${cfmlExpression}", cfmlExpression);
                
                // Post-process through StringOutput for emoji and placeholder handling
                return org.lucee.lucli.StringOutput.getInstance().process(result);
            } catch (Exception e) {
                LuCLI.printDebug("CfmlCommand", "Warning: Failed to inject built-in variables: " + e.getMessage());
                // Fallback: just replace the expression without built-in variables
                String result = scriptTemplate.replace("${cfmlExpression}", cfmlExpression);
                return org.lucee.lucli.StringOutput.getInstance().process(result);
            }
            
        } catch (Exception e) {
            // Fallback to inline generation if reading external script fails
            LuCLI.printDebug("CfmlCommand", "Warning: Failed to read external script template, using fallback: " + e.getMessage());
            
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