package org.lucee.lucli.modules;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lightweight loader for LuCLI module repositories.
 *
 * For this initial iteration, it only loads the bundled local repository
 * from src/main/resources/repository/local.json. External repositories
 * can be added later reusing the same data model.
 */
public class ModuleRepositoryIndex {

    public static class RepoModule {
        private final String name;
        private final String description;
        private final String url;
        private final String repository;

        public RepoModule(String name, String description, String url, String repository) {
            this.name = name;
            this.description = description;
            this.url = url;
            this.repository = repository;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getUrl() {
            return url;
        }

        public String getRepository() {
            return repository;
        }
    }

    private final Map<String, RepoModule> modulesByName;

    private ModuleRepositoryIndex(Map<String, RepoModule> modulesByName) {
        this.modulesByName = modulesByName;
    }

    /**
     * Load the default repository index, currently consisting only of
     * the bundled local repository JSON. If the file is missing or
     * cannot be parsed, this method returns an empty index.
     */
    public static ModuleRepositoryIndex loadDefault() {
        Map<String, RepoModule> byName = new LinkedHashMap<>();

        ObjectMapper mapper = new ObjectMapper();

        // 1) Load bundled local repository from classpath
        try (InputStream is = ModuleRepositoryIndex.class.getResourceAsStream("/repository/local.json")) {
            if (is != null) {
                JsonNode root = mapper.readTree(is);
                loadRepositoryFromNode(byName, root, null);
            }
        } catch (IOException e) {
            // Fail softly; repository data is optional
            if (org.lucee.lucli.LuCLI.verbose || org.lucee.lucli.LuCLI.debug) {
                System.err.println("Warning: Failed to load local module repository index: " + e.getMessage());
            }
        }

        // 2) Load external repositories from settings.json (if configured)
        loadExternalRepositories(byName, mapper);

        return new ModuleRepositoryIndex(Collections.unmodifiableMap(byName));
    }

    /**
     * Load external module repositories configured in ~/.lucli/settings.json
     * under the top-level "moduleRepositories" array. Each entry should be of
     * the form { "name": "repoName", "url": "https://.../modules.json" }.
     */
    private static void loadExternalRepositories(Map<String, RepoModule> byName, ObjectMapper mapper) {
        Path settingsPath = getSettingsFilePath();
        if (settingsPath == null || !Files.exists(settingsPath)) {
            return;
        }

        try {
            String content = Files.readString(settingsPath);
            if (content == null || content.trim().isEmpty()) {
                return;
            }
            JsonNode root = mapper.readTree(content);
            if (root == null || !root.isObject()) {
                return;
            }

            JsonNode reposNode = root.get("moduleRepositories");
            if (reposNode == null || !reposNode.isArray()) {
                return;
            }

            for (JsonNode repoNode : reposNode) {
                if (!repoNode.isObject()) {
                    continue;
                }
                String name = repoNode.path("name").asText("");
                String url = repoNode.path("url").asText("");
                if (url.isEmpty()) {
                    continue;
                }

                try {
                    JsonNode repoRoot = fetchRepositoryJson(url, mapper);
                    if (repoRoot != null) {
                        loadRepositoryFromNode(byName, repoRoot, name.isEmpty() ? null : name);
                    }
                } catch (IOException e) {
                    if (org.lucee.lucli.LuCLI.verbose || org.lucee.lucli.LuCLI.debug) {
                        System.err.println("Warning: Failed to load external module repository '" + name + "' from " + url + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (org.lucee.lucli.LuCLI.verbose || org.lucee.lucli.LuCLI.debug) {
                System.err.println("Warning: Failed to read settings.json for external module repositories: " + e.getMessage());
            }
        }
    }

    /**
     * Populate the index from a repository JSON object with the standard
     * { repository, modules: [ {name, description, url}, ... ] } shape.
     *
     * @param byName       target map of module name to RepoModule
     * @param root         parsed JSON root node
     * @param overrideName optional repository name to use instead of the
     *                     "repository" field in the JSON (may be null)
     */
    private static void loadRepositoryFromNode(Map<String, RepoModule> byName, JsonNode root, String overrideName) {
        if (root == null || !root.isObject()) {
            return;
        }

        String repoName = overrideName != null ? overrideName : root.path("repository").asText("external");
        JsonNode modulesNode = root.path("modules");
        if (!modulesNode.isArray()) {
            return;
        }

        for (JsonNode mod : modulesNode) {
            String name = mod.path("name").asText(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            String description = mod.path("description").asText("");
            String url = mod.path("url").asText("");

            // Do not override entries that already exist (e.g. bundled local repo)
            if (byName.containsKey(name)) {
                continue;
            }

            RepoModule rm = new RepoModule(name, description, url, repoName);
            byName.put(name, rm);
        }
    }

    /**
     * Fetch repository JSON from a remote URL.
     */
    private static JsonNode fetchRepositoryJson(String urlString, ObjectMapper mapper) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " when fetching module repository from " + urlString);
        }

        try (InputStream is = conn.getInputStream()) {
            return mapper.readTree(is);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Compute the path to ~/.lucli/settings.json (respecting lucli.home and
     * LUCLI_HOME overrides).
     */
    private static Path getSettingsFilePath() {
        String lucliHomeStr = System.getProperty("lucli.home");
        if (lucliHomeStr == null) {
            lucliHomeStr = System.getenv("LUCLI_HOME");
        }
        if (lucliHomeStr == null) {
            String userHome = System.getProperty("user.home");
            lucliHomeStr = Paths.get(userHome, ".lucli").toString();
        }
        Path lucliHome = Paths.get(lucliHomeStr);
        return lucliHome.resolve("settings.json");
    }

    public Map<String, RepoModule> getModulesByName() {
        return modulesByName;
    }
}
