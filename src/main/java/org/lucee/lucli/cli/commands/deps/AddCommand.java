package org.lucee.lucli.cli.commands.deps;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.config.DependencyConfig;
import org.lucee.lucli.config.LuceeJsonEditor;
import org.lucee.lucli.deps.ExtensionRegistry;
import org.lucee.lucli.modules.ModuleRepositoryIndex;

import picocli.CommandLine.Command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Interactive wizard to add dependencies to lucee.json
 */
@Command(
    name = "add",
    description = "Add a dependency to lucee.json via an interactive wizard"
)
public class AddCommand implements Callable<Integer> {

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

    @Override
    public Integer call() throws Exception {
        try {
            StringOutput.Quick.info("📦 LuCLI Dependency Wizard");

            // 1. Scope: dependencies vs devDependencies
            boolean dev = askScope();

            // 2. What kind of dependency
            int kind = askKind();

            DependencyConfig dep;
            switch (kind) {
                case 1:
                    dep = runCfmlGitWizard();
                    break;
                case 2:
                    dep = runExtensionWizard();
                    break;
                case 3:
                    dep = runJavaMavenWizard();
                    break;
                case 4:
                    dep = runJarOrFileWizard();
                    break;
                default:
                    StringOutput.Quick.error("Unknown option");
                    return 1;
            }

            if (dep == null) {
                StringOutput.Quick.info("Cancelled, no changes made.");
                return 0;
            }

            // 3. Confirmation and write to lucee.json
            confirmAndSave(dep, dev);

            return 0;
        } catch (IOException e) {
            StringOutput.Quick.error("❌ I/O error: " + e.getMessage());
            return 1;
        }
    }

    private boolean askScope() throws IOException {
        while (true) {
            System.out.println();
            System.out.println("Select dependency scope:");
            System.out.println("  1) dependencies (prod)");
            System.out.println("  2) devDependencies");
            System.out.print("> ");
            String line = in.readLine();
            if (line == null) return false;
            line = line.trim();
            if (line.isEmpty() || "1".equals(line)) {
                return false; // prod
            }
            if ("2".equals(line)) {
                return true; // dev
            }
            System.out.println("Please enter 1 or 2.");
        }
    }

    private int askKind() throws IOException {
        while (true) {
            System.out.println();
            System.out.println("What do you want to add?");
            System.out.println("  1) CFML module / library (git)");
            System.out.println("  2) Lucee extension (.lex / ID / slug)");
            System.out.println("  3) Java/Maven library");
            System.out.println("  4) Raw JAR / file");
            System.out.print("> ");
            String line = in.readLine();
            if (line == null) return 0;
            line = line.trim();
            if (line.matches("[1-4]")) {
                return Integer.parseInt(line);
            }
            System.out.println("Please enter 1, 2, 3, or 4.");
        }
    }

    private DependencyConfig runCfmlGitWizard() throws IOException {
        System.out.println();
        System.out.println("CFML Git dependency");
        System.out.println("--------------------");

        // Try to use the module repository index as a curated registry first
        ModuleRepositoryIndex repoIndex = ModuleRepositoryIndex.loadDefault();
        Map<String, ModuleRepositoryIndex.RepoModule> repoModules = repoIndex.getModulesByName();

        ModuleRepositoryIndex.RepoModule selectedModule = null;
        if (!repoModules.isEmpty()) {
            selectedModule = chooseModuleFromRegistry(repoModules);
        }

        String name;
        String url;

        if (selectedModule != null) {
            System.out.println();
            System.out.println("Using curated module: " + selectedModule.getName());
            System.out.println("  " + selectedModule.getDescription());
            System.out.println("  " + selectedModule.getUrl());

            name = askWithDefault("Dependency name (key in dependencies)", selectedModule.getName());
            if (name == null) return null;

            url = askWithDefault("Git URL", selectedModule.getUrl());
            if (url == null) return null;
        } else {
            name = askRequired("Dependency name (e.g. fw1, my-lib)");
            if (name == null) return null;

            url = askRequired("Git URL (e.g. https://github.com/user/repo.git)");
            if (url == null) return null;
        }

        String ref = askWithDefault("Git ref (branch, tag, or commit)", "main");
        if (ref == null) return null;

        String subPath = askOptional("Subdirectory within repo to install (optional)");

        DependencyConfig dep = new DependencyConfig(name);
        dep.setType("cfml");
        dep.setSource("git");
        dep.setUrl(url);
        dep.setRef(ref);
        if (subPath != null && !subPath.isBlank()) {
            dep.setSubPath(subPath.trim());
        }

        // Let applyDefaults derive installPath and mapping
        dep.applyDefaults();

        // Allow override
        System.out.println();
        System.out.println("Defaults:");
        System.out.println("  installPath: " + dep.getInstallPath());
        System.out.println("  mapping    : " + dep.getMapping());

        String customInstallPath = askWithDefault("Install path", dep.getInstallPath());
        if (customInstallPath == null) return null;
        dep.setInstallPath(customInstallPath.trim());

        String customMapping = askWithDefault("Mapping", dep.getMapping());
        if (customMapping == null) return null;
        dep.setMapping(customMapping.trim());

        return dep;
    }

    private DependencyConfig runExtensionWizard() throws IOException {
        System.out.println();
        System.out.println("Lucee extension dependency");
        System.out.println("-------------------------");
        System.out.println("You can specify an extension by:");
        System.out.println("  • slug/name/alias from Lucee extension registry (e.g. redis, h2)");
        System.out.println("  • full extension ID (UUID)");
        System.out.println("  • URL to a .lex file");
        System.out.println("  • local path to a .lex file");
        System.out.println("Type '?' to list known extensions.");

        String name = askRequired("Dependency name (key in dependencies, e.g. redis, h2) ");
        if (name == null) return null;

        while (true) {
            String identifier = askRequired("Extension identifier (slug/name/alias/UUID or .lex URL/path)");
            if (identifier == null) return null;
            if ("?".equals(identifier.trim())) {
                printExtensionList();
                continue;
            }

            DependencyConfig dep = new DependencyConfig(name);
            dep.setType("extension");

            String trimmed = identifier.trim();
            if (trimmed.endsWith(".lex") && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
                // URL-based extension
                dep.setUrl(trimmed);
            } else if (trimmed.endsWith(".lex") || trimmed.startsWith("/") || trimmed.startsWith("./") || trimmed.startsWith("../")) {
                // Local path
                dep.setPath(trimmed);
            } else {
                // Assume slug/name/UUID; store as id (resolved later by DependencyConfig.getId())
                dep.setId(trimmed);
            }

            // Optional version metadata
            String version = askOptional("Version string (optional, for documentation)");
            if (version != null && !version.isBlank()) {
                dep.setVersion(version.trim());
            }

            dep.applyDefaults();
            return dep;
        }
    }

    private DependencyConfig runJavaMavenWizard() throws IOException {
        System.out.println();
        System.out.println("Java/Maven dependency");
        System.out.println("---------------------");

        String name = askRequired("Dependency name (key in dependencies, e.g. slf4j-api)");
        if (name == null) return null;

        String groupId = askRequired("groupId (e.g. org.slf4j)");
        if (groupId == null) return null;

        String artifactId = askRequired("artifactId (e.g. slf4j-api)");
        if (artifactId == null) return null;

        String version = askRequired("version (e.g. 2.0.13)");
        if (version == null) return null;

        String repository = askWithDefault("Repository URL", "https://repo1.maven.org/maven2/");
        if (repository == null) return null;

        DependencyConfig dep = new DependencyConfig(name);
        dep.setType("java");
        dep.setSource("maven");
        dep.setGroupId(groupId.trim());
        dep.setArtifactId(artifactId.trim());
        dep.setVersion(version.trim());
        dep.setRepository(repository.trim());

        dep.applyDefaults();

        String installPath = askWithDefault("Install path", dep.getInstallPath());
        if (installPath == null) return null;
        dep.setInstallPath(installPath.trim());

        return dep;
    }

    private DependencyConfig runJarOrFileWizard() throws IOException {
        System.out.println();
        System.out.println("Raw JAR / file dependency");
        System.out.println("-------------------------");

        String name = askRequired("Dependency name (key in dependencies)");
        if (name == null) return null;

        String pathOrUrl = askRequired("Local path or URL to the JAR/file");
        if (pathOrUrl == null) return null;

        DependencyConfig dep = new DependencyConfig(name);

        String trimmed = pathOrUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            dep.setSource("http");
            dep.setUrl(trimmed);
        } else {
            dep.setSource("file");
            dep.setPath(trimmed);
        }

        if (trimmed.endsWith(".jar")) {
            dep.setType("jar");
        } else {
            dep.setType("cfml");
        }

        dep.applyDefaults();

        String installPath = askWithDefault("Install path", dep.getInstallPath());
        if (installPath == null) return null;
        dep.setInstallPath(installPath.trim());

        return dep;
    }

    private void confirmAndSave(DependencyConfig dep, boolean dev) throws IOException {
        System.out.println();
        System.out.println("About to add to lucee.json:\n");

        // Build a JSON-like preview map
        Map<String, Object> preview = new LinkedHashMap<>();
        if (dep.getType() != null) preview.put("type", dep.getType());
        if (dep.getVersion() != null) preview.put("version", dep.getVersion());
        if (dep.getSource() != null) preview.put("source", dep.getSource());
        if (dep.getUrl() != null) preview.put("url", dep.getUrl());
        if (dep.getRef() != null) preview.put("ref", dep.getRef());
        if (dep.getSubPath() != null) preview.put("subPath", dep.getSubPath());
        if (dep.getInstallPath() != null) preview.put("installPath", dep.getInstallPath());
        if (dep.getMapping() != null) preview.put("mapping", dep.getMapping());
        if (dep.getPath() != null) preview.put("path", dep.getPath());
        if (dep.getGroupId() != null) preview.put("groupId", dep.getGroupId());
        if (dep.getArtifactId() != null) preview.put("artifactId", dep.getArtifactId());
        if (dep.getRepository() != null) preview.put("repository", dep.getRepository());
        if (dep.getRawId() != null) preview.put("id", dep.getRawId());

        String indent = "  ";
        System.out.println("\"" + dep.getName() + "\": {");
        boolean first = true;
        for (Map.Entry<String, Object> e : preview.entrySet()) {
            String comma = first ? "" : ",";
            first = false;
            System.out.println(indent + "\"" + e.getKey() + "\": \"" + e.getValue() + "\"" + comma);
        }
        System.out.println("}");
        System.out.println();

        String confirm = askWithDefault("Save changes to lucee.json? [Y/n]", "Y");
        if (confirm == null) {
            StringOutput.Quick.info("Cancelled, no changes made.");
            return;
        }
        String v = confirm.trim().toLowerCase();
        if (!v.isEmpty() && !(v.equals("y") || v.equals("yes"))) {
            StringOutput.Quick.info("Cancelled, no changes made.");
            return;
        }

        try {
            LuceeJsonEditor editor = new LuceeJsonEditor(Paths.get("."));
            editor.addOrUpdateDependency(dep.getName(), dep, dev);
            editor.save();
            String scope = dev ? "devDependencies" : "dependencies";
            StringOutput.Quick.success("✅ Added '" + dep.getName() + "' to " + scope + " in lucee.json");
        } catch (IOException e) {
            StringOutput.Quick.error("❌ Failed to update lucee.json: " + e.getMessage());
        }
    }

    private ModuleRepositoryIndex.RepoModule chooseModuleFromRegistry(Map<String, ModuleRepositoryIndex.RepoModule> repoModules) throws IOException {
        System.out.println();
        System.out.println("Known CFML modules (from module registry):");

        java.util.List<ModuleRepositoryIndex.RepoModule> list = new java.util.ArrayList<>(repoModules.values());
        for (int i = 0; i < list.size(); i++) {
            ModuleRepositoryIndex.RepoModule m = list.get(i);
            String desc = m.getDescription() != null ? m.getDescription() : "";
            System.out.printf("  %d) %s - %s\n", i + 1, m.getName(), desc);
            if (m.getUrl() != null && !m.getUrl().isBlank()) {
                System.out.println("     " + m.getUrl());
            }
        }
        System.out.println("  0) Custom git dependency");

        while (true) {
            System.out.print("Select module [0 for custom]: ");
            String line = in.readLine();
            if (line == null) {
                return null;
            }
            line = line.trim();
            if (line.isEmpty() || "0".equals(line)) {
                return null; // custom path
            }
            try {
                int idx = Integer.parseInt(line);
                if (idx >= 1 && idx <= list.size()) {
                    return list.get(idx - 1);
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Please enter a number between 0 and " + list.size() + ".");
        }
    }

    private void printExtensionList() {
        System.out.println();
        System.out.println("Known Lucee extensions (slug → name):");
        Map<String, ExtensionRegistry.ExtensionInfo> all = ExtensionRegistry.listAll();
        // De-duplicate by ID, prefer slug-like keys
        Map<String, String> byId = new LinkedHashMap<>();
        for (Map.Entry<String, ExtensionRegistry.ExtensionInfo> e : all.entrySet()) {
            String key = e.getKey();
            ExtensionRegistry.ExtensionInfo info = e.getValue();
            if (!byId.containsKey(info.id)) {
                byId.put(info.id, key + " → " + info.name);
            }
        }
        for (String value : byId.values()) {
            System.out.println("  - " + value);
        }
        System.out.println();
    }

    // === Prompt helpers ===

    private String askRequired(String label) throws IOException {
        while (true) {
            System.out.print(label + ": ");
            String line = in.readLine();
            if (line == null) return null;
            line = line.trim();
            if (!line.isEmpty()) {
                return line;
            }
            System.out.println("This field is required.");
        }
    }

    private String askWithDefault(String label, String defaultValue) throws IOException {
        String prompt = defaultValue != null ? String.format("%s [%s]: ", label, defaultValue) : (label + ": ");
        System.out.print(prompt);
        String line = in.readLine();
        if (line == null) return null;
        line = line.trim();
        if (line.isEmpty()) {
            return defaultValue;
        }
        return line;
    }

    private String askOptional(String label) throws IOException {
        System.out.print(label + " (leave blank to skip): ");
        String line = in.readLine();
        if (line == null) return null;
        line = line.trim();
        return line.isEmpty() ? null : line;
    }
}
