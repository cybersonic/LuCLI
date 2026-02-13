package org.lucee.lucli.cli.commands.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuceeScriptEngine;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Direct implementation of modules run command - executes a module with arguments
 */
@Command(
    name = "run",
    description = "Run a module",
    mixinStandardHelpOptions = true
)
public class ModulesRunCommandImpl implements Callable<Integer> {

    @Parameters(
        index = "0",
        paramLabel = "MODULE_NAME",
        description = "Name of the module to run"
    )
    private String moduleName;

    @picocli.CommandLine.Unmatched
    private java.util.List<String> moduleArgs = new java.util.ArrayList<>();

    @Override
    public Integer call() throws Exception {
        runModule();
        return 0;
    }

    /**
     * Run a specific module with arguments
     */
    private void runModule() throws Exception {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            System.err.println("Module name is required for run command.");
            System.exit(1);
        }
        
        Path modulesDir = getModulesDirectory();
        Path moduleDir = modulesDir.resolve(moduleName);
        
        if (!Files.exists(moduleDir)) {
            System.err.println("Module '" + moduleName + "' not found.");
            System.err.println("Available modules:");
            listAvailableModules(modulesDir);
            System.exit(1);
        }
        
        // Look for Module.cfc
        Path moduleFile = moduleDir.resolve("Module.cfc");
        if (Files.exists(moduleFile)) {
            // Execute the Module.cfc with arguments
            LuceeScriptEngine engine = LuceeScriptEngine.getInstance();
            String[] argsArray = moduleArgs != null ? moduleArgs.toArray(new String[0]) : new String[0];
            engine.executeModule(moduleName, argsArray);
        } else {
            System.err.println("Module '" + moduleName + "' does not have a Module.cfc file.");
            System.exit(1);
        }
    }

    /**
     * Get the modules directory (~/.lucli/modules)
     */
    private Path getModulesDirectory() throws IOException {
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
     * List available modules
     */
    private void listAvailableModules(Path modulesDir) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(modulesDir)) {
            stream.filter(Files::isDirectory)
                  .map(p -> p.getFileName().toString())
                  .sorted()
                  .forEach(name -> System.err.println("  - " + name));
        }
    }
}
