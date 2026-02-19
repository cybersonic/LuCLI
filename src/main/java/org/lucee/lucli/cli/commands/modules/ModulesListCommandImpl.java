package org.lucee.lucli.cli.commands.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.modules.ModuleConfig;
import org.lucee.lucli.modules.ModuleRepositoryIndex;
import org.lucee.lucli.ui.Table;

import picocli.CommandLine.Command;

/**
 * Lists available LuCLI modules (both installed and available from repository)
 * 
 * This command scans ~/.lucli/modules for installed modules and compares with
 * the bundled repository index to show what's available.
 */
@Command(
    name = "list",
    description = "List available modules"
)
public class ModulesListCommandImpl implements Callable<Integer> {

    @Override
    public Integer call() {
        try {
            listModules();
            return 0;
        } catch (Exception e) {
            System.err.println("Error listing modules: " + e.getMessage());
            return 1;
        }
    }

    /**
     * List installed modules under ~/.lucli/modules and known modules from
     * the bundled repository index.
     */
    private void listModules() throws IOException {
        Path modulesDir = getModulesDirectory();

        if (!Files.exists(modulesDir)) {
            StringOutput.getInstance().println("${EMOJI_INFO} No modules directory found at: " + modulesDir);
            StringOutput.getInstance().println("${EMOJI_BULB} Use 'lucli modules init <module-name>' to create your first module.");
            return;
        }

        // Build map of installed modules (name -> ModuleConfig)
        Map<String, ModuleConfig> installed = new TreeMap<>();
        try (java.util.stream.Stream<Path> stream = Files.list(modulesDir)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                ModuleConfig config = ModuleConfig.load(dir);
                installed.put(config.getName(), config);
            });
        }

        // Load repository index (bundled local.json)
        ModuleRepositoryIndex repoIndex = ModuleRepositoryIndex.loadDefault();
        Map<String, ModuleRepositoryIndex.RepoModule> repoModules = repoIndex.getModulesByName();

        if (installed.isEmpty() && repoModules.isEmpty()) {
            StringOutput.getInstance().println("${EMOJI_INFO} No modules found.");
            StringOutput.getInstance().println("${EMOJI_BULB} Use 'lucli modules init <module-name>' to create a new module.");
            return;
        }

        // Union of names from installed modules and repository modules
        Set<String> allNames = new TreeSet<>();
        allNames.addAll(installed.keySet());
        allNames.addAll(repoModules.keySet());

        // Build the table
        Table.Builder tableBuilder = Table.builder()
            .title("LuCLI Modules")
            .title("Module directory: " + modulesDir)
            .headers("NAME", "INSTALLED", "STATUS", "VERSION", "DESCRIPTION");

        int count = 0;
        for (String name : allNames) {
            ModuleConfig config = installed.get(name);
            
            String installedMarker;
            String status;
            String version;
            String description;
            
            if (config != null) {
                // Installed module - use ModuleConfig
                installedMarker = "${EMOJI_SUCCESS}";
                status = config.getStatus();
                version = config.getVersion();
                description = config.getDescription();
                
                // Fall back to repo description if local is default
                if ("No description available".equals(description)) {
                    ModuleRepositoryIndex.RepoModule repoMod = repoModules.get(name);
                    if (repoMod != null && repoMod.getDescription() != null && !repoMod.getDescription().isEmpty()) {
                        description = repoMod.getDescription();
                    }
                }
            } else {
                // Not installed - use repo metadata
                installedMarker = "";
                status = "AVAILABLE";
                ModuleRepositoryIndex.RepoModule repoMod = repoModules.get(name);
                version = (repoMod != null && repoMod.getVersion() != null) ? repoMod.getVersion() : "N/A";
                description = (repoMod != null && repoMod.getDescription() != null) ? repoMod.getDescription() : "No description available";
            }
            
            tableBuilder.row(name, installedMarker, status, version, description);
            count++;
        }
        
        tableBuilder.footer("Total: " + count + " module" + (count != 1 ? "s" : ""));
        tableBuilder.build().print();
    }

    /**
     * Get the modules directory path
     */
    private Path getModulesDirectory() {
        String lucliHome = System.getProperty("lucli.home");
        if (lucliHome == null || lucliHome.trim().isEmpty()) {
            lucliHome = System.getenv("LUCLI_HOME");
        }
        if (lucliHome == null || lucliHome.trim().isEmpty()) {
            lucliHome = System.getProperty("user.home") + "/.lucli";
        }
        return Paths.get(lucliHome).resolve("modules");
    }
}
