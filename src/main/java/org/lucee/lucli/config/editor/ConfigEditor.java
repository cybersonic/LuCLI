package org.lucee.lucli.config.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Scanner;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.lucee.lucli.commands.ServerConfigHelper;
import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerConfig.ServerConfig;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Very small, prompt-based editor for the "General" tab.
 *
 * This is intended as a starting point: it focuses on the core
 * General fields and can later be swapped out for a full curses/JLine UI.
 */
public class ConfigEditor {

    private static LineReader versionLineReader;

    /**
     * Edit the General tab fields of a lucee.json configuration.
     *
     * @return true if any changes were saved.
     */
    public boolean edit(Path projectDir,
                        Path configFile,
                        ServerConfig config,
                        JsonNode schema,
                        String environment) throws IOException {

        Scanner scanner = new Scanner(System.in);

        
        header("Lucee Config Editor");
        description("Project:  " + projectDir.toAbsolutePath());
        description("Config:   " + configFile.toAbsolutePath());
        if (environment != null && !environment.trim().isEmpty()) {
            description("Env:      " + environment.trim());
        }
        newline();

        Map<String, Runnable> updaters = new LinkedHashMap<>();

        // Optional: show short help using schema descriptions
        JsonNode props = schema != null ? schema.path("properties") : null;

        // --- General section ---
        title("General");

        // name
        printFieldHelp("name", props);
        System.out.print("Name [" + nullToEmpty(config.name) + "]: ");
        String name = scanner.nextLine().trim();
        if (!name.isEmpty()) {
            updaters.put("name", () -> config.name = name);
        }

        // host
        printFieldHelp("host", props);
        System.out.print("Host [" + nullToEmpty(config.host) + "]: ");
        String host = scanner.nextLine().trim();
        if (!host.isEmpty()) {
            updaters.put("host", () -> config.host = host);
        }

        // version
        printFieldHelp("version", props);

        // Show a short suggestion list of known versions (if available)
        List<String> suggestedVersions = null;
        int suggestedCount = 0;
        try {
            ServerConfigHelper helper = new ServerConfigHelper();
            List<String> versions = helper.getAvailableVersions();
            if (versions != null && !versions.isEmpty()) {
                System.out.println("Known Lucee versions (latest first, top 5):");
                suggestedCount = Math.min(5, versions.size());
                suggestedVersions = versions.subList(0, suggestedCount);
                for (int i = 0; i < suggestedCount; i++) {
                    System.out.println("  " + (i + 1) + ") " + suggestedVersions.get(i));
                }
            }
        } catch (Exception e) {
            // Non-fatal; just skip suggestions if we can't load them
        }

        String versionInput = readVersionLine(
                "Lucee version (or # from list) [" + nullToEmpty(config.version) + "]: ", scanner).trim();
        if (!versionInput.isEmpty()) {
            String chosenVersion = versionInput;
            // Allow "1".."5" to pick from the suggested list
            if (suggestedVersions != null && versionInput.matches("\\d+")) {
                try {
                    int index = Integer.parseInt(versionInput) - 1;
                    if (index >= 0 && index < suggestedCount) {
                        chosenVersion = suggestedVersions.get(index);
                        System.out.println("  → Using " + chosenVersion);
                    }
                } catch (NumberFormatException ignored) {
                    // Fall back to raw input
                }
            }
            final String applyVersion = chosenVersion;
            updaters.put("version", () -> config.version = applyVersion);
        }

        // port
        printFieldHelp("port", props);
        System.out.print("HTTP port [" + config.port + "]: ");
        String portInput = scanner.nextLine().trim();
        if (!portInput.isEmpty()) {
            try {
                int port = Integer.parseInt(portInput);
                if (port < 1 || port > 65535) {
                    System.out.println("⚠️ Port must be between 1 and 65535. Ignoring change.");
                } else {
                    updaters.put("port", () -> config.port = port);
                }
            } catch (NumberFormatException e) {
                System.out.println("⚠️ Invalid port number. Ignoring change.");
            }
        }

        // webroot
        printFieldHelp("webroot", props);
        System.out.print("Webroot [" + nullToEmpty(config.webroot) + "]: ");
        String webroot = scanner.nextLine().trim();
        if (!webroot.isEmpty()) {
            updaters.put("webroot", () -> config.webroot = webroot);
        }

        // enableLucee
        printFieldHelp("enableLucee", props);
        System.out.print("Enable Lucee (true/false) [" + config.enableLucee + "]: ");
        String enableLuceeInput = scanner.nextLine().trim();
        if (!enableLuceeInput.isEmpty()) {
            Boolean parsed = parseBoolean(enableLuceeInput);
            if (parsed == null) {
                System.out.println("⚠️ Expected true/false. Ignoring change.");
            } else {
                updaters.put("enableLucee", () -> config.enableLucee = parsed);
            }
        }

        // enableREST
        printFieldHelp("enableREST", props);
        System.out.print("Enable REST (true/false) [" + config.enableREST + "]: ");
        String enableRestInput = scanner.nextLine().trim();
        if (!enableRestInput.isEmpty()) {
            Boolean parsed = parseBoolean(enableRestInput);
            if (parsed == null) {
                System.out.println("⚠️ Expected true/false. Ignoring change.");
            } else {
                updaters.put("enableREST", () -> config.enableREST = parsed);
            }
        }

        newline();

        // --- JVM section ---
        title("JVM");

        String currentMax = config.jvm != null ? nullToEmpty(config.jvm.maxMemory) : "";
        description("Max heap size (e.g. 512m, 2g).");
        System.out.print("Max memory [" + currentMax + "]: ");
        String maxMemInput = scanner.nextLine().trim();
        if (!maxMemInput.isEmpty()) {
            if (!maxMemInput.matches("^[1-9]\\d*(m|g)$")) {
                System.out.println("⚠️ Expected format like 512m or 2g. Ignoring change.");
            } else {
                updaters.put("jvm.maxMemory", () -> {
                    ensureJvmConfig(config);
                    config.jvm.maxMemory = maxMemInput;
                });
            }
        }

        String currentMin = config.jvm != null ? nullToEmpty(config.jvm.minMemory) : "";
        description("Initial heap size (e.g. 128m, 512m).");
        System.out.print("Min memory [" + currentMin + "]: ");
        String minMemInput = scanner.nextLine().trim();
        if (!minMemInput.isEmpty()) {
            if (!minMemInput.matches("^[1-9]\\d*(m|g)$")) {
                System.out.println("⚠️ Expected format like 128m or 1g. Ignoring change.");
            } else {
                updaters.put("jvm.minMemory", () -> {
                    ensureJvmConfig(config);
                    config.jvm.minMemory = minMemInput;
                });
            }
        }

        String[] currentJvmArgs = (config.jvm != null && config.jvm.additionalArgs != null)
                ? config.jvm.additionalArgs
                : new String[0];
        if (currentJvmArgs.length == 0) {
            System.out.println("  Additional JVM args: (none)");
        } else {
            System.out.println("  Additional JVM args:");
            for (int i = 0; i < currentJvmArgs.length; i++) {
                System.out.println("    " + (i + 1) + ") " + currentJvmArgs[i]);
            }
        }
        System.out.print("Set JVM additional args as space-separated list (blank to keep, '-' to clear): ");
        String jvmArgsInput = scanner.nextLine().trim();
        if (!jvmArgsInput.isEmpty()) {
            if ("-".equals(jvmArgsInput)) {
                updaters.put("jvm.additionalArgs", () -> {
                    ensureJvmConfig(config);
                    config.jvm.additionalArgs = new String[0];
                });
            } else {
                final String[] newArgs = jvmArgsInput.split("\\s+");
                updaters.put("jvm.additionalArgs", () -> {
                    ensureJvmConfig(config);
                    config.jvm.additionalArgs = newArgs;
                });
            }
        }

        newline();

        // --- HTTPS section ---
        title("HTTPS");

        boolean currentHttpsEnabled = config.https != null && config.https.enabled;
        description("Optional HTTPS configuration. Adds HTTPS connector and self-signed keystore.");
        System.out.print("Enable HTTPS (true/false) [" + currentHttpsEnabled + "]: ");
        String httpsEnableInput = scanner.nextLine().trim();
        Boolean httpsEnabledParsed = httpsEnableInput.isEmpty() ? null : parseBoolean(httpsEnableInput);
        if (!httpsEnableInput.isEmpty() && httpsEnabledParsed == null) {
            System.out.println("⚠️ Expected true/false. Ignoring HTTPS enabled change.");
            httpsEnabledParsed = null;
        }

        final Boolean httpsEnabledFinal = httpsEnabledParsed;
        Integer httpsPortNew = null;
        Boolean httpsRedirectNew = null;

        boolean effectiveHttpsEnabled = httpsEnabledFinal != null ? httpsEnabledFinal : currentHttpsEnabled;
        if (effectiveHttpsEnabled) {
            int currentHttpsPort = 8443;
            if (config.https != null && config.https.port != null) {
                currentHttpsPort = config.https.port;
            }
            System.out.print("HTTPS port [" + currentHttpsPort + "]: ");
            String httpsPortInput = scanner.nextLine().trim();
            if (!httpsPortInput.isEmpty()) {
                try {
                    int p = Integer.parseInt(httpsPortInput);
                    if (p < 1 || p > 65535) {
                        System.out.println("⚠️ HTTPS port must be between 1 and 65535. Ignoring change.");
                    } else {
                        httpsPortNew = p;
                        if (p == config.port) {
                            System.out.println("⚠️ HTTPS port matches HTTP port; this is usually not desired.");
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println("⚠️ Invalid HTTPS port. Ignoring change.");
                }
            }

            Boolean currentRedirect = config.https != null ? config.https.redirect : null;
            boolean defaultRedirect = currentRedirect != null ? currentRedirect : true;
            description("Redirect HTTP to HTTPS (defaults to true when HTTPS is enabled).");
            System.out.print("Redirect HTTP to HTTPS (true/false) [" + defaultRedirect + "]: ");
            String redirectInput = scanner.nextLine().trim();
            if (!redirectInput.isEmpty()) {
                Boolean parsed = parseBoolean(redirectInput);
                if (parsed == null) {
                    System.out.println("⚠️ Expected true/false. Ignoring redirect change.");
                } else {
                    httpsRedirectNew = parsed;
                }
            }
        }

        final Integer httpsPortFinal = httpsPortNew;
        final Boolean httpsRedirectFinal = httpsRedirectNew;

        if (httpsEnabledFinal != null || httpsPortFinal != null || httpsRedirectFinal != null) {
            updaters.put("https", () -> {
                if (config.https == null) {
                    config.https = new LuceeServerConfig.HttpsConfig();
                }
                if (httpsEnabledFinal != null) {
                    config.https.enabled = httpsEnabledFinal;
                }
                if (httpsPortFinal != null) {
                    config.https.port = httpsPortFinal;
                }
                if (httpsRedirectFinal != null) {
                    config.https.redirect = httpsRedirectFinal;
                }
            });
        }

        newline();

        // --- Admin section ---
        title("Admin");
        boolean currentAdminEnabled = config.admin != null && config.admin.enabled;
        description("Controls Lucee administrator exposure.");
        System.out.print("Enable admin UI (true/false) [" + currentAdminEnabled + "]: ");
        String adminEnableInput = scanner.nextLine().trim();
        Boolean adminEnabledParsed = adminEnableInput.isEmpty() ? null : parseBoolean(adminEnableInput);
        if (!adminEnableInput.isEmpty() && adminEnabledParsed == null) {
            System.out.println("⚠️ Expected true/false. Ignoring admin enabled change.");
            adminEnabledParsed = null;
        }

        String currentPassword = (config.admin != null) ? nullToEmpty(config.admin.password) : "";
        if (currentPassword.isEmpty()) {
            description("Admin password: (not set)");
        } else if (currentPassword.matches("^\\$\\{.+}$")) {
            description("Admin password uses environment variable reference (recommended).");
        } else {
            description("Admin password is plain text (consider switching to an env var reference).");
        }

        System.out.println("Admin password options:");
        description("k) keep current");
        description("e) use environment variable reference (e.g. ${LUCEE_ADMIN_PASSWORD})");
        description("p) set plain password");
        System.out.print("Choice [k]: ");
        String adminChoice = scanner.nextLine().trim().toLowerCase();

        final Boolean adminEnabledFinal = adminEnabledParsed;
        String newAdminPassword = null;

        if (adminChoice.isEmpty() || adminChoice.equals("k")) {
            // keep
        } else if (adminChoice.equals("e")) {
            System.out.print("Env var name [LUCEE_ADMIN_PASSWORD]: ");
            String var = scanner.nextLine().trim();
            if (var.isEmpty()) {
                var = "LUCEE_ADMIN_PASSWORD";
            }
            newAdminPassword = "${" + var + "}";
        } else if (adminChoice.equals("p")) {
            System.out.print("Enter new admin password: ");
            String p1 = scanner.nextLine();
            System.out.print("Confirm new admin password: ");
            String p2 = scanner.nextLine();
            if (!p1.equals(p2)) {
                System.out.println("⚠️ Passwords do not match. Ignoring password change.");
            } else {
                newAdminPassword = p1;
            }
        }

        if (adminEnabledFinal != null || newAdminPassword != null) {
            final String pw = newAdminPassword;
            updaters.put("admin", () -> {
                if (config.admin == null) {
                    config.admin = new LuceeServerConfig.AdminConfig();
                }
                if (adminEnabledFinal != null) {
                    config.admin.enabled = adminEnabledFinal;
                }
                if (pw != null) {
                    config.admin.password = pw;
                }
            });
        }

        newline();

        // --- URL Rewrite section ---
        title("URL Rewrite");
        boolean currentUrlRewriteEnabled = config.urlRewrite != null && config.urlRewrite.enabled;
        description("Framework-style URL routing to a central router file.");
        System.out.print("Enable URL rewrite (true/false) [" + currentUrlRewriteEnabled + "]: ");
        String urlRewriteEnableInput = scanner.nextLine().trim();
        Boolean urlRewriteEnabledParsed = urlRewriteEnableInput.isEmpty() ? null : parseBoolean(urlRewriteEnableInput);
        if (!urlRewriteEnableInput.isEmpty() && urlRewriteEnabledParsed == null) {
            System.out.println("⚠️ Expected true/false. Ignoring URL rewrite enabled change.");
            urlRewriteEnabledParsed = null;
        }

        String currentRouterFile = config.urlRewrite != null ? nullToEmpty(config.urlRewrite.routerFile) : "index.cfm";
        System.out.print("Router file [" + currentRouterFile + "]: ");
        String routerFileInput = scanner.nextLine().trim();

        final Boolean urlRewriteEnabledFinal = urlRewriteEnabledParsed;
        final String routerFileNew = routerFileInput.isEmpty() ? null : routerFileInput;

        if (urlRewriteEnabledFinal != null || routerFileNew != null) {
            updaters.put("urlRewrite", () -> {
                if (config.urlRewrite == null) {
                    config.urlRewrite = new LuceeServerConfig.UrlRewriteConfig();
                }
                if (urlRewriteEnabledFinal != null) {
                    config.urlRewrite.enabled = urlRewriteEnabledFinal;
                }
                if (routerFileNew != null) {
                    config.urlRewrite.routerFile = routerFileNew;
                }
            });
        }

        newline();

        // --- Agents section ---
        title("Agents");
        Map<String, LuceeServerConfig.AgentConfig> agentsCopy = new HashMap<>();
        if (config.agents != null) {
            for (Map.Entry<String, LuceeServerConfig.AgentConfig> e : config.agents.entrySet()) {
                LuceeServerConfig.AgentConfig src = e.getValue();
                LuceeServerConfig.AgentConfig copy = new LuceeServerConfig.AgentConfig();
                copy.enabled = src.enabled;
                copy.description = src.description;
                copy.jvmArgs = src.jvmArgs != null ? src.jvmArgs.clone() : new String[0];
                agentsCopy.put(e.getKey(), copy);
            }
        }

        while (true) {
            if (agentsCopy.isEmpty()) {
                description("No agents defined.");
            } else {
                description("Defined agents:");
                int idx = 1;
                for (Map.Entry<String, LuceeServerConfig.AgentConfig> e : agentsCopy.entrySet()) {
                    description(idx + ") " + e.getKey() + "  [enabled: " + e.getValue().enabled + "]");
                    idx++;
                }
            }
            description("a) add new agent");
            System.out.print("Select agent number to edit, 'a' to add, or Enter to continue: ");
            String choice = scanner.nextLine().trim();
            if (choice.isEmpty()) {
                break;
            }
            if (choice.equalsIgnoreCase("a")) {
                System.out.print("New agent id: ");
                String id = scanner.nextLine().trim();
                if (id.isEmpty()) {
                    System.out.println("⚠️ Agent id cannot be empty.");
                    continue;
                }
                if (agentsCopy.containsKey(id)) {
                    System.out.println("⚠️ Agent with id '" + id + "' already exists.");
                    continue;
                }
                LuceeServerConfig.AgentConfig a = new LuceeServerConfig.AgentConfig();
                a.enabled = false;
                a.jvmArgs = new String[0];
                a.description = null;
                agentsCopy.put(id, a);
                continue;
            }
            if (!choice.matches("\\d+")) {
                System.out.println("⚠️ Expected a number or 'a'.");
                continue;
            }
            int sel = Integer.parseInt(choice) - 1;
            if (sel < 0 || sel >= agentsCopy.size()) {
                System.out.println("⚠️ Invalid selection.");
                continue;
            }
            String selectedId = agentsCopy.keySet().stream().skip(sel).findFirst().orElse(null);
            if (selectedId == null) {
                System.out.println("⚠️ Invalid selection.");
                continue;
            }
            LuceeServerConfig.AgentConfig agent = agentsCopy.get(selectedId);
            System.out.println("Editing agent '" + selectedId + "'");
            description("1) Toggle enabled (currently " + agent.enabled + ")");
            description("2) Edit JVM args");
            description("3) Edit description");
            System.out.print("Choice [Enter to go back]: ");
            String act = scanner.nextLine().trim();
            if (act.isEmpty()) {
                continue;
            }
            switch (act) {
                case "1":
                    agent.enabled = !agent.enabled;
                    System.out.println("  → Enabled set to " + agent.enabled);
                    break;
                case "2":
                    if (agent.jvmArgs == null || agent.jvmArgs.length == 0) {
                        System.out.println("  Current JVM args: (none)");
                    } else {
                        System.out.println("  Current JVM args:");
                        for (int i = 0; i < agent.jvmArgs.length; i++) {
                            System.out.println("    " + (i + 1) + ") " + agent.jvmArgs[i]);
                        }
                    }
                    System.out.print("  New JVM args as space-separated list (blank to keep, '-' to clear): ");
                    String aa = scanner.nextLine().trim();
                    if (!aa.isEmpty()) {
                        if ("-".equals(aa)) {
                            agent.jvmArgs = new String[0];
                        } else {
                            agent.jvmArgs = aa.split("\\s+");
                        }
                    }
                    break;
                case "3":
                    System.out.print("  New description (blank to clear): ");
                    String desc = scanner.nextLine();
                    agent.description = desc.isEmpty() ? null : desc;
                    break;
                default:
                    System.out.println("⚠️ Unknown choice.");
                    break;
            }
        }

        if (!agentsCopy.isEmpty()) {
            updaters.put("agents", () -> config.agents = agentsCopy);
        }

        if (updaters.isEmpty()) {
            newline();
            System.out.println("No changes entered.");
            return false;
        }

        newline();
        System.out.println("You are about to update these fields:");
        for (String key : updaters.keySet()) {
            System.out.println("  - " + key);
        }
        System.out.print("Save changes to " + configFile.getFileName() + "? [y/N]: ");
        String confirm = scanner.nextLine().trim().toLowerCase();

        if (!confirm.equals("y") && !confirm.equals("yes")) {
            System.out.println("Changes discarded.");
            return false;
        }

        // Apply changes
        updaters.values().forEach(Runnable::run);

        // Persist using existing helper so formatting and defaults are consistent
        LuceeServerConfig.saveConfig(config, configFile);

        System.out.println("✔ Saved updates to " + configFile.toAbsolutePath());
        return true;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void ensureJvmConfig(ServerConfig config) {
        if (config.jvm == null) {
            config.jvm = new LuceeServerConfig.JvmConfig();
        }
    }

    private static String readVersionLine(String prompt, Scanner fallbackScanner) {
        try {
            if (versionLineReader == null) {
                Terminal terminal = TerminalBuilder.builder()
                        .system(true)
                        .build();
                versionLineReader = LineReaderBuilder.builder()
                        .terminal(terminal)
                        .completer(new LuceeVersionCompleter())
                        .build();
            }
            return versionLineReader.readLine(prompt);
        } catch (Exception e) {
            // Fallback to plain stdin if JLine cannot be initialized
            System.out.print(prompt);
            return fallbackScanner.nextLine();
        }
    }

    private static void printFieldHelp(String fieldName, JsonNode props) {
        if (props == null || fieldName == null) {
            return;
        }
        JsonNode field = props.get(fieldName);
        if (field == null) {
            return;
        }
        JsonNode desc = field.get("description");
        if (desc != null && desc.isTextual()) {
            description(desc.asText());
        }
    }
    private static void header(String text) {
        System.out.println("=== " + text + " ===");
    }

    private static void title(String text) {
        System.out.println("[ " + text + " ]");
    }

    private static void description(String text) {
        System.out.println("    " + text);
    }

    private static void newline() {
        System.out.println("");
    }

    /**
     * JLine completer for Lucee versions, using the same ServerConfigHelper
     * used elsewhere (and by LuceeVersionCandidates).
     */
    private static class LuceeVersionCompleter implements Completer {
        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String buffer = line.word().trim();
            try {
                ServerConfigHelper helper = new ServerConfigHelper();
                List<String> versions = helper.getAvailableVersions();
                if (versions == null || versions.isEmpty()) {
                    return;
                }
                for (String v : versions) {
                    if (buffer.isEmpty() || v.startsWith(buffer)) {
                        candidates.add(new Candidate(v));
                    }
                }
            } catch (Exception e) {
                // On failure, leave candidates empty (no completion)
            }
        }
    }

    private static Boolean parseBoolean(String input) {
        String v = input.toLowerCase();
        if (v.equals("true") || v.equals("t") || v.equals("yes") || v.equals("y")) {
            return Boolean.TRUE;
        }
        if (v.equals("false") || v.equals("f") || v.equals("no") || v.equals("n")) {
            return Boolean.FALSE;
        }
        return null;
    }
}
