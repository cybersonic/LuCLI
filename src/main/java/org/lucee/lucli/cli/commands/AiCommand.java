package org.lucee.lucli.cli.commands;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.paths.LucliPaths;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * AI command integration that relies on endpoints managed by the local Lucee instance.
 *
 * LuCLI stores only lightweight local defaults and skill configuration paths.
 */
@Command(
    name = "ai",
    description = "Use Lucee AI endpoints and prompts",
    mixinStandardHelpOptions = true,
    subcommands = {
        AiCommand.ConfigCommand.class,
        AiCommand.PromptCommand.class,
        AiCommand.ListCommand.class,
        AiCommand.TestCommand.class,
        AiCommand.SkillCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class AiCommand implements Callable<Integer> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESPONSE_PROCESS_COOKIES_MARKER = "ResponseProcessCookies";
    private static final String OPENAI_ENGINE_CLASS = "lucee.runtime.ai.openai.OpenAIEngine";
    private static final Map<String, ProviderDefaults> GUIDED_PROVIDER_DEFAULTS = Map.of(
        "openai", new ProviderDefaults(OPENAI_ENGINE_CLASS, "gpt-4o", null),
        "copilot", new ProviderDefaults(OPENAI_ENGINE_CLASS, "gpt-4.1", null),
        "deepseek", new ProviderDefaults(OPENAI_ENGINE_CLASS, "deepseek-chat", null),
        "grok", new ProviderDefaults(OPENAI_ENGINE_CLASS, "grok-2-latest", null),
        "ollama", new ProviderDefaults(OPENAI_ENGINE_CLASS, "llama3.1", "http://localhost:11434/v1"),
        "perplexity", new ProviderDefaults(OPENAI_ENGINE_CLASS, "sonar", null)
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".tif", ".tiff"
    );


    @Override
    public Integer call() throws Exception {
        new CommandLine(this).usage(System.out);
        return 0;
    }


    @Command(
        name = "config",
        aliases = {"configure"},
        description = "Manage Lucee AI config defaults and endpoint entries",
        mixinStandardHelpOptions = true,
        subcommands = {
            ConfigCommand.DefaultsCommand.class,
            ConfigCommand.AddCommand.class,
            ConfigCommand.ListCommand.class,
            CommandLine.HelpCommand.class
        }
    )
    static class ConfigCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            new CommandLine(this).usage(System.out);
            return 0;
        }

        @Command(
            name = "defaults",
            description = "View/update LuCLI AI defaults",
            mixinStandardHelpOptions = true
        )
        static class DefaultsCommand implements Callable<Integer> {
            @Option(names = "--default-endpoint", description = "Default Lucee endpoint name")
            private String defaultEndpoint;

            @Option(names = "--default-model", description = "Default model hint used by LuCLI prompt templates")
            private String defaultModel;

            @Option(names = "--show", description = "Show current AI defaults")
            private boolean show;

            @Override
            public Integer call() throws Exception {
                AiLocalConfig config = loadAiLocalConfig();
                boolean changed = false;

                if (!isBlank(defaultEndpoint)) {
                    config.defaultEndpoint = defaultEndpoint.trim();
                    changed = true;
                }
                if (!isBlank(defaultModel)) {
                    config.defaultModel = defaultModel.trim();
                    changed = true;
                }

                if (changed) {
                    saveAiLocalConfig(config);
                    StringOutput.Quick.success("Saved AI defaults.");
                }

                if (show || changed || (!changed && isBlank(defaultEndpoint) && isBlank(defaultModel))) {
                    printAiDefaults(config);
                } else {
                    new CommandLine(this).usage(System.out);
                }
                return 0;
            }
        }

        @Command(
            name = "add",
            description = "Add/update an AI endpoint entry via CFConfig import",
            mixinStandardHelpOptions = true
        )
        static class AddCommand implements Callable<Integer> {
            @Option(names = "--name", description = "Endpoint name/key (e.g., mychatgpt)")
            private String name;

            @Option(names = "--class", defaultValue = OPENAI_ENGINE_CLASS, description = "AI engine class")
            private String className;

            @Option(names = "--type", defaultValue = "openai", description = "Provider type (openai, copilot, deepseek, grok, ollama, perplexity, other)")
            private String type;

            @Option(names = "--url", description = "Optional custom endpoint URL")
            private String url;

            @Option(names = "--secret-key", description = "Secret/API key (supports placeholders like #env:CHATGPT_SECRET_KEY#)")
            private String secretKey;

            @Option(names = "--model", description = "Model name (e.g., gpt-4o)")
            private String model;

            @Option(names = "--message", defaultValue = "Keep all answers as short as possible", description = "System message")
            private String message;

            @Option(names = "--timeout", defaultValue = "5000", description = "Timeout in milliseconds")
            private Integer timeout;

            @Option(names = "--default-mode", defaultValue = "exception", description = "Default mode for this endpoint entry")
            private String defaultMode;

            @Option(names = "--json", description = "Print imported structure as JSON")
            private boolean json;

            @Option(names = "--show", description = "Show full secretKey values in output (otherwise masked)")
            private boolean showSecrets;
            @Option(names = "--guided", description = "Interactive guided setup for AI connection fields")
            private boolean guided;

            @Option(names = "--test-after-save", description = "Run a lightweight connection test after saving")
            private boolean testAfterSave;

            @Override
            public Integer call() throws Exception {
                boolean shouldTestAfterSave = testAfterSave;

                if (guided) {
                    java.io.Console console = System.console();
                    if (console == null) {
                        StringOutput.Quick.error("No console available for guided mode. Re-run without --guided and pass flags directly.");
                        return 1;
                    }

                    Boolean guidedTestChoice = promptGuidedValues(console, shouldTestAfterSave);
                    if (guidedTestChoice == null) {
                        StringOutput.Quick.info("Aborted.");
                        return 0;
                    }
                    shouldTestAfterSave = guidedTestChoice;
                }
                if (isBlank(name)) {
                    StringOutput.Quick.error("Endpoint name is required.");
                    return 1;
                }

                AddConfigRequest request = new AddConfigRequest();
                request.name = name.trim();
                request.className = className;
                request.type = type;
                request.url = url;
                request.secretKey = secretKey;
                request.model = model;
                request.message = message;
                request.timeout = timeout;
                request.defaultMode = defaultMode;

                PromptResult result = executeConfigAdd(request);
                StringOutput.Quick.success("AI endpoint config saved in Lucee local config: " + request.name);

                if (json) {
                    String sourceJson = firstNonBlank(result.json, result.text);
                    System.out.println(prettyJsonOrRaw(redactSecretKeysInJsonString(sourceJson, showSecrets)));
                } else if (!isBlank(result.text)) {
                    String sourceJson = firstNonBlank(result.json, result.text);
                    System.out.println(prettyJsonOrRaw(redactSecretKeysInJsonString(sourceJson, showSecrets)));
                }

                if (shouldTestAfterSave) {
                    return runConnectionTest(request.name);
                }
                return 0;
            }

            private Boolean promptGuidedValues(java.io.Console console, boolean currentTestChoice) throws Exception {
                StringOutput.Quick.info("Guided AI connection setup");

                name = promptRequired(console, "Endpoint name", name);
                type = normalizeProviderType(promptWithDefault(console, "Provider type", type));
                applyGuidedProviderDefaults(type);
                className = promptWithDefault(console, "Engine class", className);
                model = promptOptional(console, "Model", model);
                secretKey = promptSecret(console, "Secret/API key (supports #env:...# placeholders)", secretKey);
                url = promptOptional(console, "Custom URL", url);
                message = promptWithDefault(console, "System message", message);
                timeout = promptInteger(console, "Timeout (ms)", timeout, 5000);
                defaultMode = promptWithDefault(console, "Default mode", defaultMode);

                System.out.println();
                System.out.println("Summary:");
                System.out.println("  name: " + fallback(name));
                System.out.println("  type: " + fallback(type));
                System.out.println("  class: " + fallback(className));
                System.out.println("  model: " + fallback(model));
                System.out.println("  secretKey: " + (isBlank(secretKey) ? "(unset)" : "(set)"));
                System.out.println("  url: " + fallback(url));
                System.out.println("  timeout: " + (timeout == null ? "(unset)" : timeout));
                System.out.println("  default: " + fallback(defaultMode));
                System.out.println();

                if (!promptYesNo(console, "Save this AI connection?", true)) {
                    return null;
                }

                if (!currentTestChoice) {
                    currentTestChoice = promptYesNo(console, "Test connection after save?", false);
                }
                return currentTestChoice;
            }

            private void applyGuidedProviderDefaults(String providerType) {
                ProviderDefaults defaults = GUIDED_PROVIDER_DEFAULTS.get(providerType);
                if (defaults == null) {
                    return;
                }

                className = applyDefaultIfUnset(className, defaults.className);
                model = applyDefaultIfUnset(model, defaults.model);
                url = applyDefaultIfUnset(url, defaults.url);

                List<String> hints = new ArrayList<>();
                if (!isBlank(defaults.className)) {
                    hints.add("class=" + defaults.className);
                }
                if (!isBlank(defaults.model)) {
                    hints.add("model=" + defaults.model);
                }
                if (!isBlank(defaults.url)) {
                    hints.add("url=" + defaults.url);
                }
                if (!hints.isEmpty()) {
                    StringOutput.Quick.info("Suggested defaults for '" + providerType + "': " + String.join(", ", hints));
                }
            }

            private static String applyDefaultIfUnset(String currentValue, String defaultValue) {
                if (!isBlank(currentValue) || isBlank(defaultValue)) {
                    return currentValue;
                }
                return defaultValue;
            }

            private static String normalizeProviderType(String value) {
                if (isBlank(value)) {
                    return "openai";
                }
                return value.trim().toLowerCase(Locale.ROOT);
            }

            private Integer runConnectionTest(String endpoint) {
                try {
                    PromptRequest request = new PromptRequest();
                    request.endpoint = endpoint;
                    request.text = "Respond with the word: pong";
                    request.files = new ArrayList<>();

                    PromptResult result = executePrompt(request);
                    StringOutput.Quick.success("AI endpoint test completed for '" + endpoint + "'.");

                    if (json) {
                        if (!isBlank(result.json)) {
                            System.out.println(prettyJsonOrRaw(result.json));
                        } else {
                            Map<String, Object> envelope = new LinkedHashMap<>();
                            envelope.put("result", result.text);
                            System.out.println(prettyJsonOrRaw(toJson(envelope)));
                        }
                    } else if (!isBlank(result.text)) {
                        System.out.println(result.text);
                    }
                    return 0;
                } catch (Exception e) {
                    return printFriendlyAiFailure("AI endpoint test failed", endpoint, e);
                }
            }

            private static String promptRequired(java.io.Console console, String label, String currentValue) {
                String value = promptWithDefault(console, label, currentValue);
                while (isBlank(value)) {
                    value = promptWithDefault(console, label, value);
                    if (isBlank(value)) {
                        System.out.println("Value is required.");
                    }
                }
                return value.trim();
            }

            private static String promptOptional(java.io.Console console, String label, String currentValue) {
                String value = promptWithDefault(console, label + " (optional)", currentValue);
                return isBlank(value) ? null : value.trim();
            }

            private static String promptWithDefault(java.io.Console console, String label, String currentValue) {
                String prompt = isBlank(currentValue) ? "%s: " : "%s [%s]: ";
                String input = isBlank(currentValue)
                    ? console.readLine(prompt, label)
                    : console.readLine(prompt, label, currentValue);

                if (input == null) {
                    return currentValue;
                }

                String trimmed = input.trim();
                if (trimmed.isEmpty()) {
                    return currentValue;
                }
                return trimmed;
            }

            private static String promptSecret(java.io.Console console, String label, String currentValue) {
                char[] input = console.readPassword("%s%s: ", label, isBlank(currentValue) ? " (optional)" : " (optional, press Enter to keep current)");
                if (input == null || input.length == 0) {
                    return currentValue;
                }
                return new String(input).trim();
            }

            private static Integer promptInteger(java.io.Console console, String label, Integer currentValue, Integer fallbackValue) {
                Integer effectiveDefault = currentValue != null ? currentValue : fallbackValue;
                while (true) {
                    String raw = promptWithDefault(console, label, effectiveDefault == null ? null : effectiveDefault.toString());
                    if (isBlank(raw)) {
                        if (effectiveDefault != null) {
                            return effectiveDefault;
                        }
                        System.out.println("Value is required.");
                        continue;
                    }
                    try {
                        return Integer.valueOf(raw.trim());
                    } catch (NumberFormatException nfe) {
                        System.out.println("Please enter a valid integer.");
                    }
                }
            }

            private static boolean promptYesNo(java.io.Console console, String label, boolean defaultValue) {
                while (true) {
                    String suffix = defaultValue ? " [Y/n]: " : " [y/N]: ";
                    String raw = console.readLine("%s%s", label, suffix);
                    if (raw == null || raw.trim().isEmpty()) {
                        return defaultValue;
                    }
                    String normalized = raw.trim().toLowerCase(Locale.ROOT);
                    if (normalized.equals("y") || normalized.equals("yes")) {
                        return true;
                    }
                    if (normalized.equals("n") || normalized.equals("no")) {
                        return false;
                    }
                    System.out.println("Please answer y or n.");
                }
            }

            private static String toJson(Object value) {
                try {
                    return MAPPER.writeValueAsString(value);
                } catch (Exception e) {
                    return String.valueOf(value);
                }
            }
        }

        @Command(
            name = "list",
            description = "List AI endpoint entries from Lucee server CFConfig",
            mixinStandardHelpOptions = true
        )
        static class ListCommand implements Callable<Integer> {
            @Option(names = "--name", description = "Filter by endpoint name (repeatable)")
            private List<String> names = new ArrayList<>();

            @Option(names = "--json", description = "Output as JSON")
            private boolean json;

            @Option(names = "--show", description = "Show full secretKey values (otherwise masked)")
            private boolean showSecrets;

            @Override
            public Integer call() throws Exception {
                Path cfConfigFile = luceeCfConfigFile();
                if (!Files.exists(cfConfigFile) || !Files.isRegularFile(cfConfigFile)) {
                    if (json) {
                        System.out.println("{}");
                    } else {
                        StringOutput.Quick.info("No Lucee CFConfig file found: " + cfConfigFile);
                    }
                    return 0;
                }

                ObjectNode allEntries = readLuceeAiEntries(cfConfigFile);
                LinkedHashSet<String> requested = normalizeNames(names);
                ObjectNode result = requested.isEmpty()
                    ? allEntries
                    : filterAiEntries(allEntries, requested);

                if (json) {
                    JsonNode output = redactedCopy(result, showSecrets);
                    System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));
                    return 0;
                }

                if (!requested.isEmpty()) {
                    for (String name : requested) {
                        if (!allEntries.has(name)) {
                            StringOutput.Quick.warning("AI provider not found: " + name);
                        }
                    }
                }

                if (result.isEmpty()) {
                    StringOutput.Quick.info("No AI providers configured.");
                    return 0;
                }

                printAiConfigEntries(result, null, showSecrets);
                return 0;
            }
        }
    }

    @Command(
        name = "prompt",
        description = "Run one-shot prompt using a Lucee-managed endpoint",
        mixinStandardHelpOptions = true
    )
    static class PromptCommand implements Callable<Integer> {

        @Option(names = "--text", description = "Prompt text")
        private String text;
        @Option(names = "--image", description = "Image file path (repeatable)")
        private List<Path> images = new ArrayList<>();

        @Option(names = "--rules-file", description = "Rules file to attach as instructions (repeatable)")
        private List<Path> rulesFiles = new ArrayList<>();

        @Option(names = "--rules-folder", description = "Folder containing rules files to attach (repeatable)")
        private List<Path> rulesFolders = new ArrayList<>();

        @Option(names = "--endpoint", description = "Lucee endpoint name")
        private String endpointName;

        @Option(names = "--model", description = "Model hint (stored in request context)")
        private String model;

        @Option(names = "--system", description = "High-priority instructions for the AI (plain text or @file)")
        private String systemInstruction;

        @Option(names = "--temperature", description = "Temperature")
        private Double temperature;

        @Option(names = "--timeout", description = "Socket timeout in milliseconds")
        private Integer timeoutMillis;

        @Option(names = "--skill", description = "Skill name or explicit skill file path")
        private String skillName;

        @Option(names = "--skills-path", description = "Command-local skill search path (repeatable)")
        private List<Path> commandSkillPaths = new ArrayList<>();

        @Option(names = "--json", description = "Output raw JSON when possible")
        private boolean json;

        @Override
        public Integer call() throws Exception {
            AiLocalConfig config = loadAiLocalConfig();

            SkillDefinition skill = null;
            if (!isBlank(skillName)) {
                skill = resolveSkill(skillName.trim(), commandSkillPaths, LuCLI.verbose);
                if (skill == null) {
                    StringOutput.Quick.error("Skill not found: " + skillName);
                    return 1;
                }
            }

            String endpoint = firstNonBlank(endpointName, config.defaultEndpoint);
            if (isBlank(endpoint)) {
                StringOutput.Quick.error("No endpoint specified. Use --endpoint or set --default-endpoint via 'lucli ai config'.");
                return 1;
            }

            String finalText = firstNonBlank(text, skill != null ? skill.text : null);
            String baseSystem = firstNonBlank(readSystemInstruction(systemInstruction), skill != null ? skill.system : null);
            String finalModel = firstNonBlank(model, skill != null ? skill.model : null, config.defaultModel);
            Double finalTemperature = firstNonNull(temperature, skill != null ? skill.temperature : null);
            Integer finalTimeout = firstNonNull(timeoutMillis, skill != null ? skill.timeoutMillis : null);
            List<Path> normalizedImages = normalizeAndValidateImages(images);
            List<Path> normalizedRuleFiles = normalizeAndCollectRuleFiles(rulesFiles, rulesFolders);
            String rulesBlock = buildRulesInstructionBlock(normalizedRuleFiles);
            String finalSystem = mergeInstructionBlocks(baseSystem, rulesBlock);

            if (isBlank(finalText) && normalizedImages.isEmpty()) {
                StringOutput.Quick.error("Prompt content is empty. Provide --text and/or at least one --image.");
                return 1;
            }

            if (!isBlank(finalModel) && LuCLI.verbose) {
                StringOutput.Quick.warning("Model override is a hint in this pass; active endpoint/model selection is controlled by Lucee endpoint config.");
            }
            if (!normalizedRuleFiles.isEmpty() && LuCLI.verbose) {
                LuCLI.verbose("Attached %d rules file(s).", normalizedRuleFiles.size());
            }

            PromptRequest request = new PromptRequest();
            request.endpoint = endpoint;
            request.model = finalModel;
            request.system = finalSystem;
            request.text = finalText;
            request.temperature = finalTemperature;
            request.timeoutMillis = finalTimeout;
            request.files = normalizedImages.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .collect(Collectors.toList());
            PromptResult result;
            try {
                result = executePrompt(request);
            } catch (Exception e) {
                return printFriendlyAiFailure("AI prompt failed", endpoint, e);
            }

            if (json) {
                if (!isBlank(result.json)) {
                    System.out.println(prettyJsonOrRaw(result.json));
                } else {
                    Map<String, Object> envelope = new LinkedHashMap<>();
                    envelope.put("result", result.text);
                    System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
                }
            } else if (!isBlank(result.text)) {
                System.out.println(result.text);
            }

            return 0;
        }
    }

    @Command(
        name = "list",
        description = "List AI providers/endpoints configured in Lucee server CFConfig",
        mixinStandardHelpOptions = true
    )
    static class ListCommand implements Callable<Integer> {

        @Option(names = "--name", description = "Filter by endpoint/provider name (repeatable)")
        private List<String> endpointNames = new ArrayList<>();
        @Option(names = "--refresh", description = "Compatibility flag (currently no-op)")
        private boolean refresh;

        @Option(names = "--json", description = "Output as JSON")
        private boolean json;

        @Option(names = "--show", description = "Show full secretKey values (otherwise masked)")
        private boolean showSecrets;

        @Override
        public Integer call() throws Exception {
            Path cfConfigFile = luceeCfConfigFile();
            if (!Files.exists(cfConfigFile) || !Files.isRegularFile(cfConfigFile)) {
                if (json) {
                    System.out.println("{}");
                } else {
                    StringOutput.Quick.info("No Lucee CFConfig file found: " + cfConfigFile);
                }
                return 0;
            }
            AiLocalConfig config = loadAiLocalConfig();
            ObjectNode allEntries = readLuceeAiEntries(cfConfigFile);
            LinkedHashSet<String> requested = normalizeNames(endpointNames);
            ObjectNode result = requested.isEmpty()
                ? allEntries
                : filterAiEntries(allEntries, requested);
            if (refresh && (LuCLI.verbose || LuCLI.debug)) {
                StringOutput.Quick.warning("--refresh is currently ignored because Lucee AIGetMetaData does not support a refresh argument.");
            }
            if (json) {
                JsonNode output = redactedCopy(result, showSecrets);
                System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));
                return 0;
            }
            if (!requested.isEmpty()) {
                for (String name : requested) {
                    if (!allEntries.has(name)) {
                        StringOutput.Quick.warning("AI provider not found: " + name);
                    }
                }
            }
            if (result.isEmpty()) {
                StringOutput.Quick.info("No AI providers configured.");
                return 0;
            }
            printAiConfigEntries(result, config.defaultEndpoint, showSecrets);
            return 0;
        }
    }

    @Command(
        name = "test",
        description = "Run a lightweight prompt against a Lucee-managed endpoint",
        mixinStandardHelpOptions = true
    )
    static class TestCommand implements Callable<Integer> {

        @Option(names = "--endpoint", description = "Endpoint name")
        private String endpointName;

        @Option(names = "--text", description = "Test prompt text", defaultValue = "Respond with the word: pong")
        private String promptText;

        @Override
        public Integer call() throws Exception {
            AiLocalConfig config = loadAiLocalConfig();
            String endpoint = firstNonBlank(endpointName, config.defaultEndpoint);
            if (isBlank(endpoint)) {
                StringOutput.Quick.error("No endpoint specified. Use --endpoint or set a default endpoint.");
                return 1;
            }

            PromptRequest request = new PromptRequest();
            request.endpoint = endpoint;
            request.text = firstNonBlank(promptText, "Respond with the word: pong");
            request.files = new ArrayList<>();
            PromptResult result;
            try {
                result = executePrompt(request);
            } catch (Exception e) {
                return printFriendlyAiFailure("AI endpoint test failed", endpoint, e);
            }
            StringOutput.Quick.success("AI endpoint test completed for '" + endpoint + "'.");
            if (!isBlank(result.text)) {
                System.out.println(result.text);
            }
            return 0;
        }
    }

    @Command(
        name = "skill",
        description = "Manage AI skills and skill search paths",
        mixinStandardHelpOptions = true,
        subcommands = {
            SkillCommand.PathCommand.class,
            SkillCommand.ListSkillsCommand.class,
            CommandLine.HelpCommand.class
        }
    )
    static class SkillCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            new CommandLine(this).usage(System.out);
            return 0;
        }

        @Command(
            name = "path",
            description = "Manage global skill search paths",
            subcommands = {
                PathCommand.AddPathCommand.class,
                PathCommand.RemovePathCommand.class,
                PathCommand.ListPathsCommand.class,
                CommandLine.HelpCommand.class
            }
        )
        static class PathCommand implements Callable<Integer> {
            @Override
            public Integer call() throws Exception {
                new CommandLine(this).usage(System.out);
                return 0;
            }

            @Command(name = "add", description = "Add a global skill path")
            static class AddPathCommand implements Callable<Integer> {
                @Parameters(index = "0", paramLabel = "<dir>", description = "Directory to add")
                private Path directory;

                @Override
                public Integer call() throws Exception {
                    Path normalized = normalizePath(directory);
                    if (!Files.isDirectory(normalized)) {
                        StringOutput.Quick.error("Skill path is not a directory: " + normalized);
                        return 1;
                    }

                    SkillPathsConfig config = loadSkillPathsConfig();
                    List<String> paths = config.paths == null ? new ArrayList<>() : new ArrayList<>(config.paths);
                    String value = normalized.toString();
                    if (!paths.contains(value)) {
                        paths.add(value);
                    }
                    config.paths = paths;
                    saveSkillPathsConfig(config);
                    StringOutput.Quick.success("Added skill path: " + value);
                    return 0;
                }
            }

            @Command(name = "remove", description = "Remove a global skill path")
            static class RemovePathCommand implements Callable<Integer> {
                @Parameters(index = "0", paramLabel = "<dir>", description = "Directory to remove")
                private Path directory;

                @Override
                public Integer call() throws Exception {
                    Path normalized = normalizePath(directory);
                    SkillPathsConfig config = loadSkillPathsConfig();
                    List<String> paths = config.paths == null ? new ArrayList<>() : new ArrayList<>(config.paths);
                    boolean removed = paths.remove(normalized.toString());
                    config.paths = paths;
                    saveSkillPathsConfig(config);
                    if (removed) {
                        StringOutput.Quick.success("Removed skill path: " + normalized);
                    } else {
                        StringOutput.Quick.warning("Skill path not found: " + normalized);
                    }
                    return 0;
                }
            }

            @Command(name = "list", description = "List global skill paths")
            static class ListPathsCommand implements Callable<Integer> {
                @Option(names = "--json", description = "Output as JSON")
                private boolean json;

                @Override
                public Integer call() throws Exception {
                    SkillPathsConfig config = loadSkillPathsConfig();
                    List<String> paths = config.paths == null ? new ArrayList<>() : config.paths;

                    if (json) {
                        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config));
                        return 0;
                    }

                    if (paths.isEmpty()) {
                        StringOutput.Quick.info("No global skill paths configured.");
                        return 0;
                    }
                    for (String p : paths) {
                        System.out.println(p);
                    }
                    return 0;
                }
            }
        }

        @Command(name = "list", description = "List discoverable skills across effective paths")
        static class ListSkillsCommand implements Callable<Integer> {

            @Option(names = "--skills-path", description = "Command-local skill path override (repeatable)")
            private List<Path> commandSkillPaths = new ArrayList<>();

            @Option(names = "--json", description = "Output as JSON")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                Map<String, SkillDefinition> skills = discoverSkillDefinitions(commandSkillPaths, LuCLI.verbose);
                if (skills.isEmpty()) {
                    StringOutput.Quick.info("No skills discovered.");
                    return 0;
                }

                if (json) {
                    System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(skills));
                    return 0;
                }

                for (Map.Entry<String, SkillDefinition> entry : skills.entrySet()) {
                    SkillDefinition skill = entry.getValue();
                    String source = isBlank(skill.source) ? "(unknown source)" : skill.source;
                    String line = entry.getKey() + "  [" + source + "]";
                    if (!isBlank(skill.model)) {
                        line = line + " model=" + skill.model;
                    }
                    System.out.println(line);
                }
                return 0;
            }
        }
    }

    // ---------- Lucee execution helpers ----------

    private static PromptResult executePrompt(PromptRequest request) throws Exception {
        String payload = MAPPER.writeValueAsString(request);
        String script = buildPromptScript(payload);

        LuceeScriptEngine engine = LuceeScriptEngine.getInstance();
        evalPromptScriptWithFilteredStderr(engine, script);

        Object text = engine.getEngine().get("__lucliAiResultText");
        Object json = engine.getEngine().get("__lucliAiResultJson");

        PromptResult result = new PromptResult();
        result.text = text != null ? String.valueOf(text) : "";
        result.json = json != null ? String.valueOf(json) : "";
        return result;
    }

    private static void evalPromptScriptWithFilteredStderr(LuceeScriptEngine engine, String script) throws Exception {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        Exception evalError = null;

        try (PrintStream capture = new PrintStream(capturedErr, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            engine.eval(script);
        } catch (Exception e) {
            evalError = e;
        } finally {
            System.setErr(originalErr);
        }

        flushFilteredPromptStderr(capturedErr.toString(StandardCharsets.UTF_8), originalErr);

        if (evalError != null) {
            throw evalError;
        }
    }

    private static void flushFilteredPromptStderr(String captured, PrintStream originalErr) {
        if (isBlank(captured)) {
            return;
        }

        String[] lines = captured.split("\\R");
        for (String line : lines) {
            if (isBlank(line)) {
                continue;
            }

            if (line.contains(RESPONSE_PROCESS_COOKIES_MARKER)) {
                if (LuCLI.verbose || LuCLI.debug) {
                    LuCLI.verbose("⚠️ HTTP cookie warning (suppressed from normal output): %s", line.trim());
                }
                continue;
            }

            originalErr.println(line);
        }
    }

    private static PromptResult executeConfigAdd(AddConfigRequest request) throws Exception {
        String payload = MAPPER.writeValueAsString(request);
        String escapedPayload = escapeForSingleQuotedCfml(payload);

        String script = """
payload = deserializeJSON('%s');

entry = {
    "class": payload.className,
    "custom": {
        "message": payload.message,
        "secretKey": payload.secretKey,
        "model": payload.model,
        "type": payload.type,
        "timeout": payload.timeout
    },
    "default": payload.defaultMode
};

if (!len(trim(payload.url ?: ""))) {
    structDelete(entry.custom, "url", false);
} else {
    entry.custom["url"] = payload.url;
}
if (!len(trim(payload.message ?: ""))) structDelete(entry.custom, "message", false);
if (!len(trim(payload.secretKey ?: ""))) structDelete(entry.custom, "secretKey", false);
if (!len(trim(payload.model ?: ""))) structDelete(entry.custom, "model", false);
if (!len(trim(payload.type ?: ""))) structDelete(entry.custom, "type", false);
if (isNull(payload.timeout)) structDelete(entry.custom, "timeout", false);
if (!len(trim(payload.defaultMode ?: ""))) structDelete(entry, "default", false);

cfg = {"ai": {}};
cfg.ai[payload.name] = entry;

configImport(
    data=cfg,
    password=request.SERVERADMINPASSWORD,
    type="server",
    flushExistingData=false
);

__lucliAiResultJson = serializeJSON(cfg);
__lucliAiResultText = __lucliAiResultJson;
""".formatted(escapedPayload);

        LuceeScriptEngine engine = LuceeScriptEngine.getInstance();
        engine.eval(script);

        Object text = engine.getEngine().get("__lucliAiResultText");
        Object json = engine.getEngine().get("__lucliAiResultJson");

        PromptResult result = new PromptResult();
        result.text = text != null ? String.valueOf(text) : "";
        result.json = json != null ? String.valueOf(json) : "";
        return result;
    }


    private static String buildPromptScript(String payloadJson) {
        String escapedPayload = escapeForSingleQuotedCfml(payloadJson);
        return """
payload = deserializeJSON('%s');

aiSession = createAISession(name=payload.endpoint);
questionText = payload.text ?: "";
if (structKeyExists(payload, "system") && len(trim(payload.system))) {
    if (len(trim(questionText))) {
        questionText = "[Instructions]\\n" & payload.system & "\\n\\n[Task]\\n" & questionText;
    } else {
        questionText = "[Instructions]\\n" & payload.system;
    }
}

question = "";
if (structKeyExists(payload, "files") && isArray(payload.files) && arrayLen(payload.files) > 0) {
    question = [];
    if (len(trim(questionText))) {
        arrayAppend(question, questionText);
    }
    for (filePath in payload.files) {
        arrayAppend(question, fileReadBinary(filePath));
    }
} else {
    question = questionText;
}

aiResponse = inquiryAISession(aiSession, question);

if (isSimpleValue(aiResponse)) {
    __lucliAiResultText = toString(aiResponse);
    __lucliAiResultJson = "";
} else {
    __lucliAiResultJson = serializeJSON(aiResponse);
    __lucliAiResultText = __lucliAiResultJson;
}
""".formatted(escapedPayload);
    }

    // ---------- File + skill resolution ----------

    private static List<Path> normalizeAndValidateImages(List<Path> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        List<Path> validated = new ArrayList<>();
        for (Path path : images) {
            Path normalized = normalizePath(path);
            if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
                throw new IllegalArgumentException("File not found: " + normalized);
            }
            if (!isImageFile(normalized)) {
                throw new IllegalArgumentException("Unsupported file type for first-pass multimodal support: " + normalized);
            }
            validated.add(normalized);
        }
        return validated;
    }

    private static List<Path> normalizeAndCollectRuleFiles(List<Path> rulesFiles, List<Path> rulesFolders) throws IOException {
        LinkedHashSet<Path> ordered = new LinkedHashSet<>();

        if (rulesFiles != null) {
            for (Path path : rulesFiles) {
                Path normalized = normalizePath(path);
                if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
                    throw new IllegalArgumentException("Rules file not found: " + normalized);
                }
                ordered.add(normalized);
            }
        }

        if (rulesFolders != null) {
            for (Path folder : rulesFolders) {
                Path normalizedFolder = normalizePath(folder);
                if (!Files.exists(normalizedFolder) || !Files.isDirectory(normalizedFolder)) {
                    throw new IllegalArgumentException("Rules folder not found: " + normalizedFolder);
                }

                try (Stream<Path> stream = Files.walk(normalizedFolder)) {
                    List<Path> discovered = stream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(Path::toString))
                        .collect(Collectors.toList());
                    ordered.addAll(discovered);
                }
            }
        }

        return new ArrayList<>(ordered);
    }

    private static String buildRulesInstructionBlock(List<Path> ruleFiles) throws IOException {
        if (ruleFiles == null || ruleFiles.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        int included = 0;

        for (Path path : ruleFiles) {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (isBlank(content)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("Rule source: ").append(path).append("\n");
            builder.append(content.trim());
            included++;
        }

        if (included == 0) {
            return null;
        }

        return "Apply the following rules when producing your answer:\n\n" + builder;
    }

    private static String mergeInstructionBlocks(String primary, String secondary) {
        if (isBlank(primary)) {
            return secondary;
        }
        if (isBlank(secondary)) {
            return primary;
        }
        return primary.trim() + "\n\n" + secondary.trim();
    }

    private static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static SkillDefinition resolveSkill(String skillNameOrPath, List<Path> commandSkillPaths, boolean verbose) throws Exception {
        if (isBlank(skillNameOrPath)) {
            return null;
        }

        Path directPath = tryAsPath(skillNameOrPath);
        if (directPath != null && Files.isRegularFile(directPath)) {
            SkillDefinition direct = loadSkillFromFile(directPath);
            direct.source = directPath.toString();
            if (isBlank(direct.name)) {
                direct.name = stripExtension(directPath.getFileName().toString());
            }
            return direct;
        }

        String requestedName = skillNameOrPath.trim();
        SkillDefinition winner = null;

        for (Path dir : buildEffectiveSkillPathOrder(commandSkillPaths)) {
            Path candidate = dir.resolve(requestedName + ".json");
            if (Files.isRegularFile(candidate)) {
                SkillDefinition current = loadSkillFromFile(candidate);
                current.source = candidate.toString();
                if (isBlank(current.name)) {
                    current.name = stripExtension(candidate.getFileName().toString());
                }
                if (winner == null) {
                    winner = current;
                } else if (verbose) {
                    StringOutput.Quick.warning("Skill '" + requestedName + "' shadowed by earlier path. Ignoring: " + candidate);
                }
            }
        }

        if (winner != null) {
            return winner;
        }

        SkillsConfig namedSkills = loadSkillsConfig();
        if (namedSkills.skills != null && namedSkills.skills.containsKey(requestedName)) {
            SkillDefinition fromNamed = namedSkills.skills.get(requestedName);
            fromNamed.name = requestedName;
            fromNamed.source = LucliPaths.resolve().aiSkillsFile().toString();
            return fromNamed;
        }

        return null;
    }

    private static Map<String, SkillDefinition> discoverSkillDefinitions(List<Path> commandSkillPaths, boolean verbose) throws Exception {
        Map<String, SkillDefinition> discovered = new LinkedHashMap<>();

        for (Path dir : buildEffectiveSkillPathOrder(commandSkillPaths)) {
            if (!Files.isDirectory(dir)) {
                continue;
            }

            List<Path> jsonFiles;
            try (Stream<Path> stream = Files.list(dir)) {
                jsonFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
            }

            for (Path file : jsonFiles) {
                SkillDefinition skill = loadSkillFromFile(file);
                if (isBlank(skill.name)) {
                    skill.name = stripExtension(file.getFileName().toString());
                }
                skill.source = file.toString();

                if (discovered.containsKey(skill.name)) {
                    if (verbose) {
                        StringOutput.Quick.warning("Skill '" + skill.name + "' shadowed by earlier path. Ignoring: " + file);
                    }
                    continue;
                }
                discovered.put(skill.name, skill);
            }
        }

        SkillsConfig namedSkills = loadSkillsConfig();
        if (namedSkills.skills != null) {
            for (Map.Entry<String, SkillDefinition> entry : namedSkills.skills.entrySet()) {
                if (discovered.containsKey(entry.getKey())) {
                    if (verbose) {
                        StringOutput.Quick.warning("Skill '" + entry.getKey() + "' from skills.json is shadowed by path-based skill.");
                    }
                    continue;
                }
                SkillDefinition copy = entry.getValue();
                copy.name = entry.getKey();
                copy.source = LucliPaths.resolve().aiSkillsFile().toString();
                discovered.put(entry.getKey(), copy);
            }
        }

        return discovered;
    }

    private static List<Path> buildEffectiveSkillPathOrder(List<Path> commandSkillPaths) throws Exception {
        LinkedHashSet<Path> ordered = new LinkedHashSet<>();

        if (commandSkillPaths != null) {
            for (Path path : commandSkillPaths) {
                Path normalized = normalizePath(path);
                if (Files.isDirectory(normalized)) {
                    ordered.add(normalized);
                }
            }
        }

        Path projectDefault = normalizePath(Paths.get(System.getProperty("user.dir"), ".lucli", "skills"));
        if (Files.isDirectory(projectDefault)) {
            ordered.add(projectDefault);
        }

        SkillPathsConfig global = loadSkillPathsConfig();
        if (global.paths != null) {
            for (String value : global.paths) {
                if (isBlank(value)) {
                    continue;
                }
                Path normalized = normalizePath(Paths.get(value));
                if (Files.isDirectory(normalized)) {
                    ordered.add(normalized);
                }
            }
        }

        return new ArrayList<>(ordered);
    }

    private static SkillDefinition loadSkillFromFile(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), SkillDefinition.class);
    }

    // ---------- Local persistence ----------

    private static AiLocalConfig loadAiLocalConfig() throws Exception {
        Path settingsFile = LucliPaths.resolve().aiSettingsFile();
        if (Files.exists(settingsFile)) {
            return MAPPER.readValue(settingsFile.toFile(), AiLocalConfig.class);
        }

        // Backward compatibility for earlier first-pass naming.
        Path legacyFile = LucliPaths.resolve().aiDir().resolve("endpoints.json");
        if (Files.exists(legacyFile)) {
            return MAPPER.readValue(legacyFile.toFile(), AiLocalConfig.class);
        }

        return new AiLocalConfig();
    }

    private static void saveAiLocalConfig(AiLocalConfig config) throws Exception {
        Path file = LucliPaths.resolve().aiSettingsFile();
        ensureParentDirectory(file);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), config);
    }

    private static SkillPathsConfig loadSkillPathsConfig() throws Exception {
        Path file = LucliPaths.resolve().aiSkillPathsFile();
        if (!Files.exists(file)) {
            return new SkillPathsConfig();
        }
        SkillPathsConfig config = MAPPER.readValue(file.toFile(), SkillPathsConfig.class);
        if (config.paths == null) {
            config.paths = new ArrayList<>();
        }
        return config;
    }

    private static void saveSkillPathsConfig(SkillPathsConfig config) throws Exception {
        Path file = LucliPaths.resolve().aiSkillPathsFile();
        ensureParentDirectory(file);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), config);
    }

    private static SkillsConfig loadSkillsConfig() throws Exception {
        Path file = LucliPaths.resolve().aiSkillsFile();
        if (!Files.exists(file)) {
            return new SkillsConfig();
        }
        SkillsConfig config = MAPPER.readValue(file.toFile(), SkillsConfig.class);
        if (config.skills == null) {
            config.skills = new LinkedHashMap<>();
        }
        return config;
    }

    private static void ensureParentDirectory(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
    private static Path luceeCfConfigFile() {
        return LucliPaths.resolve().home()
            .resolve("lucee-server")
            .resolve("lucee-server")
            .resolve("context")
            .resolve(".CFConfig.json");
    }

    private static ObjectNode readLuceeAiEntries(Path cfConfigFile) throws IOException {
        JsonNode root = MAPPER.readTree(cfConfigFile.toFile());
        JsonNode ai = root == null ? null : root.get("ai");
        if (ai == null || ai.isNull() || !ai.isObject()) {
            return MAPPER.createObjectNode();
        }
        return ((ObjectNode) ai).deepCopy();
    }

    private static ObjectNode filterAiEntries(ObjectNode allEntries, Set<String> names) {
        ObjectNode filtered = MAPPER.createObjectNode();
        for (String name : names) {
            JsonNode value = allEntries.get(name);
            if (value != null) {
                filtered.set(name, value);
            }
        }
        return filtered;
    }

    private static LinkedHashSet<String> normalizeNames(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                normalized.add(value.trim());
            }
        }
        return normalized;
    }

    // ---------- Misc helpers ----------
    private static void printAiConfigEntries(ObjectNode entries) {
        printAiConfigEntries(entries, null, false);
    }

    private static void printAiConfigEntries(ObjectNode entries, String defaultEndpoint) {
        printAiConfigEntries(entries, defaultEndpoint, false);
    }

    private static void printAiConfigEntries(ObjectNode entries, String defaultEndpoint, boolean showSecrets) {
        java.util.Iterator<Map.Entry<String, JsonNode>> iterator = entries.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            JsonNode custom = value.path("custom");

            String className = nodeText(value, "class");
            String type = nodeText(custom, "type");
            String model = nodeText(custom, "model");
            String secretKey = nodeText(custom, "secretKey");
            String defaultMode = nodeText(value, "default");

            String label = (!isBlank(defaultEndpoint) && name.equals(defaultEndpoint))
                ? name + " (default)"
                : name;
            System.out.println(label);
            System.out.println("  class: " + fallback(className));
            System.out.println("  type: " + fallback(type));
            System.out.println("  model: " + fallback(model));
            System.out.println("  secretKey: " + formatSecretValue(secretKey, showSecrets));
            System.out.println("  default: " + fallback(defaultMode));
        }
    }

    private static String nodeText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static String fallback(String value) {
        return isBlank(value) ? "(unset)" : value;
    }

    private static String formatSecretValue(String value, boolean showSecrets) {
        if (isBlank(value)) {
            return "(unset)";
        }
        return showSecrets ? value : maskSecretValue(value);
    }

    private static String maskSecretValue(String value) {
        if (isBlank(value)) {
            return "(unset)";
        }
        String trimmed = value.trim();
        int length = trimmed.length();
        if (length <= 2) {
            return "*".repeat(length);
        }
        if (length <= 6) {
            return trimmed.substring(0, 1) + "..." + trimmed.substring(length - 1);
        }
        int visiblePrefix = Math.min(4, Math.max(1, length / 4));
        int visibleSuffix = Math.min(4, Math.max(1, length / 4));
        if (visiblePrefix + visibleSuffix >= length) {
            visiblePrefix = 1;
            visibleSuffix = 1;
        }
        return trimmed.substring(0, visiblePrefix) + "..." + trimmed.substring(length - visibleSuffix);
    }

    private static JsonNode redactedCopy(JsonNode node, boolean showSecrets) {
        if (showSecrets || node == null) {
            return node;
        }
        JsonNode copy = node.deepCopy();
        redactSecretKeysInPlace(copy);
        return copy;
    }

    private static String redactSecretKeysInJsonString(String rawJson, boolean showSecrets) {
        if (showSecrets || isBlank(rawJson)) {
            return rawJson;
        }
        try {
            JsonNode root = MAPPER.readTree(rawJson);
            JsonNode redacted = redactedCopy(root, false);
            return MAPPER.writeValueAsString(redacted);
        } catch (Exception e) {
            return rawJson;
        }
    }

    private static void redactSecretKeysInPlace(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode value = field.getValue();
                if ("secretKey".equalsIgnoreCase(fieldName) && value != null && value.isTextual()) {
                    objectNode.put(fieldName, maskSecretValue(value.asText()));
                    continue;
                }
                if (value != null && (value.isObject() || value.isArray())) {
                    redactSecretKeysInPlace(value);
                }
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                redactSecretKeysInPlace(child);
            }
        }
    }

    private static int printFriendlyAiFailure(String action, String endpoint, Exception error) {
        String details = gatherExceptionMessages(error);
        String lower = details.toLowerCase(Locale.ROOT);
        String endpointSuffix = isBlank(endpoint) ? "" : " for '" + endpoint + "'";

        if (lower.contains("insufficient_quota") || lower.contains("exceeded your current quota")) {
            StringOutput.Quick.error(action + endpointSuffix + ": provider quota exceeded (HTTP 429 insufficient_quota). Check your billing and usage limits.");
        } else if (lower.contains("status-code:429") || lower.contains("rate limit")) {
            StringOutput.Quick.error(action + endpointSuffix + ": provider rate limit reached (HTTP 429). Try again shortly.");
        } else if (lower.contains("status-code:401") || lower.contains("invalid_api_key") || lower.contains("unauthorized")) {
            StringOutput.Quick.error(action + endpointSuffix + ": authentication failed (HTTP 401). Check your API key/secret.");
        } else if (lower.contains("status-code:403") || lower.contains("forbidden")) {
            StringOutput.Quick.error(action + endpointSuffix + ": request forbidden (HTTP 403). Check provider permissions.");
        } else {
            StringOutput.Quick.error(action + endpointSuffix + ": " + truncate(details, 260));
        }

        if ((LuCLI.verbose || LuCLI.debug) && !isBlank(details)) {
            StringOutput.Quick.info("Provider error details: " + truncate(details, 600));
        }
        return 1;
    }

    private static String gatherExceptionMessages(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        StringBuilder combined = new StringBuilder();
        Set<Throwable> seen = new HashSet<>();
        Throwable current = throwable;

        while (current != null && !seen.contains(current)) {
            seen.add(current);
            String message = normalizeWhitespace(current.getMessage());
            if (isBlank(message)) {
                message = current.getClass().getSimpleName();
            }
            if (!isBlank(message)) {
                if (!combined.isEmpty()) {
                    combined.append(" | ");
                }
                combined.append(message);
            }
            current = current.getCause();
        }

        return normalizeWhitespace(combined.toString());
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, Math.max(0, maxLength));
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private static void printAiDefaults(AiLocalConfig config) {
        System.out.println("AI defaults:");
        System.out.println("  defaultEndpoint: " + (isBlank(config.defaultEndpoint) ? "(unset)" : config.defaultEndpoint));
        System.out.println("  defaultModel: " + (isBlank(config.defaultModel) ? "(unset)" : config.defaultModel));
    }

    private static Path normalizePath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path is required.");
        }
        return path.toAbsolutePath().normalize();
    }

    private static Path tryAsPath(String value) {
        try {
            return normalizePath(Paths.get(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static String readSystemInstruction(String value) throws IOException {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("@")) {
            return trimmed;
        }
        Path file = normalizePath(Paths.get(trimmed.substring(1)));
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("System instruction file not found: " + file);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static String prettyJsonOrRaw(String rawJson) {
        if (isBlank(rawJson)) {
            return "";
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(rawJson));
        } catch (Exception e) {
            return rawJson;
        }
    }

    private static String escapeForSingleQuotedCfml(String value) {
        return value
            .replace("#", "##")
            .replace("'", "''");
    }

    private static String stripExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx <= 0) {
            return filename;
        }
        return filename.substring(0, idx);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    // ---------- DTOs ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiLocalConfig {
        public String defaultEndpoint;
        public String defaultModel;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SkillPathsConfig {
        public List<String> paths = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SkillsConfig {
        public Map<String, SkillDefinition> skills = new LinkedHashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SkillDefinition {
        public String name;
        public String source;
        public String system;
        public String text;
        public String model;
        public Double temperature;
        public Integer timeoutMillis;
    }

    static class AddConfigRequest {
        public String name;
        public String className;
        public String type;
        public String url;
        public String secretKey;
        public String model;
        public String message;
        public Integer timeout;
        public String defaultMode;
    }

    static class PromptRequest {
        public String endpoint;
        public String model;
        public String system;
        public String text;
        public Double temperature;
        public Integer timeoutMillis;
        public List<String> files = new ArrayList<>();
    }


    static class PromptResult {
        public String text;
        public String json;
    }

    static class ProviderDefaults {
        final String className;
        final String model;
        final String url;

        ProviderDefaults(String className, String model, String url) {
            this.className = className;
            this.model = model;
            this.url = url;
        }
    }
}
