package org.lucee.lucli;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.lucee.lucli.modules.ModuleCommand;


/**
 * Singleton ScriptEngine wrapper for Lucee CFML that handles all script execution responsibilities.
 * This class manages the JSR223 ScriptEngine lifecycle and provides methods for executing
 * various types of CFML scripts (CFM templates, CFC components, modules).
 * 
 * Responsibilities:
 * - Manages singleton ScriptEngine instance
 * - Handles script file type detection and execution
 * - Manages component and module loading/extraction
 * - Sets up script context and bindings
 * - Provides script execution error handling
 */
public class LuceeScriptEngine {
    
    private static final String ENGINE_NAME = "CFML";
    private static LuceeScriptEngine instance;
    private static final Object lock = new Object();
    
    private ScriptEngine engine;
    private boolean verbose;
    private boolean debug;
    
    /**
     * Private constructor - use getInstance() to get the singleton
     * @throws IOException 
     */
    private LuceeScriptEngine(boolean verboseMode, boolean debugMode) throws IOException {
        this.verbose = verboseMode;
        this.debug = debugMode;
        
        if (verbose) {
            StringOutput.getInstance().println("${EMOJI_TOOL} Initializing LuceeScriptEngine singleton...");
        }
        
        try {
            this.engine = initializeEngine();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        if (verbose) {
            StringOutput.Quick.success("LuceeScriptEngine initialized successfully");
        }
    }
    
    /**
     * Get the singleton instance of LuceeScriptEngine
     * @throws IOException 
     */
    public static LuceeScriptEngine getInstance(boolean verboseMode, boolean debugMode) throws IOException {
        Timer.start("LuceeScriptEngine Initialization");
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {

                    instance = new LuceeScriptEngine(verboseMode, debugMode);
                }
            }
        }
        Timer.stop("LuceeScriptEngine Initialization");

        // CFMLEngineFactory.getInstance().getClassUtil().loadBIF(null, ENGINE_NAME, ENGINE_NAME, null)
        return instance;
    }
    /**
     * Initialize the JSR223 ScriptEngine for CFML
     * @throws Exception 
     */
    private ScriptEngine initializeEngine() throws Exception {
        configureLuceeDirectories();
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName(ENGINE_NAME);
        if (engine == null) {
            throw new IllegalStateException("CFML ScriptEngine not found. Make sure Lucee is properly configured.");
        }

        // Get the path to modules. 
        Path modulepath = ModuleCommand.getModulesDirectory();

        engine.put("lucli_modules_path", modulepath.toString());
        engine.put("__verboseMode", verbose);
        engine.put("__debugMode", debug);
        
        String script = readScriptTemplate("/script_engine/initializeEngine.cfs");
        engine.eval(script);
        return engine;
    }
    
    /**
     * Get direct access to the underlying ScriptEngine (use with caution)
     */
    public ScriptEngine getEngine() {
        return engine;
    }
    
    
    /**
     * Execute a simple CFML code snippet
     */
    public Object eval(String script) throws ScriptException {
        return engine.eval(script);
    }
    
    
    /**
     * Returns the version of lucee we are running
     * @return
     * @throws ScriptException 
     */
    public String getVersion() throws ScriptException {
    	instance.eval("version = SERVER.LUCEE.version");
        String version = (String)engine.get("version");
        return version;
    }

    private static String unwrap(String str) {
		if (str == null) return null;
		str = str.trim();
		if (str.length() == 0) return null;

		if (str.startsWith("\"") && str.endsWith("\"")) str = str.substring(1, str.length() - 1);
		if (str.startsWith("'") && str.endsWith("'")) str = str.substring(1, str.length() - 1);
		return str;
	}
    
    public void executeModule(String moduleName, String[] scriptArgs) throws Exception {

        // Parse key=value arguments into a map
        Map<String, String> argsMap = new java.util.HashMap<>();
        for (String arg : scriptArgs) {
            int equalsIndex = arg.indexOf('=');
            if (equalsIndex > 0) {
                String key = arg.substring(0, equalsIndex).trim();
                String value = arg.substring(equalsIndex + 1).trim();
                // Remove quotes if present
                value = unwrap(value);
                argsMap.put(key, value);
            }
        }

            Timer.start("Module Execution: " + moduleName);
            if (isVerboseMode() || isDebugMode()) {
                System.out.println("=== Direct Module Execution Script ===");
                
            }

            // TOOD: add timing
            setupScriptContext(engine, moduleName, scriptArgs);
            engine.put("moduleName", moduleName);

            engine.put("argsCollection", Arrays.asList(scriptArgs));
            engine.put("argCollection", argsMap);

            engine.put("verbose", LuCLI.verbose);
            engine.put("timing", LuCLI.timing);

            String script = readScriptTemplate("/script_engine/executeModule.cfs");
            engine.eval(script);
            Timer.stop("Module Execution: " + moduleName);
    }


    /**
     * Main script execution entry point - determines file type and executes appropriately
     */
    public void executeScript(String scriptFile, String[] scriptArgs) throws Exception {
        Timer.start("File Validation");
        // Validate script file exists
        Path scriptPath = Paths.get(scriptFile);
        if (!Files.exists(scriptPath)) {
            throw new FileNotFoundException("Script file not found: " + scriptFile);
        }
        Timer.stop("File Validation");
        
        Timer.start("File Type Detection");
        // Determine file type: .cfc (component), .cfm (template), .cfs (script)
        String fileName = scriptFile.toLowerCase();
        boolean isCFC = fileName.endsWith(".cfc");
        boolean isCFM = fileName.endsWith(".cfm");
        // boolean isCFS = fileName.endsWith(".cfs");
        Timer.stop("File Type Detection");
        
        Timer.start("Read Script Content");
        // Read script content
        String scriptContent = Files.readString(scriptPath);
        Timer.stop("Read Script Content");
        
        if (isCFC) {
            Timer.start("Component Execution");
            executeComponent(scriptFile, scriptContent, scriptArgs);
            Timer.stop("Component Execution");
         } else if (isCFM) {
            Timer.start("Template Execution");
            executeTemplate(scriptFile, scriptContent, scriptArgs);
            Timer.stop("Template Execution");
        } else {
            Timer.start("Script Content Execution");
            // Default to script execution (.cfs files or others)
            executeScriptContent(scriptFile, scriptContent, scriptArgs);
            Timer.stop("Script Content Execution");
        }
    }

    /**
     * Execute script content directly (for .cfs files)
     */
    private void executeScriptContent(String scriptFile, String scriptContent, String[] scriptArgs) throws Exception {
        if (verbose) {
            StringOutput.getInstance().println("${EMOJI_GEAR} Executing CFS script: " + scriptFile);
            StringOutput.getInstance().println("${EMOJI_INFO} Arguments: " + Arrays.toString(scriptArgs));
        }
        
        // Process shebang if present
        String processedContent = processShebang(scriptContent);
        
        executeWrappedScript(processedContent, scriptFile, scriptArgs);
    }
    
    /**
     * Execute a CFC component
     */
    private void executeComponent(String scriptFile, String scriptContent, String[] scriptArgs) throws Exception {
        if (isVerboseMode()) {
            StringOutput.getInstance().println("${EMOJI_GEAR} Executing CFC component: " + scriptFile);
            StringOutput.getInstance().println("${EMOJI_INFO} Arguments: " + Arrays.toString(scriptArgs));
        }
        
        // Check if this is an extracted module from ~/.lucli
        if (isExtractedModule(scriptFile)) {
            // For extracted modules, execute directly with a simple argument setup
            String directScript = createModuleDirectScript(scriptContent, scriptArgs);
            
            if (isVerboseMode() || isDebugMode()) {
                System.out.println("=== Direct Module Execution Script ===");
                System.out.println(directScript);
                System.out.println("=== End Direct Script ===");
            }
            
            executeWrappedScript(directScript, scriptFile, scriptArgs);
        } else {
            // For regular CFC files, use component wrapper
            String wrappedScript = createComponentWrapper(scriptContent, scriptFile, scriptArgs);
            
            if (isVerboseMode() || isDebugMode()) {
                System.out.println("=== Generated CFC Wrapper Script ===");
                System.out.println(wrappedScript);
                System.out.println("=== End Wrapper Script ===");
            }
            
            executeWrappedScript(wrappedScript, scriptFile, scriptArgs);
        }
    }
    
    private void executeTemplate(String scriptFile, String scriptContent, String[] scriptArgs) throws Exception {
        if (isVerboseMode()) {
            StringOutput.getInstance().println("${EMOJI_GEAR} Executing CFS script: " + scriptFile);
            StringOutput.getInstance().println("${EMOJI_INFO} Arguments: " + Arrays.toString(scriptArgs));
        }
        
        // For CFS scripts, inject built-in variables directly into the script content
        // so they are available as CFML variables, not just engine bindings
        String scriptWithVariables = injectBuiltinVariables(scriptContent, scriptFile, scriptArgs);
        
        if (isVerboseMode() || isDebugMode()) {
            System.out.println("=== CFS Script with Built-in Variables ===");
            System.out.println(scriptWithVariables);
            System.out.println("=== End Script ===");
        }
        
        // CFM files can contain CFML tags and need to be processed as templates
        // The JSR223 engine should handle this, but we may need to wrap it appropriately
        executeWrappedScript(scriptWithVariables, scriptFile, scriptArgs);
    }
    

    private void executeWrappedScript(String scriptContent, String scriptFile, String[] scriptArgs) throws Exception {
        
        // Try internal component loading first, fall back to filesystem extraction if needed
        boolean useInternalComponents = tryInternalComponentLoading();
        
        if (!useInternalComponents) {
            // Copy built-in components from JAR to filesystem so Lucee can find them
            try {
                copyBuiltinComponents();
            } catch (IOException e) {
                if (isDebugMode()) {
                    System.err.println("Warning: Failed to copy builtin components: " + e.getMessage());
                }
            }
            
            // Copy module components if this is a module execution
            try {
                copyModuleComponents(scriptFile);
            } catch (IOException e) {
                if (isDebugMode()) {
                    System.err.println("Warning: Failed to copy module components: " + e.getMessage());
                }
            }
        }
        
        
        
        if (engine == null) {
            throw new ScriptException("CFML ScriptEngine not found. Make sure Lucee is properly configured.");
        }
        
        // Set up built-in variables using centralized manager
        try {
            BuiltinVariableManager variableManager = BuiltinVariableManager.getInstance(verbose, debug);
            variableManager.setupBuiltinVariables(engine, scriptFile, scriptArgs);
            
            // Set up component mapping for ~/.lucli directory
            // Path lucliHome = getLucliHomeDirectory();
            // String mappingScript = createLucliMappingScript(lucliHome);
          
        } catch (IOException e) {
            if (isDebugMode()) {
                System.err.println("Warning: Failed to set up built-in variables: " + e.getMessage());
            }
        } catch (Exception e) {
            if (isDebugMode()) {
                System.err.println("Warning: Failed to set up lucli mapping: " + e.getMessage());
            }
        }
        
        // Execute the script
        if (isVerboseMode()) {
            System.out.println("Executing: " + scriptFile);
            System.out.println("Arguments: " + Arrays.toString(scriptArgs));
            System.out.println("Engine: " + engine.getClass().getName());
        }
        
        // System.out.println("Executing CFML script: " + scriptContent);
        boolean isScript = scriptFile.toLowerCase().endsWith(".cfs") || scriptFile.toLowerCase().endsWith(".cfc");
        try {
            Timer.start("CFML Script Evaluation");
            Object result;
            if(isScript){
                result = engine.eval(scriptContent);
            }
            else {
                
                result = engine.eval("```" + scriptContent + "```");
            }
            Timer.stop("CFML Script Evaluation");
            
            
            // Handle result if any
            if (result != null && isVerboseMode()) {
                System.out.println("Script result: " + result);
            }
            
        } catch (ScriptException e) {

            if(isDebugMode()) {
                e.printStackTrace();
            }
            throw new ScriptException("Error executing CFML script '" + scriptFile +  "': " + e.getMessage());
            
        }
    }

    private void setupScriptContext(ScriptEngine engine, String scriptFile, String[] scriptArgs) throws IOException {

        Bindings bindings = engine.createBindings();
        // Set current working directory
        bindings.put("__cwd", System.getProperty("user.dir"));
        // Set script file information
        bindings.put("__scriptFile", scriptFile);
        bindings.put("__scriptPath", Paths.get(scriptFile).toAbsolutePath().toString());
        
        // Handle case where file is in current directory (getParent() returns null)
        Path parent = Paths.get(scriptFile).getParent();
        String scriptDir = parent != null ? parent.toAbsolutePath().toString() : System.getProperty("user.dir");
        bindings.put("__scriptDir", scriptDir);
        
        // Set arguments
        bindings.put("__arguments", scriptArgs);
        bindings.put("__argumentCount", scriptArgs.length);
        
        // Set individual arguments for easy access (similar to CGI.argv)
        for (int i = 0; i < scriptArgs.length; i++) {
            bindings.put("arg" + (i + 1), scriptArgs[i]);
        }
        
        // Environment variables
        bindings.put("__env", System.getenv());
        
        // System properties
        bindings.put("__systemProps", System.getProperties());
        
        // Add lucli home directory path for component mapping
        Path lucliHome = getLucliHomeDirectory();
        bindings.put("__lucliHome", lucliHome.toString());
        
        engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
        
        // // Set up component mapping for ~/.lucli directory
        // try {
        //     String mappingScript = createLucliMappingScript(lucliHome);
        //     if (isVerboseMode()) {
        //         System.out.println("Setting up lucli mapping: " + lucliHome);
        //         System.out.println("Mapping script: " + mappingScript);
        //     }
        //     engine.eval(mappingScript);
        // } catch (Exception e) {
        //     if (isDebugMode()) {
        //         System.err.println("Warning: Failed to set up lucli mapping: " + e.getMessage());
        //     }
        // }
        
        if (isVerboseMode()) {
            System.out.println("Script context variables set:");
            System.out.println("  __scriptFile: " + scriptFile);
            System.out.println("  __arguments: " + Arrays.toString(scriptArgs));
            System.out.println("  __lucliHome: " + lucliHome);
        }
    }
    
    // Helper methods - moved from LuCLI
    
    private boolean isVerboseMode() {
        return verbose;
    }
    
    private boolean isDebugMode() {
        return debug;
    }
    
    /**
     * Process shebang lines in CFML script content
     * Removes shebang line and optionally processes any shebang-specific options
     */
    private String processShebang(String scriptContent) {
        if (scriptContent == null || scriptContent.isEmpty()) {
            return scriptContent;
        }
        
        // Check if the first line starts with #!
        String[] lines = scriptContent.split("\n", 2);
        if (lines.length > 0 && lines[0].startsWith("#!")) {
            String shebangLine = lines[0];
            
            if (isVerboseMode()) {
                System.out.println("Found shebang line: " + shebangLine);
            }
            
            // Return script content without the shebang line
            if (lines.length > 1) {
                String remainingContent = lines[1];
                
                // Add comment showing original shebang for reference
                StringBuilder processed = new StringBuilder();
                processed.append("// Original shebang: ").append(shebangLine).append("\n");
                processed.append(remainingContent);
                
                if (isVerboseMode()) {
                    System.out.println("Processed script (shebang removed)");
                }
                
                return processed.toString();
            } else {
                // Only shebang line, no content
                return "// Empty script with shebang: " + shebangLine;
            }
        }
        
        // No shebang found, return original content
        return scriptContent;
    }
    
    /**
     * Check if a script file is an extracted module from ~/.lucli
     */
    private boolean isExtractedModule(String scriptFile) {
        
        Path scriptPath = Paths.get(scriptFile);
        
        if (scriptPath.getFileName() != null && 
            scriptPath.getFileName().toString().equals("Module.cfc")) {
            
            Path parent = scriptPath.getParent();
            if (parent != null) {
                Path grandParent = parent.getParent();
                
                if (grandParent != null && 
                    grandParent.getFileName().toString().equals("modules")) {
                    
                    Path greatGrandParent = grandParent.getParent();
                    if (greatGrandParent != null && 
                        greatGrandParent.getFileName().toString().equals(".lucli")) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Create a direct execution script for extracted modules
     */
    private String createModuleDirectScript(String moduleContent, String[] scriptArgs) {
        try {
            // Read the external script template
            String scriptTemplate = readScriptTemplate("/script_engine/moduleDirectExecution.cfs");
            
            // Build argument setup code
            StringBuilder argSetup = new StringBuilder();
            for (int i = 0; i < scriptArgs.length; i++) {
                argSetup.append("arrayAppend(args, '").append(scriptArgs[i].replace("'", "''")).append("');\n");
            }
            
            // Determine execution content based on component structure
            String executionContent;
            boolean hasHelperFunctions = hasHelperFunctions(moduleContent);
            
            if (hasHelperFunctions) {
                // For modules with helper functions, convert the entire component to script format
                executionContent = "// Module has helper functions - converting entire component to script\n" +
                                 convertComponentToScript(moduleContent, scriptArgs);
            } else {
                // For simple modules, try to extract just the main function
                String mainFunctionContent = extractMainFunction(moduleContent);
                if (mainFunctionContent != null && !mainFunctionContent.trim().isEmpty()) {
                    executionContent = "// Extracted main function content:\n" + mainFunctionContent;
                } else {
                    // Fallback: wrap the entire component in a script block
                    executionContent = "// Fallback: execute entire module content\n" +
                                     "// Note: Component syntax may cause issues\n" +
                                     moduleContent;
                }
            }
            
            // Get built-in variables setup
            BuiltinVariableManager variableManager = BuiltinVariableManager.getInstance(verbose, debug);
            String builtinSetup = variableManager.createVariableSetupScript(null, scriptArgs);
            
            // Replace placeholders and post-process
            String result = scriptTemplate
                .replace("${builtinVariablesSetup}", builtinSetup)
                .replace("${argumentSetup}", argSetup.toString())
                .replace("${moduleExecutionContent}", executionContent);
                
            // Post-process through StringOutput for emoji and placeholder handling
            return StringOutput.getInstance().process(result);
                
        } catch (Exception e) {
            // Fallback to inline generation if reading external script fails
            if (isDebugMode()) {
                StringOutput.Quick.warning("Failed to read external script template, using fallback: " + e.getMessage());
            }
            return createModuleDirectScriptFallback(moduleContent, scriptArgs);
        }
    }
    
    /**
     * Create component wrapper for CFC execution
     */
    private String createComponentWrapper(String componentContent, String scriptFile, String[] scriptArgs) {
        try {
            // Read the external script template
            String scriptTemplate = readScriptTemplate("/script_engine/componentWrapper.cfs");
            
            // Convert file path to component dotted path
            String componentPath = getComponentPath(scriptFile);
            
            if(scriptArgs == null) {
                scriptArgs = new String[0]; // Ensure we have an empty array if no args provided
            }
            if (isVerboseMode()) {
                System.out.println("Component path: " + componentPath);
            }
            
            // Build argument setup code
            StringBuilder argSetup = new StringBuilder();
            for (int i = 0; i < scriptArgs.length; i++) {
                argSetup.append("arrayAppend(args, '").append(scriptArgs[i].replace("'", "''")).append("');\n");
            }
            
            // Build component instantiation code
            String componentInstantiation;
            if (componentPath.startsWith("FILE:")) {
                // For extracted modules, we'll directly execute the component content
                componentInstantiation = "  // Module content will be executed directly\n" +
                                       "  // Component instantiation happens inline\n";
            } else {
                // Create component using dotted path
                componentInstantiation = "  obj = createObject('component', '" + componentPath + "');\n";
            }
            
            // Get built-in variables setup
            BuiltinVariableManager variableManager = BuiltinVariableManager.getInstance(verbose, debug);
            String builtinSetup = variableManager.createVariableSetupScript(scriptFile, scriptArgs);
            
            // Replace placeholders and post-process
            String result = scriptTemplate
                .replace("${builtinVariablesSetup}", builtinSetup)
                .replace("${scriptFile}", scriptFile)
                .replace("${componentPath}", componentPath)
                .replace("${argumentSetup}", argSetup.toString())
                .replace("${componentInstantiation}", componentInstantiation);
                
            // Post-process through StringOutput for emoji and placeholder handling
            return StringOutput.getInstance().process(result);
                
        } catch (Exception e) {
            // Fallback to inline generation if reading external script fails
            if (isDebugMode()) {
                System.err.println("Warning: Failed to read external script template, using fallback: " + e.getMessage());
            }
            return createComponentWrapperFallback(componentContent, scriptFile, scriptArgs);
        }
    }
    
    /**
     * Try to configure Lucee to load components internally from JAR resources
     */
    private boolean tryInternalComponentLoading() {
        // For now, this is complex to implement correctly with inheritance
        if (isVerboseMode()) {
            System.out.println("Internal component loading not implemented, using filesystem extraction");
        }
        return false; // Always fall back to filesystem extraction for now
    }
    
    /**
     * Get the lucli home directory (~/.lucli)
     */
    private Path getLucliHomeDirectory() throws IOException {
        Path homeDir = Paths.get(System.getProperty("user.home"), ".lucli");
        if (!Files.exists(homeDir)) {
            Files.createDirectories(homeDir);
            if (isVerboseMode()) {
                System.out.println("Created lucli home directory: " + homeDir);
            }
        }
        return homeDir;
    }
    
    /**
     * Copy module components from JAR resources to ~/.lucli/modules
     */
    private void copyModuleComponents(String scriptFile) throws IOException {
        // Placeholder implementation - copy specific module components as needed
        if (isVerboseMode()) {
            System.out.println("Module component copying placeholder for: " + scriptFile);
        }
    }
    
    /**
     * Copy built-in components from JAR resources to ~/.lucli/builtin
     */
    private void copyBuiltinComponents() throws IOException {
        // Placeholder implementation - copy builtin components as needed
        if (isVerboseMode()) {
            System.out.println("Builtin component copying placeholder");
        }
    }
    
    
    /**
     * Generate the correct component path for CFML createObject() calls
     */
    private String getComponentPath(String scriptFile) {
        // For extracted modules in ~/.lucli, use the absolute file path instead of dotted notation
        Path scriptPath = Paths.get(scriptFile);
        
        if (scriptPath.getFileName() != null && 
            scriptPath.getFileName().toString().equals("Module.cfc")) {
            
            Path parent = scriptPath.getParent();
            if (parent != null) {
                String potentialModuleName = parent.getFileName().toString();
                Path grandParent = parent.getParent();
                
                if (grandParent != null && 
                    grandParent.getFileName().toString().equals("modules")) {
                    
                    Path greatGrandParent = grandParent.getParent();
                    if (greatGrandParent != null && 
                        greatGrandParent.getFileName().toString().equals(".lucli")) {
                        // This is an extracted module - return the absolute file path
                        return "FILE:" + scriptPath.toAbsolutePath().toString();
                    }
                }
            }
        }
        
        // Regular file path - convert to dotted notation
        String componentPath = scriptFile;
        if (componentPath.toLowerCase().endsWith(".cfc")) {
            componentPath = componentPath.substring(0, componentPath.length() - 4); // Remove .cfc extension
        }
        componentPath = componentPath.replace('/', '.').replace('\\', '.'); // Convert path separators to dots
        return componentPath;
    }
    
    /**
     * Check if a component has helper functions
     */
    private boolean hasHelperFunctions(String componentContent) {
        if (componentContent == null || componentContent.trim().isEmpty()) {
            return false;
        }
        
        // Count all function declarations
        int functionCount = 0;
        int searchPos = 0;
        
        while (searchPos < componentContent.length()) {
            int functionIndex = componentContent.indexOf("function ", searchPos);
            if (functionIndex == -1) {
                break;
            }
            
            functionCount++;
            searchPos = functionIndex + 9; // "function ".length()
        }
        
        // If there's more than one function (main + helpers), return true
        boolean hasHelpers = functionCount > 1;
        
        if (isVerboseMode()) {
            System.out.println("Component has " + functionCount + " functions, helpers detected: " + hasHelpers);
        }
        
        return hasHelpers;
    }
    
    /**
     * Extract the main function content from a CFC component
     */
    private String extractMainFunction(String componentContent) {
        if (componentContent == null || componentContent.trim().isEmpty()) {
            return null;
        }
        
        // Find the main function in the component
        int mainStart = componentContent.indexOf("function main(");
        if (mainStart == -1) {
            mainStart = componentContent.indexOf("function main ");
        }
        
        if (mainStart == -1) {
            if (isVerboseMode()) {
                System.out.println("Could not find main function in component");
            }
            return null;
        }
        
        // Find the opening brace of the function
        int braceStart = componentContent.indexOf("{", mainStart);
        if (braceStart == -1) {
            return null;
        }
        
        // Find the matching closing brace
        int braceCount = 1;
        int currentPos = braceStart + 1;
        int braceEnd = -1;
        
        while (currentPos < componentContent.length() && braceCount > 0) {
            char ch = componentContent.charAt(currentPos);
            if (ch == '{') {
                braceCount++;
            } else if (ch == '}') {
                braceCount--;
                if (braceCount == 0) {
                    braceEnd = currentPos;
                    break;
                }
            }
            currentPos++;
        }
        
        if (braceEnd == -1) {
            return null;
        }
        
        // Extract the function body (without the outer braces)
        String functionBody = componentContent.substring(braceStart + 1, braceEnd).trim();
        
        // Fix variable scoping issues
        functionBody = fixVariableScoping(functionBody);
        
        if (isVerboseMode()) {
            System.out.println("Extracted main function body (" + functionBody.length() + " characters)");
        }
        
        return functionBody;
    }
    
    /**
     * Fix variable scoping issues in extracted function body
     */
    private String fixVariableScoping(String functionBody) {
        String fixed = functionBody.replaceAll("\\bvar\\s+", "");
        
        if (isVerboseMode() && !fixed.equals(functionBody)) {
            System.out.println("Fixed variable scoping by removing 'var' declarations");
        }
        
        return fixed;
    }
    
    /**
     * Convert an entire CFC component to script format
     */
    private String convertComponentToScript(String componentContent, String[] scriptArgs) {
        try {
            // Read the external script template
            String scriptTemplate = readScriptTemplate("/script_engine/componentToScript.cfs");
            
            // Process component content - remove wrapper and fix scoping
            String processedContent = processComponentContent(componentContent);
            
            // Replace the placeholder with the processed content
            String result = scriptTemplate.replace("${processedComponentContent}", processedContent);
            
            // Post-process through StringOutput for emoji and placeholder handling
            return StringOutput.getInstance().process(result);
            
        } catch (Exception e) {
            // Fallback to inline generation if reading external script fails
            if (isDebugMode()) {
                System.err.println("Warning: Failed to read external script template, using fallback: " + e.getMessage());
            }
            return convertComponentToScriptFallback(componentContent, scriptArgs);
        }
    }

    public void evalFile(String command, String[] scriptArgs) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'evalFile'");
    }
    
    /**
     * Read a script template from resources
     */
    private String readScriptTemplate(String templatePath) throws Exception {
        try (java.io.InputStream is = LuceeScriptEngine.class.getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new java.io.FileNotFoundException("Script template not found: " + templatePath);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Fallback method for createModuleDirectScript when external template fails
     */
    private String createModuleDirectScriptFallback(String moduleContent, String[] scriptArgs) {
        StringBuilder script = new StringBuilder();
        
        script.append("// Direct module execution - converted from component to script\n");
        
        // Set up arguments in multiple ways to ensure compatibility
        script.append("// Set up args parameter for function\n");
        script.append("args = [];\n");
        for (int i = 0; i < scriptArgs.length; i++) {
            script.append("arrayAppend(args, '").append(scriptArgs[i].replace("'", "''")).append("');\n");
        }
        
        script.append("\n// Set up arguments.args for legacy compatibility\n");
        script.append("arguments.args = args;\n");
        script.append("\n");
        
        // Check if the component has helper functions by looking for private function declarations
        boolean hasHelperFunctions = hasHelperFunctions(moduleContent);
        
        if (hasHelperFunctions) {
            // For modules with helper functions, convert the entire component to script format
            script.append("// Module has helper functions - converting entire component to script\n");
            String componentAsScript = convertComponentToScript(moduleContent, scriptArgs);
            script.append(componentAsScript);
        } else {
            // For simple modules, try to extract just the main function
            String mainFunctionContent = extractMainFunction(moduleContent);
            if (mainFunctionContent != null && !mainFunctionContent.trim().isEmpty()) {
                script.append("// Extracted main function content:\n");
                script.append(mainFunctionContent);
            } else {
                // Fallback: wrap the entire component in a script block
                script.append("// Fallback: execute entire module content\n");
                script.append("// Note: Component syntax may cause issues\n");
                script.append(moduleContent);
            }
        }
        
        return script.toString();
    }
    
    /**
     * Fallback method for createComponentWrapper when external template fails
     */
    private String createComponentWrapperFallback(String componentContent, String scriptFile, String[] scriptArgs) {
        StringBuilder wrapper = new StringBuilder();
        
        // Convert file path to component dotted path
        String componentPath = getComponentPath(scriptFile);
        
        if(scriptArgs == null) {
            scriptArgs = new String[0]; // Ensure we have an empty array if no args provided
        }
        if (isVerboseMode()) {
            System.out.println("Component path: " + componentPath);
        }
        
        // Use pure CFML script syntax without cfscript tags
        wrapper.append("// Auto-generated wrapper for CFC: ").append(scriptFile).append("\n");
        wrapper.append("// Component path: ").append(componentPath).append("\n\n");
        
        // Prepare arguments array
        wrapper.append("// Prepare arguments for main() method\n");
        wrapper.append("args = [];\n");
        for (int i = 0; i < scriptArgs.length; i++) {
            wrapper.append("arrayAppend(args, '").append(scriptArgs[i].replace("'", "''")).append("');\n");
        }
        
        wrapper.append("\n// Create component instance and call main()\n");
        wrapper.append("try {\n");
        
        // Handle different component path types
        if (componentPath.startsWith("FILE:")) {
            // For extracted modules, we'll directly execute the component content
            wrapper.append("  // Module content will be executed directly\n");
            wrapper.append("  // Component instantiation happens inline\n");
        } else {
            // Create component using dotted path
            wrapper.append("  obj = createObject('component', '").append(componentPath).append("');\n");
        }
        wrapper.append("  // Call init() method first to initialize component\n");
        wrapper.append("  if (structKeyExists(obj, 'init')) {\n");
        wrapper.append("    initResult = obj.init();\n");
        wrapper.append("  }\n");
        wrapper.append("  \n");
        wrapper.append("  // Try to call main() method if it exists\n");
        wrapper.append("  if (structKeyExists(obj, 'main')) {\n");
        wrapper.append("    result = obj.main(args);\n");
        wrapper.append("    if (isDefined('result') \u0026\u0026 len(result)) {\n");
            wrapper.append("      writeOutput(result \u0026 chr(10));\n");
        wrapper.append("    }\n");
        wrapper.append("  }\n");
        wrapper.append("  // No error message if main() doesn't exist - init() might be sufficient\n");
        
        wrapper.append("} catch (any e) {\n");
        wrapper.append("  writeOutput('Component execution error: ' & e.message & chr(10));\n");
        wrapper.append("  writeOutput('Detail: ' & e.detail & chr(10));\n");
        wrapper.append("}\n");
        
        return wrapper.toString();
    }
    
   
    /**
     * Process component content by removing wrapper and fixing scoping
     */
    private String processComponentContent(String componentContent) {
        // Remove the component wrapper and convert functions to script format
        String content = componentContent.trim();
        
        // Remove the outer component { } wrapper
        int componentPos = content.indexOf("component");
        if (componentPos != -1) {
            // Find the opening brace after the component keyword
            int braceStart = content.indexOf("{", componentPos);
            if (braceStart != -1) {
                // Find the matching closing brace for the component wrapper
                int braceCount = 1;
                int braceEnd = -1;
                int currentPos = braceStart + 1;
                
                while (currentPos < content.length() && braceCount > 0) {
                    char ch = content.charAt(currentPos);
                    if (ch == '{') {
                        braceCount++;
                    } else if (ch == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            braceEnd = currentPos;
                            break;
                        }
                    }
                    currentPos++;
                }
                
                if (braceEnd != -1) {
                    content = content.substring(braceStart + 1, braceEnd).trim();
                }
            }
        }
        
        // Remove public/private modifiers from function declarations
        content = content.replaceAll("\\b(private|public)\\s+function\\b", "function");
        
        // Fix variable scoping issues
        content = fixVariableScoping(content);
        
        return content;
    }
    
    /**
     * Evaluate CFML script with built-in variables available
     * This method ensures built-in variables are available for interactive and CLI usage
     * 
     * @param scriptContent The CFML script content to execute
     * @return The result of script execution
     * @throws Exception if script execution fails
     */
    public Object evalWithBuiltinVariables(String scriptContent) throws Exception {
        return evalWithBuiltinVariables(scriptContent, null, null);
    }
    
    /**
     * Create a script with built-in variables injected as CFML variables
     * This is useful for templates that need built-in variables available as CFML vars
     * 
     * @param scriptContent The base script content
     * @param scriptFile The script file path (can be null)
     * @param scriptArgs The script arguments (can be null) 
     * @return Script content with built-in variables prepended
     * @throws Exception if variable setup fails
     */
    public String injectBuiltinVariables(String scriptContent, String scriptFile, String[] scriptArgs) throws Exception {
        try {
            BuiltinVariableManager variableManager = BuiltinVariableManager.getInstance(verbose, debug);
            String variableSetup = variableManager.createVariableSetupScript(scriptFile, scriptArgs);
            
            // Prepend the variable setup to the script content
            StringBuilder fullScript = new StringBuilder();
            fullScript.append(variableSetup);
            fullScript.append("\n// === Original Script Content ===\n");
            fullScript.append(scriptContent);
            
            return fullScript.toString();
        } catch (IOException e) {
            if (isDebugMode()) {
                System.err.println("Warning: Failed to inject built-in variables: " + e.getMessage());
            }
            // Return original script content if injection fails
            return scriptContent;
        }
    }
    
    /**
     * Evaluate CFML script with built-in variables and specific script context
     * 
     * @param scriptContent The CFML script content to execute
     * @param scriptFile The script file path (can be null for interactive mode)
     * @param scriptArgs The script arguments (can be null)
     * @return The result of script execution
     * @throws Exception if script execution fails
     */
    public Object evalWithBuiltinVariables(String scriptContent, String scriptFile, String[] scriptArgs) throws Exception {
        if (engine == null) {
            throw new ScriptException("CFML ScriptEngine not found. Make sure Lucee is properly configured.");
        }
        
        // Set up built-in variables using centralized manager
        // try {
        //     BuiltinVariableManager variableManager = BuiltinVariableManager.getInstance(verbose, debug);
        //     variableManager.setupBuiltinVariables(engine, scriptFile, scriptArgs);
            
        //     // Set up component mapping for ~/.lucli directory
        //     Path lucliHome = getLucliHomeDirectory();
        //     String mappingScript = createLucliMappingScript(lucliHome);
        //     if (isVerboseMode()) {
        //         System.out.println("Setting up lucli mapping: " + lucliHome);
        //     }
        //     engine.eval(mappingScript);
        // } catch (IOException e) {
        //     if (isDebugMode()) {
        //         System.err.println("Warning: Failed to set up built-in variables: " + e.getMessage());
        //     }
        // } catch (Exception e) {
        //     if (isDebugMode()) {
        //         System.err.println("Warning: Failed to set up lucli mapping: " + e.getMessage());
        //     }
        // }
        
        // Execute the script
        return engine.eval(scriptContent);
    }
    
    /**
     * Fallback method for convertComponentToScript when external template fails
     */
    private String convertComponentToScriptFallback(String componentContent, String[] scriptArgs) {
        StringBuilder script = new StringBuilder();
        
        // Process component content
        String content = processComponentContent(componentContent);
        
        // Add the function content directly to script
        script.append(content);
        script.append("\n\n");
        
        // Add a call to main() at the end
        script.append("// Call the main function\n");
        script.append("if (isDefined('main')) {\n");
        script.append("  main(args);\n");
        script.append("} else {\n");
        script.append("  writeOutput('Main function not found in component' & chr(10));\n");
        script.append("}\n");
        
        return script.toString();
    }

     /**
     * Configure Lucee directories (copied from original LuCLI.java)
     */
    private void configureLuceeDirectories() throws java.io.IOException {
        // Allow customization of lucli home via environment variable or system property
        String lucliHomeStr = System.getProperty("lucli.home");
        if (lucliHomeStr == null) {
            lucliHomeStr = System.getenv("LUCLI_HOME");
        }
        if (lucliHomeStr == null) {
            String userHome = System.getProperty("user.home");
            lucliHomeStr = java.nio.file.Paths.get(userHome, ".lucli").toString();
        }
        
        java.nio.file.Path lucliHome = java.nio.file.Paths.get(lucliHomeStr);
        java.nio.file.Path luceeServerDir = lucliHome.resolve("lucee-server");
        java.nio.file.Path patchesDir = lucliHome.resolve("patches");
        
        // Create all necessary directories if they don't exist
        java.nio.file.Files.createDirectories(luceeServerDir);
        java.nio.file.Files.createDirectories(patchesDir);
        
        if (verbose || debug) {
            System.out.println(StringOutput.msg("config.lucee.directories"));
            System.out.println("  " + StringOutput.msg("config.lucli.home", lucliHome.toString()));
            System.out.println("  " + StringOutput.msg("config.lucee.server", luceeServerDir.toString()));
            System.out.println("  " + StringOutput.msg("config.patches", patchesDir.toString()));
        }

        // Set Lucee system properties
        System.setProperty("lucee.base.dir", luceeServerDir.toString());
        System.setProperty("lucee.server.dir", luceeServerDir.toString());
        System.setProperty("lucee.web.dir", luceeServerDir.toString());
        System.setProperty("lucee.patch.dir", patchesDir.toString());
        System.setProperty("lucee.controller.disabled", "true"); // Disable web controller for CLI
        
        // Ensure Lucee doesn't try to create default directories in system paths
        System.setProperty("lucee.controller.disabled", "true");
        System.setProperty("lucee.use.lucee.configs", "false");
    }
}
