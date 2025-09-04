package org.lucee.lucli.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.StringOutput;

/**
 * Handles module operations - listing, initializing, and managing CFML modules
 */
public class ModuleCommand {
    
    /**
     * Execute the module command with the given arguments
     */
    public static void executeModule(String[] args) {
        ModuleOptions options = parseArguments(args);
        
        if (options.showHelp) {
            showHelp();
            return;
        }
        
        try {
            switch (options.action) {
                case LIST:
                    listModules();
                    break;
                case INIT:
                    initModule(options.moduleName);
                    break;
                case RUN:
                    runModule(options.moduleName, options.moduleArgs);
                    break;
                default:
                    System.err.println("Unknown module action. Use --help for available commands.");
                    System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("Error executing module command: " + e.getMessage());
            if (options.verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    /**
     * List all available modules in ~/.lucli/modules
     */
    private static void listModules() throws IOException {
        Path modulesDir = getModulesDirectory();
        
        if (!Files.exists(modulesDir)) {
            StringOutput.getInstance().println("${EMOJI_INFO} No modules directory found at: " + modulesDir);
            StringOutput.getInstance().println("${EMOJI_BULB} Use 'lucli modules init <module-name>' to create your first module.");
            return;
        }
        
        System.out.println("LuCLI Modules");
        System.out.println("=============");
        System.out.println();
        System.out.println("Module directory: " + modulesDir);
        System.out.println();
        
        if (Files.list(modulesDir).count() == 0) {
            System.out.println("No modules found.");
            System.out.println("Use 'lucli modules init <module-name>' to create a new module.");
            return;
        }
        
        System.out.printf("%-20s %-10s %-50s%n", "NAME", "STATUS", "DESCRIPTION");
        System.out.println("─".repeat(80));
        
        Files.list(modulesDir)
            .filter(Files::isDirectory)
            .sorted()
            .forEach(moduleDir -> {
                String moduleName = moduleDir.getFileName().toString();
                String status = getModuleStatus(moduleDir);
                String description = getModuleDescription(moduleDir);
                
                System.out.printf("%-20s %-10s %-50s%n", moduleName, status, description);
            });
    }
    
    /**
     * Initialize a new module with the given name
     */
    private static void initModule(String moduleName) throws IOException {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            // Interactive mode - ask for module name
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter module name: ");
            moduleName = scanner.nextLine().trim();
            
            if (moduleName.isEmpty()) {
                System.err.println("Module name cannot be empty.");
                System.exit(1);
            }
        }
        
        // Validate module name
        if (!isValidModuleName(moduleName)) {
            System.err.println("Invalid module name. Module names should contain only letters, numbers, hyphens, and underscores.");
            System.exit(1);
        }
        
        Path modulesDir = getModulesDirectory();
        Path moduleDir = modulesDir.resolve(moduleName);
        
        if (Files.exists(moduleDir)) {
            System.err.println("Module '" + moduleName + "' already exists at: " + moduleDir);
            System.exit(1);
        }
        
        // Create module directory structure
        Files.createDirectories(moduleDir);
        
        // Create Module.cfc file
        createModuleFile(moduleDir, moduleName);
        
        // Create module.json metadata file
        createModuleMetadata(moduleDir, moduleName);
        
        // Create README.md
        createModuleReadme(moduleDir, moduleName);
        
        StringOutput.Quick.success("Successfully created module: " + moduleName);
        StringOutput.getInstance().println("${EMOJI_FOLDER} Module directory: " + moduleDir);
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Edit " + moduleDir.resolve("Module.cfc") + " to implement your module");
        System.out.println("  2. Update " + moduleDir.resolve("module.json") + " with module metadata");
        System.out.println("  3. Test your module with: lucli " + moduleDir.resolve("Module.cfc"));
    }
    
    /**
     * Get the modules directory (~/.lucli/modules)
     */
    private static Path getModulesDirectory() throws IOException {
        String lucliHomeStr = System.getProperty("lucli.home");
        if (lucliHomeStr == null) {
            lucliHomeStr = System.getenv("LUCLI_HOME");
        }
        if (lucliHomeStr == null) {
            String userHome = System.getProperty("user.home");
            lucliHomeStr = Paths.get(userHome, ".lucli").toString();
        }
        
        Path lucliHome = Paths.get(lucliHomeStr);
        Path modulesDir = lucliHome.resolve("modules");
        
        // Ensure the directories exist
        Files.createDirectories(modulesDir);
        
        return modulesDir;
    }
    
    /**
     * Get the status of a module (READY, INCOMPLETE, ERROR)
     */
    private static String getModuleStatus(Path moduleDir) {
        Path moduleFile = moduleDir.resolve("Module.cfc");
        Path metadataFile = moduleDir.resolve("module.json");
        
        if (Files.exists(moduleFile) && Files.exists(metadataFile)) {
            return "READY";
        } else if (Files.exists(moduleFile) || Files.exists(metadataFile)) {
            return "INCOMPLETE";
        } else {
            return "ERROR";
        }
    }
    
    /**
     * Get the description of a module from its metadata
     */
    private static String getModuleDescription(Path moduleDir) {
        Path metadataFile = moduleDir.resolve("module.json");
        
        if (!Files.exists(metadataFile)) {
            return "No description available";
        }
        
        try {
            String content = Files.readString(metadataFile);
            // Simple JSON parsing for description - in a real implementation, use Jackson
            if (content.contains("\"description\"")) {
                int start = content.indexOf("\"description\"");
                int colonPos = content.indexOf(":", start);
                int quoteStart = content.indexOf("\"", colonPos) + 1;
                int quoteEnd = content.indexOf("\"", quoteStart);
                if (quoteStart > 0 && quoteEnd > quoteStart) {
                    return content.substring(quoteStart, quoteEnd);
                }
            }
        } catch (IOException e) {
            // Ignore and return default
        }
        
        return "No description available";
    }
    
    /**
     * Validate module name
     */
    private static boolean isValidModuleName(String name) {
        return name != null && name.matches("^[a-zA-Z0-9_-]+$") && !name.isEmpty();
    }
    
    /**
     * Create the main Module.cfc file
     */
    private static void createModuleFile(Path moduleDir, String moduleName) throws IOException {
        String moduleContent = String.format(
            "component {\n" +
            "    /**\n" +
            "     * %s Module\n" +
            "     * \n" +
            "     * This is the main entry point for your module.\n" +
            "     * Implement your module logic in the main() function.\n" +
            "     */\n" +
            "    \n" +
            "    function init() {\n" +
            "        // Module initialization code goes here\n" +
            "        return this;\n" +
            "    }\n" +
            "    \n" +
            "    function main(args) {\n" +
            "        // Main module logic goes here\n" +
            "        writeOutput(\"Hello from %s module!\" & chr(10));\n" +
            "        writeOutput(\"Arguments passed: \" & arrayLen(args) & chr(10));\n" +
            "        \n" +
            "        for (var i = 1; i <= arrayLen(args); i++) {\n" +
            "            writeOutput(\"  Arg \" & i & \": \" & args[i] & chr(10));\n" +
            "        }\n" +
            "        \n" +
            "        return \"Module executed successfully\";\n" +
            "    }\n" +
            "    \n" +
            "    // Add your helper functions here\n" +
            "    \n" +
            "}\n",
            moduleName, moduleName
        );
        
        Files.writeString(moduleDir.resolve("Module.cfc"), moduleContent);
    }
    
    /**
     * Create module metadata file
     */
    private static void createModuleMetadata(Path moduleDir, String moduleName) throws IOException {
        String metadataContent = String.format(
            "{\n" +
            "  \"name\": \"%s\",\n" +
            "  \"version\": \"1.0.0\",\n" +
            "  \"description\": \"A new LuCLI module\",\n" +
            "  \"author\": \"Your Name\",\n" +
            "  \"license\": \"MIT\",\n" +
            "  \"keywords\": [\"lucli\", \"module\", \"cfml\"],\n" +
            "  \"main\": \"Module.cfc\",\n" +
            "  \"created\": \"%s\"\n" +
            "}\n",
            moduleName,
            java.time.Instant.now().toString()
        );
        
        Files.writeString(moduleDir.resolve("module.json"), metadataContent);
    }
    
    /**
     * Create module README.md file
     */
    private static void createModuleReadme(Path moduleDir, String moduleName) throws IOException {
        String readmeContent = String.format(
            "# %s\n" +
            "\n" +
            "A LuCLI module that does amazing things.\n" +
            "\n" +
            "## Usage\n" +
            "\n" +
            "```bash\n" +
            "# Execute the module\n" +
            "lucli %s/Module.cfc\n" +
            "\n" +
            "# Execute with arguments\n" +
            "lucli %s/Module.cfc arg1 arg2 arg3\n" +
            "```\n" +
            "\n" +
            "## Description\n" +
            "\n" +
            "Add a detailed description of what your module does here.\n" +
            "\n" +
            "## Arguments\n" +
            "\n" +
            "- `arg1`: Description of first argument\n" +
            "- `arg2`: Description of second argument\n" +
            "\n" +
            "## Examples\n" +
            "\n" +
            "Add usage examples here.\n" +
            "\n" +
            "## Development\n" +
            "\n" +
            "This module was created with `lucli modules init %s`.\n",
            moduleName, moduleName, moduleName, moduleName
        );
        
        Files.writeString(moduleDir.resolve("README.md"), readmeContent);
    }
    
    /**
     * Parse command line arguments for module command
     */
    private static ModuleOptions parseArguments(String[] args) {
        ModuleOptions options = new ModuleOptions();
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            switch (arg) {
                case "-h":
                case "--help":
                    options.showHelp = true;
                    return options;
                    
                case "-v":
                case "--verbose":
                    options.verbose = true;
                    break;
                    
                case "list":
                    options.action = ModuleAction.LIST;
                    break;
                    
                case "init":
                    options.action = ModuleAction.INIT;
                    // Next argument is the module name (optional)
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        options.moduleName = args[i + 1];
                        i++; // Skip next argument as we consumed it
                    }
                    break;
                    
                case "run":
                    options.action = ModuleAction.RUN;
                    // Next argument should be the module name
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        options.moduleName = args[i + 1];
                        i++; // Skip next argument as we consumed it
                        
                        // Collect remaining arguments as module arguments
                        int remainingArgs = args.length - i - 1;
                        if (remainingArgs > 0) {
                            options.moduleArgs = new String[remainingArgs];
                            System.arraycopy(args, i + 1, options.moduleArgs, 0, remainingArgs);
                            i = args.length; // Break out of the loop
                        }
                    }
                    break;
                    
                default:
                    if (!arg.startsWith("-") && options.action == null) {
                        // Default action is list if no action specified
                        options.action = ModuleAction.LIST;
                    }
                    break;
            }
        }
        
        // Default action is list
        if (options.action == null) {
            options.action = ModuleAction.LIST;
        }
        
        return options;
    }
    
    /**
     * Show help information for the modules command
     */
    private static void showHelp() {
        System.out.println("LuCLI Module Management");
        System.out.println();
        System.out.println("Usage: lucli modules [command] [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  list                       List all installed modules (default)");
        System.out.println("  init [module-name]         Initialize a new module");
        System.out.println("  run <module-name> [args]   Run a specific module with arguments");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -v, --verbose              Enable verbose output");
        System.out.println("  -h, --help                 Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  lucli modules                          # List all modules");
        System.out.println("  lucli modules list                     # List all modules");
        System.out.println("  lucli modules init                     # Initialize module (interactive)");
        System.out.println("  lucli modules init my-awesome-module   # Initialize named module");
        System.out.println("  lucli modules run my-module arg1 arg2  # Run a module with arguments");
        System.out.println();
        System.out.println("Module Directory:");
        System.out.println("  Modules are stored in ~/.lucli/modules/");
        System.out.println("  Each module should have a Module.cfc file as the entry point.");
    }
    
    /**
     * Run a specific module with arguments
     */
    private static void runModule(String moduleName, String[] moduleArgs) throws Exception {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            System.err.println("Module name is required for run command.");
            System.exit(1);
        }
        
        Path modulesDir = getModulesDirectory();
        Path moduleDir = modulesDir.resolve(moduleName);
        
        if (!Files.exists(moduleDir)) {
            System.err.println("Module '" + moduleName + "' not found.");
            System.err.println("Available modules:");
            listModules();
            System.exit(1);
        }
        
        // Look for Module.cfc first
        Path moduleFile = moduleDir.resolve("Module.cfc");
        if (Files.exists(moduleFile)) {
            // Execute the Module.cfc with arguments
            LuceeScriptEngine engine = LuceeScriptEngine.getInstance(false, false);
            engine.executeScript(moduleFile.toString(), moduleArgs != null ? moduleArgs : new String[0]);
        } else {
            System.err.println("Module '" + moduleName + "' does not have a Module.cfc file.");
            System.exit(1);
        }
    }
    
    /**
     * Check if a module exists
     */
    public static boolean moduleExists(String moduleName) {
        try {
            Path modulesDir = getModulesDirectory();
            Path moduleDir = modulesDir.resolve(moduleName);
            Path moduleFile = moduleDir.resolve("Module.cfc");
            return Files.exists(moduleFile);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Execute a module by name with arguments (public method for LuCLI main)
     */
    public static void executeModuleByName(String moduleName, String[] moduleArgs) {
        try {
            runModule(moduleName, moduleArgs);
        } catch (Exception e) {
            System.err.println("Error executing module '" + moduleName + "': " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Module action enumeration
     */
    public enum ModuleAction {
        LIST, INIT, RUN
    }
    
    /**
     * Module options class
     */
    private static class ModuleOptions {
        ModuleAction action = null;
        String moduleName = null;
        String[] moduleArgs = null;
        boolean showHelp = false;
        boolean verbose = false;
    }
}
