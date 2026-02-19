package org.lucee.lucli.cli.commands.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.lucee.lucli.StringOutput;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Direct implementation of modules uninstall command
 */
@Command(
    name = "uninstall",
    description = "Uninstall (remove) a module"
)
public class ModulesUninstallCommandImpl implements Callable<Integer> {

    @Parameters(
        paramLabel = "MODULE_NAME",
        description = "Name of module to uninstall"
    )
    private String moduleName;

    @Override
    public Integer call() throws Exception {
        uninstallModule();
        return 0;
    }

    /**
     * Uninstall (remove) a module by deleting its directory under ~/.lucli/modules
     */
    private void uninstallModule() throws IOException {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            System.err.println("Module name is required for uninstall command.");
            System.exit(1);
        }

        Path modulesDir = getModulesDirectory();
        Path moduleDir = modulesDir.resolve(moduleName);

        if (!Files.exists(moduleDir)) {
            System.err.println("Module '" + moduleName + "' is not installed.");
            System.exit(1);
        }

        try (Stream<Path> walk = Files.walk(moduleDir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
        }

        StringOutput.Quick.success("Successfully uninstalled module: " + moduleName);
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
}
