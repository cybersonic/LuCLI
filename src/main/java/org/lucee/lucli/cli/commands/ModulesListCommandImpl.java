package org.lucee.lucli.cli.commands;

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
import org.lucee.lucli.modules.ModuleRepositoryIndex;

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
     * 
     * Adapted from ModuleCommand.listModules()
     */
    private void listModules() throws IOException {
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

        // Build map of installed modules (name -> path)
        Map<String, Path> installed = new TreeMap<>();
        try (java.util.stream.Stream<Path> stream = Files.list(modulesDir)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                installed.put(dir.getFileName().toString(), dir);
            });
        }

        // Load repository index (bundled local.json)
        ModuleRepositoryIndex repoIndex = ModuleRepositoryIndex.loadDefault();
        Map<String, ModuleRepositoryIndex.RepoModule> repoModules = repoIndex.getModulesByName();

        if (installed.isEmpty() && repoModules.isEmpty()) {
            System.out.println("No modules found.");
            System.out.println("Use 'lucli modules init <module-name>' to create a new module.");
            return;
        }

        // Union of names from installed modules and repository modules
        Set<String> allNames = new TreeSet<>();
        allNames.addAll(installed.keySet());
        allNames.addAll(repoModules.keySet());

        StringOutput.getInstance().printf("%-20s %-10s %-10s %-50s%n", "NAME", "INSTALLED", "STATUS", "DESCRIPTION");
        System.out.println("─".repeat(100));

        for (String name : allNames) {
            boolean isInstalled = installed.containsKey(name);
            String installedMarker = isInstalled ? "${EMOJI_SUCCESS}" : "";

            String status;
            String description;

            Path moduleDir = installed.get(name);
            if (moduleDir != null) {
                status = getModuleStatus(moduleDir);
                description = getModuleDescription(moduleDir);
            } else {
                status = "AVAILABLE";
                ModuleRepositoryIndex.RepoModule repoMod = repoModules.get(name);
                if (repoMod != null && repoMod.getDescription() != null && !repoMod.getDescription().isEmpty()) {
                    description = repoMod.getDescription();
                } else {
                    description = "No description available";
                }
            }

            // If we have repo metadata and no local description, prefer repo description
            if (moduleDir != null) {
                ModuleRepositoryIndex.RepoModule repoMod = repoModules.get(name);
                if (repoMod != null && (description == null || description.equals("No description available"))) {
                    description = repoMod.getDescription();
                }
            }

            StringOutput.getInstance().printf("%-20s %-10s %-10s %-50s%n", name, installedMarker, status, description);
        }
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

    /**
     * Get module status (INSTALLED, DEV, etc.)
     */
    private String getModuleStatus(Path moduleDir) {
        // Check if it's a git working copy (development module)
        if (Files.isDirectory(moduleDir.resolve(".git"))) {
            return "DEV";
        }
        return "INSTALLED";
    }

    /**
     * Get module description from module.json
     */
    private String getModuleDescription(Path moduleDir) {
        Path moduleJsonPath = moduleDir.resolve("module.json");
        if (!Files.exists(moduleJsonPath)) {
            return "No description available";
        }

        try {
            String json = Files.readString(moduleJsonPath);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            
            com.fasterxml.jackson.databind.JsonNode descNode = root.get("description");
            if (descNode != null && !descNode.asText().trim().isEmpty()) {
                return descNode.asText().trim();
            }
        } catch (Exception e) {
            // Ignore parsing errors, return default
        }

        return "No description available";
    }
}
