package org.lucee.lucli;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;

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
    
    private final ScriptEngine engine;
    private final boolean verbose;
    private final boolean debug;
    
    /**
     * Private constructor - use getInstance() to get the singleton
     */
    private LuceeScriptEngine(boolean verboseMode, boolean debugMode) {
        this.verbose = verboseMode;
        this.debug = debugMode;
        
        if (verbose) {
            System.out.println("Initializing LuceeScriptEngine singleton...");
        }
        
        this.engine = initializeEngine();
        
        if (verbose) {
            System.out.println("✅ LuceeScriptEngine initialized successfully");
        }
    }
    
    /**
     * Get the singleton instance of LuceeScriptEngine
     */
    public static LuceeScriptEngine getInstance(boolean verboseMode, boolean debugMode) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new LuceeScriptEngine(verboseMode, debugMode);
                }
            }
        }

        // CFMLEngineFactory.getInstance().getClassUtil().loadBIF(null, ENGINE_NAME, ENGINE_NAME, null)
        return instance;
    }
    /**
     * Initialize the JSR223 ScriptEngine for CFML
     */
    private ScriptEngine initializeEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName(ENGINE_NAME);
        if (engine == null) {
            throw new IllegalStateException("CFML ScriptEngine not found. Make sure Lucee is properly configured.");
        }
        
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
     * Main script execution entry point - determines file type and executes appropriately
     */
    public void executeScript(String scriptFile, String[] scriptArgs) throws Exception {
        // Validate script file exists
        Path scriptPath = Paths.get(scriptFile);
        if (!Files.exists(scriptPath)) {
            throw new FileNotFoundException("Script file not found: " + scriptFile);
        }
        
        // Determine file type: .cfc (component), .cfm (template), .cfs (script)
        String fileName = scriptFile.toLowerCase();
        boolean isCFC = fileName.endsWith(".cfc");
        boolean isCFM = fileName.endsWith(".cfm");
        boolean isCFS = fileName.endsWith(".cfs");
        
        // Read script content
        String scriptContent = Files.readString(scriptPath);
        
        if (isCFC) {
            executeComponent(scriptFile, scriptContent, scriptArgs);
        } else if (isCFM) {
            executeTemplate(scriptFile, scriptContent, scriptArgs);
        } else {
            // Default to script execution (.cfs files or others)
            executeScriptContent(scriptFile, scriptContent, scriptArgs);
        }
    }

    /**
     * Execute script content directly (for .cfs files)
     */
    private void executeScriptContent(String scriptFile, String scriptContent, String[] scriptArgs) throws Exception {
        if (verbose) {
            System.out.println("Executing CFS script: " + scriptFile);
            System.out.println("Arguments: " + Arrays.toString(scriptArgs));
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
            System.out.println("Executing CFC component: " + scriptFile);
            System.out.println("Arguments: " + Arrays.toString(scriptArgs));
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
            System.out.println("Executing CFM template: " + scriptFile);
            System.out.println("Arguments: " + Arrays.toString(scriptArgs));
        }
        
        // CFM files can contain CFML tags and need to be processed as templates
        // The JSR223 engine should handle this, but we may need to wrap it appropriately
        executeWrappedScript(scriptContent, scriptFile, scriptArgs);
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
        
        // Set up script context
        // setupScriptContext(engine, scriptFile, scriptArgs);
        
        // Execute the script
        if (isVerboseMode()) {
            System.out.println("Executing: " + scriptFile);
            System.out.println("Arguments: " + Arrays.toString(scriptArgs));
            System.out.println("Engine: " + engine.getClass().getName());
        }
        
        // System.out.println("Executing CFML script: " + scriptContent);
        boolean isScript = scriptFile.toLowerCase().endsWith(".cfs") || scriptFile.toLowerCase().endsWith(".cfc");
        try {

            Object result;
            if(isScript){
                result = engine.eval(scriptContent);
            }
            else {
                
                result = engine.eval("```" + scriptContent + "```");
            }
            
            
            // Handle result if any
            if (result != null && isVerboseMode()) {
                System.out.println("Script result: " + result);
            }
            
        } catch (ScriptException e) {
            throw new ScriptException("Error executing CFML script '" + scriptFile +  "': " + e.getMessage());
            
        }
    }

        private void setupScriptContext(ScriptEngine engine, String scriptFile, String[] scriptArgs) throws IOException {

        Bindings bindings = engine.createBindings();
        
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
        
        // Set up component mapping for ~/.lucli directory
        try {
            String mappingScript = createLucliMappingScript(lucliHome);
            if (isVerboseMode()) {
                System.out.println("Setting up lucli mapping: " + lucliHome);
                System.out.println("Mapping script: " + mappingScript);
            }
            engine.eval(mappingScript);
        } catch (Exception e) {
            if (isDebugMode()) {
                System.err.println("Warning: Failed to set up lucli mapping: " + e.getMessage());
            }
        }
        
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
        
        // Path scriptPath = Paths.get(scriptFile);
        
        // if (scriptPath.getFileName() != null && 
        //     scriptPath.getFileName().toString().equals("Module.cfc")) {
            
        //     Path parent = scriptPath.getParent();
        //     if (parent != null) {
        //         Path grandParent = parent.getParent();
                
        //         if (grandParent != null && 
        //             grandParent.getFileName().toString().equals("modules")) {
                    
        //             Path greatGrandParent = grandParent.getParent();
        //             if (greatGrandParent != null && 
        //                 greatGrandParent.getFileName().toString().equals(".lucli")) {
        //                 return true;
        //             }
        //         }
        //     }
        // }
        
        return false;
    }
    
    /**
     * Create a direct execution script for extracted modules
     */
    private String createModuleDirectScript(String moduleContent, String[] scriptArgs) {
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
     * Create component wrapper for CFC execution
     */
    private String createComponentWrapper(String componentContent, String scriptFile, String[] scriptArgs) {
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
     * Create CFML script to set up component mappings for lucli home directory
     */
    private String createLucliMappingScript(Path lucliHome) {
        StringBuilder script = new StringBuilder();
        script.append("// Set up lucli component mappings\n");
        
        // Try to set application mapping if we have access to application scope
        script.append("try {\n");
        script.append("  // Try to set up application mapping\n");
        script.append("  if (isDefined('application') && isStruct(application)) {\n");
        script.append("    if (!structKeyExists(application, 'mappings')) {\n");
        script.append("      application.mappings = {};\n");
        script.append("    }\n");
        script.append("    application.mappings['/modules'] = '").append(lucliHome.toString().replace("\\", "/")).append("/modules';\n");
        script.append("    application.mappings['/builtin'] = '").append(lucliHome.toString().replace("\\", "/")).append("/builtin';\n");
        script.append("  }\n");
        script.append("} catch (any e) {\n");
        script.append("  // Ignore mapping errors\n");
        script.append("}\n");
        
        return script.toString();
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
        StringBuilder script = new StringBuilder();
        
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

    public void evalFile(String command, String[] scriptArgs) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'evalFile'");
    }
}
