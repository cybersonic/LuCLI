package org.lucee.lucli.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.Settings;
import org.lucee.lucli.secrets.LocalSecretStore;
import org.lucee.lucli.secrets.SecretStore;
import org.lucee.lucli.secrets.SecretStoreException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Resolves runtime env and secret inputs for module execution.
 */
public class ModuleRuntimeConfigResolver {

    private static final String MODULE_ENV_FILE = ".env.lucli";
    private static final String DOT_ENV_FILE = ".env";
    private static final Pattern HASH_SECRET_PATTERN = Pattern.compile("#secret:([^#]+)#");

    private final boolean strictMode;
    private final boolean allowDotEnvFallback;
    private final char[] passphraseOverride;

    private SecretStore secretStore;

    public ModuleRuntimeConfigResolver(boolean strictMode, boolean allowDotEnvFallback) {
        this(strictMode, allowDotEnvFallback, null);
    }

    ModuleRuntimeConfigResolver(boolean strictMode, boolean allowDotEnvFallback, char[] passphraseOverride) {
        this.strictMode = strictMode;
        this.allowDotEnvFallback = allowDotEnvFallback;
        this.passphraseOverride = passphraseOverride;
    }

    public static ModuleRuntimeConfigResolver fromSettings() {
        Settings settings = new Settings();
        JsonNode strictNode = settings.getNestedSetting("moduleRuntime", "strictEnv");
        JsonNode fallbackNode = settings.getNestedSetting("moduleRuntime", "allowDotEnvFallback");

        boolean strict = strictNode != null && !strictNode.isMissingNode() ? strictNode.asBoolean(false) : false;
        boolean allowFallback = fallbackNode != null && !fallbackNode.isMissingNode() ? fallbackNode.asBoolean(false) : false;

        // Env vars/system props can override settings for one-off runs.
        String strictOverride = System.getProperty("lucli.modules.strictEnv");
        if (strictOverride == null || strictOverride.isBlank()) {
            strictOverride = System.getenv("LUCLI_MODULES_STRICT_ENV");
        }
        if (strictOverride != null && !strictOverride.isBlank()) {
            strict = Boolean.parseBoolean(strictOverride);
        }

        String fallbackOverride = System.getProperty("lucli.modules.allowDotEnvFallback");
        if (fallbackOverride == null || fallbackOverride.isBlank()) {
            fallbackOverride = System.getenv("LUCLI_MODULES_ALLOW_DOTENV_FALLBACK");
        }
        if (fallbackOverride != null && !fallbackOverride.isBlank()) {
            allowFallback = Boolean.parseBoolean(fallbackOverride);
        }

        return new ModuleRuntimeConfigResolver(strict, allowFallback);
    }

    public ResolutionResult resolve(String moduleName, ModuleConfig moduleConfig, Path projectDir) throws Exception {
        Path moduleEnvPath = projectDir.resolve(MODULE_ENV_FILE);
        Path dotEnvPath = projectDir.resolve(DOT_ENV_FILE);

        Map<String, String> moduleEnvMap = Files.exists(moduleEnvPath)
            ? LuCLI.loadEnvFileToMap(moduleEnvPath)
            : new HashMap<>();
        Map<String, String> dotEnvMap = allowDotEnvFallback && Files.exists(dotEnvPath)
            ? LuCLI.loadEnvFileToMap(dotEnvPath)
            : new HashMap<>();

        Map<String, String> scriptEnv = new HashMap<>(LuCLI.scriptEnvironment);
        Map<String, String> osEnv = System.getenv();

        Map<String, String> resolvedEnv = new LinkedHashMap<>();
        Map<String, String> resolvedSecrets = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (ModuleConfig.PermissionRequirement req : moduleConfig.getEnvPermissions()) {
            String value = resolveAlias(req.getAlias(), moduleEnvMap, dotEnvMap, scriptEnv, osEnv);
            if (value != null && value.contains("#secret:")) {
                value = resolveSecretPlaceholders(value);
            }
            if ((value == null || value.isBlank()) && req.isRequired()) {
                throw new IllegalStateException("Missing required env permission '" + req.getAlias()
                    + "' for module '" + moduleName + "'");
            }
            if (value != null) {
                resolvedEnv.put(req.getAlias(), value);
            }
        }

        for (ModuleConfig.PermissionRequirement req : moduleConfig.getSecretPermissions()) {
            String binding = resolveAlias(req.getAlias(), moduleEnvMap, dotEnvMap, scriptEnv, osEnv);
            String value = null;
            if (binding != null && !binding.isBlank()) {
                if (binding.startsWith("#secret:")) {
                    value = resolveSecretPlaceholders(binding);
                } else if (binding.startsWith("secret:")) {
                    String secretName = binding.substring("secret:".length()).trim();
                    value = resolveSecretByName(secretName, req.isRequired());
                } else {
                    value = binding;
                }
            } else {
                value = resolveSecretByName(req.getAlias(), false);
            }

            if ((value == null || value.isBlank()) && req.isRequired()) {
                throw new IllegalStateException("Missing required secret permission '" + req.getAlias()
                    + "' for module '" + moduleName + "'");
            }
            if (value != null) {
                resolvedSecrets.put(req.getAlias(), value);
            }
        }

        Map<String, String> compatibilityEnv = new HashMap<>(scriptEnv);
        if (allowDotEnvFallback) {
            compatibilityEnv.putAll(dotEnvMap);
        }
        compatibilityEnv.putAll(moduleEnvMap);
        compatibilityEnv.putAll(resolvedEnv);
        compatibilityEnv.putAll(resolvedSecrets);

        Map<String, String> serverEnv = strictMode
            ? buildStrictServerEnv(resolvedEnv, resolvedSecrets)
            : compatibilityEnv;

        if (strictMode && !moduleConfig.hasDeclaredPermissions()) {
            warnings.add("Strict mode is enabled but module has no declared permissions; ambient env is not injected.");
        }

        Map<String, Object> runtimeContext = new HashMap<>();
        runtimeContext.put("moduleName", moduleName);
        runtimeContext.put("strictMode", strictMode);
        runtimeContext.put("allowDotEnvFallback", allowDotEnvFallback);
        runtimeContext.put("moduleEnvFile", Files.exists(moduleEnvPath) ? moduleEnvPath.toString() : "");
        runtimeContext.put("dotEnvFile", allowDotEnvFallback && Files.exists(dotEnvPath) ? dotEnvPath.toString() : "");

        return new ResolutionResult(serverEnv, resolvedEnv, resolvedSecrets, runtimeContext, warnings);
    }

    private String resolveAlias(String alias,
                                Map<String, String> moduleEnvMap,
                                Map<String, String> dotEnvMap,
                                Map<String, String> scriptEnv,
                                Map<String, String> osEnv) {
        if (scriptEnv.containsKey(alias) && scriptEnv.get(alias) != null && !scriptEnv.get(alias).isBlank()) {
            return scriptEnv.get(alias);
        }
        if (moduleEnvMap.containsKey(alias) && moduleEnvMap.get(alias) != null && !moduleEnvMap.get(alias).isBlank()) {
            return moduleEnvMap.get(alias);
        }
        if (allowDotEnvFallback && dotEnvMap.containsKey(alias) && dotEnvMap.get(alias) != null && !dotEnvMap.get(alias).isBlank()) {
            return dotEnvMap.get(alias);
        }
        if (osEnv.containsKey(alias) && osEnv.get(alias) != null && !osEnv.get(alias).isBlank()) {
            return osEnv.get(alias);
        }
        return null;
    }

    private String resolveSecretPlaceholders(String value) throws Exception {
        if (value == null) {
            return null;
        }
        Matcher matcher = HASH_SECRET_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String replacement = resolveSecretByName(name, true);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement != null ? replacement : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String resolveSecretByName(String name, boolean required) throws Exception {
        if (name == null || name.isBlank()) {
            return null;
        }
        SecretStore store = getSecretStore(required);
        if (store == null) {
            return null;
        }
        Optional<char[]> value = store.get(name);
        if (value.isEmpty()) {
            if (required) {
                throw new IllegalStateException("Secret '" + name + "' not found in local secret store");
            }
            return null;
        }
        return new String(value.get());
    }

    private SecretStore getSecretStore(boolean required) throws Exception {
        if (secretStore != null) {
            return secretStore;
        }

        Path storePath = getLucliHome().resolve("secrets").resolve("local.json");
        if (!Files.exists(storePath)) {
            if (required) {
                throw new IllegalStateException("Secret store not found at " + storePath
                    + ". Run 'lucli secrets init' to create it.");
            }
            return null;
        }

        char[] passphrase = passphraseOverride;
        if (passphrase == null || passphrase.length == 0) {
            String envPass = System.getenv("LUCLI_SECRETS_PASSPHRASE");
            if (envPass != null && !envPass.isEmpty()) {
                passphrase = envPass.toCharArray();
            } else if (System.console() != null) {
                passphrase = System.console().readPassword("Enter secrets passphrase to unlock module secrets: ");
            }
        }

        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalStateException("Module execution requires secrets but no passphrase is available. "
                + "Set LUCLI_SECRETS_PASSPHRASE or run in interactive mode.");
        }

        try {
            secretStore = new LocalSecretStore(storePath, passphrase);
            return secretStore;
        } catch (SecretStoreException e) {
            throw new IllegalStateException("Failed to open local secret store: " + e.getMessage(), e);
        }
    }

    private Map<String, String> buildStrictServerEnv(Map<String, String> resolvedEnv, Map<String, String> resolvedSecrets) {
        Map<String, String> strictEnv = new HashMap<>();
        strictEnv.putAll(resolvedEnv);
        strictEnv.putAll(resolvedSecrets);
        return strictEnv;
    }

    private Path getLucliHome() {
        String home = System.getProperty("lucli.home");
        if (home == null || home.isBlank()) {
            home = System.getenv("LUCLI_HOME");
        }
        if (home == null || home.isBlank()) {
            home = Paths.get(System.getProperty("user.home"), ".lucli").toString();
        }
        return Paths.get(home);
    }

    public static class ResolutionResult {
        private final Map<String, String> serverEnv;
        private final Map<String, String> envVars;
        private final Map<String, String> secrets;
        private final Map<String, Object> runtimeContext;
        private final List<String> warnings;

        ResolutionResult(Map<String, String> serverEnv,
                         Map<String, String> envVars,
                         Map<String, String> secrets,
                         Map<String, Object> runtimeContext,
                         List<String> warnings) {
            this.serverEnv = serverEnv;
            this.envVars = envVars;
            this.secrets = secrets;
            this.runtimeContext = runtimeContext;
            this.warnings = warnings;
        }

        public Map<String, String> getServerEnv() { return serverEnv; }
        public Map<String, String> getEnvVars() { return envVars; }
        public Map<String, String> getSecrets() { return secrets; }
        public Map<String, Object> getRuntimeContext() { return runtimeContext; }
        public List<String> getWarnings() { return warnings; }
    }
}
