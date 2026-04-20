package org.lucee.lucli.cli.commands.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.paths.LucliPaths;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Direct implementation of modules init command - creates a new module from template
 */
@Command(
    name = "init",
    description = "Initialize a new module"
)
public class ModulesInitCommandImpl implements Callable<Integer> {

    @Parameters(
        paramLabel = "MODULE_NAME",
        description = "Name of the module to initialize",
        arity = "0..1"
    )
    private String moduleName;

    @Option(
        names = "--git",
        description = "Initialize a git repository in the new module directory"
    )
    private boolean gitInit;

    @Option(
        names = "--no-git",
        description = "Do not initialize git and do not prompt"
    )
    private boolean noGitInit;

    @Override
    public Integer call() throws Exception {
        initModule();
        return 0;
    }

    /**
     * Initialize a new module with the given name
     */
    private void initModule() throws IOException {
        // Handle interactive mode if module name not provided
        if (moduleName == null || moduleName.trim().isEmpty()) {
            moduleName = readLineFromConsole("Enter module name: ");
            if (moduleName == null) {
                System.err.println("Module name is required when running non-interactively.");
                System.err.println("Usage: lucli module init <MODULE_NAME>");
                System.exit(1);
            }
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
        
        // Create module README.md
        createModuleReadme(moduleDir, moduleName);

        // Optionally initialize git for this module directory
        maybeInitializeGitRepository(moduleDir);
        
        StringOutput.Quick.success("Successfully created module: " + moduleName);
        StringOutput.getInstance().println("${EMOJI_FOLDER} Module directory: " + moduleDir);
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Edit " + moduleDir.resolve("Module.cfc") + " to implement your module");
        System.out.println("  2. Update " + moduleDir.resolve("module.json") + " with module metadata");
        System.out.println("  3. Test your module with: lucli " + moduleDir.resolve("Module.cfc"));
       
    }

    /**
     * Optionally initialize a git repository in the new module directory.
     */
    private void maybeInitializeGitRepository(Path moduleDir) {
        // Explicit opt-out wins
        if (noGitInit) {
            return;
        }

        // Require git to be available
        if (!isGitAvailable()) {
            return;
        }

        boolean interactive = System.console() != null;
        boolean shouldInit = gitInit;

        if (!gitInit && interactive) {
            String answer = readLineFromConsole("Initialize a git repository in this module directory? (Y/n): ");
            if (answer == null) {
                answer = "";
            }
            shouldInit = answer.isEmpty() || answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
        }

        if (!shouldInit) {
            return;
        }

        try {
            runGitCommand(moduleDir, new String[] { "git", "init" });
        } catch (Exception e) {
            if (LuCLI.verbose || LuCLI.debug) {
                System.err.println("Warning: Failed to initialize git in module directory '" + moduleDir + "': " + e.getMessage());
            }
        }
    }

    /**
     * Get the active profile's modules directory, creating it if needed.
     *
     * <p>Delegates to {@link LucliPaths#resolve()} so the path tracks the active
     * CLI profile (e.g., {@code ~/.lucli/modules} or {@code ~/.wheels/modules}).</p>
     */
    private Path getModulesDirectory() throws IOException {
        Path modulesDir = LucliPaths.resolve().modulesDir();
        Files.createDirectories(modulesDir);
        return modulesDir;
    }

    /**
     * Validate module name
     */
    private boolean isValidModuleName(String name) {
        return name != null && name.matches("^[a-zA-Z0-9_-]+$") && !name.isEmpty();
    }

    /**
     * Create the main Module.cfc file
     */
    private void createModuleFile(Path moduleDir, String moduleName) throws IOException {
        // Load template from resources
        String templatePath = "/modules/template/Module.cfc";
        String moduleTemplate;
        try {
            moduleTemplate = new String(
                getClass().getResourceAsStream(templatePath).readAllBytes()
            );
        } catch (Exception e) {
            throw new IOException("Failed to load module template from " + templatePath, e);
        }

        // Replace placeholders in template
        moduleTemplate = moduleTemplate.replace("{{MODULE_NAME}}", moduleName);
        moduleTemplate = moduleTemplate.replace("{{MODULE_NAME_UPPER}}", moduleName.toUpperCase());
        moduleTemplate = moduleTemplate.replace("{{MODULE_NAME_LOWER}}", moduleName.toLowerCase());

        Files.writeString(moduleDir.resolve("Module.cfc"), moduleTemplate);
    }

    /**
     * Create module metadata file
     */
    private void createModuleMetadata(Path moduleDir, String moduleName) throws IOException {
        // Load template from resources
        String templatePath = "/modules/template/module.json";
        String metadataTemplate;
        try {
            metadataTemplate = new String(
                getClass().getResourceAsStream(templatePath).readAllBytes()
            );
        } catch (Exception e) {
            throw new IOException("Failed to load module metadata template from " + templatePath, e);
        }

        // Replace placeholders in template
        metadataTemplate = metadataTemplate.replace("{{MODULE_NAME}}", moduleName);
        metadataTemplate = metadataTemplate.replace("{{MODULE_NAME_UPPER}}", moduleName.toUpperCase());
        metadataTemplate = metadataTemplate.replace("{{MODULE_NAME_LOWER}}", moduleName.toLowerCase());
        metadataTemplate = metadataTemplate.replace("{{TIMESTAMP}}", java.time.Instant.now().toString());

        Files.writeString(moduleDir.resolve("module.json"), metadataTemplate);
    }

    /**
     * Create module README.md file
     */
    private void createModuleReadme(Path moduleDir, String moduleName) throws IOException {
        // Load template from resources
        String templatePath = "/modules/template/README.md";
        String readmeTemplate;
        try {
            readmeTemplate = new String(
                getClass().getResourceAsStream(templatePath).readAllBytes()
            );
        } catch (Exception e) {
            throw new IOException("Failed to load README template from " + templatePath, e);
        }

        // Replace placeholders in template
        readmeTemplate = readmeTemplate.replace("{{MODULE_NAME}}", moduleName);
        readmeTemplate = readmeTemplate.replace("{{MODULE_NAME_UPPER}}", moduleName.toUpperCase());
        readmeTemplate = readmeTemplate.replace("{{MODULE_NAME_LOWER}}", moduleName.toLowerCase());

        Files.writeString(moduleDir.resolve("README.md"), readmeTemplate);
    }

    /**
     * Run a git command
     */
    private void runGitCommand(Path workingDir, String[] command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (LuCLI.verbose) {
                    System.out.println(line);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("git command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }

    /**
     * Return true if git is available on PATH
     */
    private boolean isGitAvailable() {
        try {
            Process p = new ProcessBuilder("git", "--version")
                .redirectErrorStream(true)
                .start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Read a line from the system console. Returns null when no console is available
     * or when end-of-input is encountered.
     */
    private String readLineFromConsole(String prompt) {
        java.io.Console console = System.console();
        if (console == null) {
            return null;
        }
        String value = console.readLine(prompt);
        return value == null ? null : value.trim();
    }
}
