package org.lucee.lucli.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.stream.Stream;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.StringOutput;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Handles module operations - listing, initializing, and managing CFML modules
 */
public class ModuleCommand {
    
/**
 * Execute the module command with the given arguments
 * 
 * @deprecated This method has been replaced by direct PicocLI implementations.
 *             Only used by UnifiedCommandExecutor/Terminal (old code path).
 */
@Deprecated
public static void executeModule(String[] args) {
    ModuleOptions options = parseArguments(args);
    
    if (options.showHelp) {
        showHelp();
        return;
    }
    
    try {
        switch (options.action) {
            case LIST:
                // LIST has been migrated to ModulesListCommandImpl
                System.err.println("ERROR: modules list should be handled by PicocLI ModulesListCommandImpl");
                System.err.println("This code path should not be reached anymore.");
                System.exit(1);
                break;
            case INIT:
                // INIT has been migrated to ModulesInitCommandImpl
                System.err.println("ERROR: modules init should be handled by PicocLI ModulesInitCommandImpl");
                System.err.println("This code path should not be reached anymore.");
                System.exit(1);
                break;
            case RUN:
                runModule(options.moduleName, options.moduleArgs);
                break;
            case INSTALL:
                installModule(options.moduleName, options.gitUrl, options.force);
                break;
            case UNINSTALL:
                uninstallModule(options.moduleName);
                break;
            case UPDATE:
                updateModule(options.moduleName, options.gitUrl, options.force);
                break;
            default:
                System.err.println("Unknown module action. Use --help for available commands.");
                System.exit(1);
        }
        
    } catch (Exception e) {
        System.err.println("Error executing module command: " + e.getMessage());
        
        if (LuCLI.verbose) {
            e.printStackTrace();
        }
        System.exit(1);
    }
}
    
    /**
     * List installed modules under ~/.lucli/modules and known modules from
     * the bundled repository index.
     * 
     * @deprecated This method has been migrated to ModulesListCommandImpl.
     *             This code path should not be reached anymore.
     */
    @Deprecated
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

        // Build map of installed modules (name -> description/status)
        java.util.Map<String, Path> installed = new java.util.TreeMap<>();
        try (java.util.stream.Stream<Path> stream = Files.list(modulesDir)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                installed.put(dir.getFileName().toString(), dir);
            });
        }

        // Load repository index (bundled local.json for now)
        ModuleRepositoryIndex repoIndex = ModuleRepositoryIndex.loadDefault();
        java.util.Map<String, ModuleRepositoryIndex.RepoModule> repoModules = repoIndex.getModulesByName();

        if (installed.isEmpty() && repoModules.isEmpty()) {
            System.out.println("No modules found.");
            System.out.println("Use 'lucli modules init <module-name>' to create a new module.");
            return;
        }

        // Union of names from installed modules and repository modules
        java.util.Set<String> allNames = new java.util.TreeSet<>();
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
     * Initialize a new module with the given name
     * 
     * @deprecated This method has been migrated to ModulesInitCommandImpl.
     *             This code path should not be reached anymore.
     */
    @Deprecated
    private static void initModule(String moduleName, boolean gitInit, boolean noGitInit) throws IOException {
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
        
        // Create module README.md
        createModuleReadme(moduleDir, moduleName);

        // Optionally initialize git for this module directory
        maybeInitializeGitRepository(moduleDir, gitInit, noGitInit);
        
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
     * Uses flags from CLI (gitInit / noGitInit) and falls back to an
     * interactive prompt when appropriate.
     */
    private static void maybeInitializeGitRepository(Path moduleDir, boolean gitInit, boolean noGitInit) {
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
            System.out.print("Initialize a git repository in this module directory? (Y/n): ");
            Scanner scanner = new Scanner(System.in);
            String answer = scanner.nextLine().trim();
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
     * Get the modules directory (~/.lucli/modules)
     */
    public static Path getModulesDirectory() throws IOException {
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
     * Get the LuCLI home directory (~/.lucli)
     */
    private static Path getLucliHomeDirectory() throws IOException {
        Path modulesDir = getModulesDirectory();
        Path lucliHome = modulesDir.getParent();
        if (lucliHome == null) {
            // Fallback to user.home if for some reason modulesDir has no parent
            lucliHome = Paths.get(System.getProperty("user.home"), ".lucli");
            if (!Files.exists(lucliHome)) {
                Files.createDirectories(lucliHome);
            }
        }
        return lucliHome;
    }

    /**
     * Get the settings.json file in LuCLI home (~/.lucli/settings.json)
     */
    private static Path getSettingsFile() throws IOException {
        Path lucliHome = getLucliHomeDirectory();
        return lucliHome.resolve("settings.json");
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

        // Load template from resources
        String templatePath = "/modules/template/Module.cfc";
        String moduleTemplate;
        try {
            moduleTemplate = new String(
                ModuleCommand.class.getResourceAsStream(templatePath).readAllBytes()
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
    private static void createModuleMetadata(Path moduleDir, String moduleName) throws IOException {
        // Load template from resources
        String templatePath = "/modules/template/module.json";
        String metadataTemplate;
        try {
            metadataTemplate = new String(
                ModuleCommand.class.getResourceAsStream(templatePath).readAllBytes()
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
    private static void createModuleReadme(Path moduleDir, String moduleName) throws IOException {

        // Load template from resources
        String templatePath = "/modules/template/README.md";
        String readmeTemplate;
        try {
            readmeTemplate = new String(
                ModuleCommand.class.getResourceAsStream(templatePath).readAllBytes()
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

                case "install":
                    options.action = ModuleAction.INSTALL;
                    // Next argument may be the module name
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        options.moduleName = args[i + 1];
                        i++;
                    }
                    break;

                case "uninstall":
                    options.action = ModuleAction.UNINSTALL;
                    // Next argument should be the module name
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        options.moduleName = args[i + 1];
                        i++;
                    }
                    break;

                case "update":
                    options.action = ModuleAction.UPDATE;
                    // Next argument should be the module name
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        options.moduleName = args[i + 1];
                        i++;
                    }
                    break;
                
                case "--url":
                case "-u":
                    if (i + 1 < args.length) {
                        options.gitUrl = args[i + 1];
                        i++;
                    }
                    break;

            case "--force":
                    options.force = true;
                    break;

            case "--git":
                    options.gitInit = true;
                    break;

            case "--no-git":
                    options.noGitInit = true;
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
        System.out.println(StringOutput.loadText("/text/modules-help.txt"));
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
            LuceeScriptEngine engine = LuceeScriptEngine.getInstance(LuCLI.verbose, LuCLI.debug);
            engine.executeModule(moduleName, moduleArgs != null ? moduleArgs : new String[0]);
        } else {
            System.err.println("Module '" + moduleName + "' does not have a Module.cfc file.");
            System.exit(1);
        }
    }
    
/**
     * Install a module from a URL into ~/.lucli/modules.
     *
     * Supported forms (precedence):
     *  1) Direct archive URL (zip/tar.gz)
     *  2) Git URL via `git clone` when git is available
     *  3) GitHub HTTPS URL fallback to archive download when git is not available
     */
public static void installModule(String moduleName, String gitUrl, boolean force) throws Exception {




        // Phase 1: require explicit URL
        if (gitUrl == null || gitUrl.trim().isEmpty()) {


            ModuleRepositoryIndex repoIndex = ModuleRepositoryIndex.loadDefault();
            ModuleRepositoryIndex.RepoModule repoMod =
                repoIndex.getModulesByName().get(moduleName);

            if (repoMod != null && repoMod.getUrl() != null && !repoMod.getUrl().isBlank()) {
                gitUrl = repoMod.getUrl().trim();
            } else {
                System.err.println("modules install currently requires --url=<url> or a known repository entry for '" + moduleName + "'.");
                System.exit(1);
            }
        }

        // Support #ref syntax in URL (e.g. https://github.com/user/repo.git#v1.2.0)
        String baseUrl = gitUrl;
        String ref = null;
        int hashIndex = gitUrl.indexOf('#');
        if (hashIndex >= 0) {
            baseUrl = gitUrl.substring(0, hashIndex);
            if (hashIndex + 1 < gitUrl.length()) {
                ref = gitUrl.substring(hashIndex + 1);
            }
        }

        // Create temp working directory used by all strategies
        Path tempDir = Files.createTempDirectory("lucli-module-install-");

        try {
            // Resolve the source directory (root of the unpacked/checked-out module)
            Path sourceDir;

            if (isArchiveUrl(baseUrl)) {
                // 1) Direct archive URL (zip/tar.gz) – no git required
                sourceDir = installFromArchiveUrl(tempDir, baseUrl);
            } else if (isGitAvailable()) {
                // 2) Git available – use existing clone semantics
                sourceDir = cloneWithGit(tempDir, baseUrl, ref);
            } else if (isGithubHttpsUrl(baseUrl)) {
                // 3) Git not available, but GitHub HTTPS URL – fall back to archive
                sourceDir = installFromGithubArchive(tempDir, baseUrl, ref);
            } else {
                System.err.println("git is not installed and the provided URL is not a supported archive or GitHub HTTPS URL.");
                System.exit(1);
                return; // keep compiler happy
            }

            // Validate module structure (module.json + Module.cfc at repo root)
            Path moduleJson = sourceDir.resolve("module.json");
            Path moduleCfc = sourceDir.resolve("Module.cfc");

            if (!Files.exists(moduleJson) || !Files.exists(moduleCfc)) {
                System.err.println("Downloaded repository does not appear to be a valid LuCLI module.");
                System.err.println("Expected module.json and Module.cfc in the repository root.");
                System.exit(1);
            }

            // Read module.json name (used for validation and as fallback name)
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(Files.readString(moduleJson));
            JsonNode nameNode = root.get("name");
            if (nameNode == null || nameNode.asText().trim().isEmpty()) {
                System.err.println("module.json is missing a 'name' field.");
                System.exit(1);
            }

            String metadataName = nameNode.asText().trim();

            // If no module name was provided, derive it from module.json.name
            if (moduleName == null || moduleName.trim().isEmpty()) {
                moduleName = metadataName;
            } else if (!metadataName.equals(moduleName)) {
                System.err.println("Module name mismatch: requested '" + moduleName + "' but module.json.name is '" + metadataName + "'.");
                System.exit(1);
            }

            if (!isValidModuleName(moduleName)) {
                System.err.println("Invalid module name '" + moduleName + "'. Module names should contain only letters, numbers, hyphens, and underscores.");
                System.exit(1);
            }

            Path modulesDir = getModulesDirectory();
            Path targetDir = modulesDir.resolve(moduleName);

            // Protect development modules (those with a .git directory) from being overwritten
            if (Files.exists(targetDir) && Files.isDirectory(targetDir.resolve(".git"))) {
                System.err.println("Module '" + moduleName + "' appears to be a local Git working copy at: " + targetDir);
                System.err.println("Refusing to overwrite a development module. Update it via git or remove the directory explicitly before reinstalling.");
                System.exit(1);
            }

            // Handle existing module directory depending on --force
            if (Files.exists(targetDir)) {
                if (force) {
                    // Delete existing directory before reinstalling
                    try (Stream<Path> walk = Files.walk(targetDir)) {
                        walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                    }
                } else {
                    System.err.println("Module '" + moduleName + "' already exists at: " + targetDir);
                    System.err.println("Use --force to overwrite the existing module.");
                    System.exit(1);
                }
            }

            // Copy source contents into ~/.lucli/modules/<name>, excluding .git
            Files.createDirectories(targetDir);
            try (Stream<Path> paths = Files.walk(sourceDir)) {
                paths.forEach(source -> {
                    try {
                        Path relative = sourceDir.relativize(source);
                        if (relative.toString().startsWith(".git")) {
                            return; // skip git metadata
                        }
                        if (relative.toString().isEmpty()) {
                            return; // skip root itself
                        }
                        Path dest = targetDir.resolve(relative);
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(dest);
                        } else {
                            Files.copy(source, dest);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to copy module files: " + e.getMessage(), e);
                    }
                });
            }

            // Record provenance in settings.json so we can support updates without explicit URLs
            try {
                persistModuleSource(moduleName, baseUrl, ref);
            } catch (IOException e) {
                // Do not fail the install if we can't write settings; just warn in verbose mode
                if (LuCLI.verbose || LuCLI.debug) {
                    System.err.println("Warning: Failed to update settings.json for module '" + moduleName + "': " + e.getMessage());
                }
            }

            StringOutput.Quick.success("Successfully installed module: " + moduleName);
            StringOutput.getInstance().println("${EMOJI_FOLDER} Module directory: " + targetDir);

        } finally {
            // Cleanup temp directory
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
            } catch (IOException ignored) {
            }
        }
    }
    
    private static void runGitCommand(Path workingDir, String[] command) throws IOException, InterruptedException {
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
     * Return true if git is available on PATH.
     */
    private static boolean isGitAvailable() {
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
     * Return true if URL looks like an archive we can download directly.
     */
    private static boolean isArchiveUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
    }
    
    /**
     * Return true if URL is a GitHub HTTPS URL.
     */
    private static boolean isGithubHttpsUrl(String url) {
        return url.startsWith("https://github.com/");
    }
    
    /**
     * Clone a repository into a temporary directory using git and return the repo root.
     */
    private static Path cloneWithGit(Path tempDir, String baseUrl, String ref) throws Exception {
        Path cloneDir = tempDir.resolve("repo");
        runGitCommand(null, new String[]{"git", "clone", "--depth", "1", baseUrl, cloneDir.toString()});

        // Checkout specific ref if provided
        if (ref != null && !ref.isEmpty()) {
            runGitCommand(cloneDir, new String[]{"git", "checkout", ref});
        }

        return cloneDir;
    }
    
    /**
     * Install from a direct archive URL (zip/tar.gz) and return the repo root directory.
     * Currently supports .zip; .tar.gz/.tgz are reserved for future use.
     */
    private static Path installFromArchiveUrl(Path tempDir, String archiveUrl) throws IOException {
        Path downloadDir = tempDir.resolve("download");
        Files.createDirectories(downloadDir);

        if (archiveUrl.toLowerCase().endsWith(".zip")) {
            Path zipFile = downloadDir.resolve("module.zip");
            try (java.io.InputStream in = new java.net.URL(archiveUrl).openStream()) {
                Files.copy(in, zipFile);
            }
            Path extractDir = tempDir.resolve("extract");
            Files.createDirectories(extractDir);
            unzip(zipFile, extractDir);

            // Most archives should either contain files directly or a single top-level dir
            try (Stream<Path> children = Files.list(extractDir)) {
                return children
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(extractDir);
            }
        }

        // Other archive types (tar.gz) can be added later
        throw new IOException("Unsupported archive URL: " + archiveUrl + " (only .zip is currently supported)");
    }
    
    /**
     * Install from a GitHub HTTPS URL by downloading a zip archive instead of using git.
     */
    private static Path installFromGithubArchive(Path tempDir, String baseUrl, String ref) throws IOException {
        String repo = baseUrl;
        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }

        String archiveUrl;
        if (ref != null && !ref.isEmpty()) {
            // Prefer tag archives; callers should pass branch/tag appropriately
            archiveUrl = repo + "/archive/refs/tags/" + ref + ".zip";
        } else {
            // Default to main; in the future we could try main then master
            archiveUrl = repo + "/archive/refs/heads/main.zip";
        }

        return installFromArchiveUrl(tempDir, archiveUrl);
    }
    
    /**
     * Simple zip extraction helper used by archive-based installs.
     */
    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (java.util.zip.ZipInputStream zis =
                 new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = targetDir.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(targetDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath);
                }
                zis.closeEntry();
            }
        }
    }
    
    /**
     * Uninstall (remove) a module by deleting its directory under ~/.lucli/modules
     */
    private static void uninstallModule(String moduleName) throws IOException {
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
     * Update a module by reinstalling it from the given git URL.
     * If no URL is provided, attempts to use the last stored repository/ref from settings.json.
     */
    public static void updateModule(String moduleName, String gitUrl, boolean force) throws Exception {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            System.err.println("Module name is required for update command.");
            System.exit(1);
        }

        // Protect development modules (those with a .git directory) from being overwritten
        Path modulesDir = getModulesDirectory();
        Path moduleDir = modulesDir.resolve(moduleName);
        if (Files.isDirectory(moduleDir.resolve(".git"))) {
            System.err.println("Module '" + moduleName + "' is a Git working copy at: " + moduleDir);
            System.err.println("Please update it using git (e.g. git pull) instead of 'lucli modules update'.");
            System.exit(1);
        }

        String effectiveUrl = gitUrl;

        if (effectiveUrl == null || effectiveUrl.trim().isEmpty()) {
            ModuleSource source = loadModuleSource(moduleName);
            if (source == null || source.repository == null || source.repository.trim().isEmpty()) {
                System.err.println("No stored repository URL for module '" + moduleName + "'.");
                System.err.println("Use: lucli modules update " + moduleName + " --url=<git-url>");
                System.exit(1);
            }

            effectiveUrl = source.repository;
            if (source.ref != null && !source.ref.trim().isEmpty()) {
                effectiveUrl = effectiveUrl + "#" + source.ref.trim();
            }
        }

        // Ensure force is enabled for updates regardless of user flag
        installModule(moduleName, effectiveUrl, true);
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
    LIST, INIT, RUN, INSTALL, UNINSTALL, UPDATE
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
    String gitUrl = null;
    boolean force = false;
    boolean gitInit = false;
    boolean noGitInit = false;
}

/**
 * Simple representation of where a module was installed from.
 */
private static class ModuleSource {
    final String repository;
    final String ref;

    ModuleSource(String repository, String ref) {
        this.repository = repository;
        this.ref = ref;
    }
}

/**
 * Persist module source info to settings.json under modules.<name>.
 */
private static void persistModuleSource(String moduleName, String repository, String ref) throws IOException {
    Path settingsFile = getSettingsFile();
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root;

    if (Files.exists(settingsFile)) {
        String content = Files.readString(settingsFile);
        if (content == null || content.trim().isEmpty()) {
            root = mapper.createObjectNode();
        } else {
            JsonNode existing = mapper.readTree(content);
            if (existing != null && existing.isObject()) {
                root = (ObjectNode) existing;
            } else {
                root = mapper.createObjectNode();
            }
        }
    } else {
        root = mapper.createObjectNode();
    }

    ObjectNode modulesNode;
    JsonNode modulesExisting = root.get("modules");
    if (modulesExisting != null && modulesExisting.isObject()) {
        modulesNode = (ObjectNode) modulesExisting;
    } else {
        modulesNode = mapper.createObjectNode();
        root.set("modules", modulesNode);
    }

    ObjectNode modNode = mapper.createObjectNode();
    modNode.put("repository", repository);
    if (ref != null && !ref.trim().isEmpty()) {
        modNode.put("ref", ref.trim());
    }
    modNode.put("installedAt", java.time.Instant.now().toString());

    modulesNode.set(moduleName, modNode);

    // Pretty-print for readability
    mapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile.toFile(), root);
}

/**
 * Load module source info for a given module name from settings.json.
 */
private static ModuleSource loadModuleSource(String moduleName) throws IOException {
    Path settingsFile = getSettingsFile();
    if (!Files.exists(settingsFile)) {
        return null;
    }

    ObjectMapper mapper = new ObjectMapper();
    String content = Files.readString(settingsFile);
    if (content == null || content.trim().isEmpty()) {
        return null;
    }

    JsonNode root = mapper.readTree(content);
    if (root == null || !root.isObject()) {
        return null;
    }

    JsonNode modulesNode = root.get("modules");
    if (modulesNode == null || !modulesNode.isObject()) {
        return null;
    }

    JsonNode modNode = modulesNode.get(moduleName);
    if (modNode == null || !modNode.isObject()) {
        return null;
    }

    String repo = null;
    String ref = null;

    JsonNode repoNode = modNode.get("repository");
    if (repoNode != null && repoNode.isTextual()) {
        repo = repoNode.asText();
    }

    JsonNode refNode = modNode.get("ref");
    if (refNode != null && refNode.isTextual()) {
        ref = refNode.asText();
    }

    if (repo == null || repo.trim().isEmpty()) {
        return null;
    }

    return new ModuleSource(repo, ref);
}
}
