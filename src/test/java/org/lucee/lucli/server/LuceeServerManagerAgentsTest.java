package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

// Explicit imports to ensure nested config/manager types resolve correctly
import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;

/**
 * Tests for JVM option assembly and agent activation logic
 * in LuceeServerManager (buildCatalinaOpts / resolveActiveAgents).
 */
public class LuceeServerManagerAgentsTest {

    private List<String> invokeBuildCatalinaOpts(LuceeServerConfig.ServerConfig config,
                                                LuceeServerManager.AgentOverrides overrides) throws Exception {
        LuceeServerManager manager = new LuceeServerManager();
        // Pass null projectDir for tests that don't need extension handling
        return manager.buildCatalinaOpts(config, overrides, null);
    }

    @SuppressWarnings("unchecked")
    private Set<String> invokeResolveActiveAgents(LuceeServerConfig.ServerConfig config,
                                                  LuceeServerManager.AgentOverrides overrides) throws Exception {
        LuceeServerManager manager = new LuceeServerManager();
        Method m = LuceeServerManager.class.getDeclaredMethod(
                "resolveActiveAgents",
                LuceeServerConfig.ServerConfig.class,
                LuceeServerManager.AgentOverrides.class);
        m.setAccessible(true);
        return (Set<String>) m.invoke(manager, config, overrides);
    }

    private LuceeServerConfig.ServerConfig baseConfig() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        // Make monitoring explicit for clarity
        config.monitoring.enabled = true;
        config.monitoring.jmx.port = 9000;
        config.jvm.maxMemory = "1024m";
        config.jvm.minMemory = "256m";
        config.jvm.additionalArgs = new String[] { "-Dfoo=bar" };
        config.agents = new HashMap<>();
        return config;
    }

    @Test
    void buildCatalinaOpts_noAgents_behavesLikeBefore() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();

        List<String> opts = invokeBuildCatalinaOpts(config, null);

        // Memory options
        assertTrue(opts.contains("-Xms256m"), "Should include -Xms from jvm.minMemory");
        assertTrue(opts.contains("-Xmx1024m"), "Should include -Xmx from jvm.maxMemory");

        // JMX options
        assertTrue(opts.contains("-Dcom.sun.management.jmxremote"), "Should include JMX system property");
        assertTrue(opts.contains("-Dcom.sun.management.jmxremote.port=9000"), "Should include JMX port");

        // Additional args should be present and come after any agent args (none in this case)
        int additionalIndex = opts.indexOf("-Dfoo=bar");
        assertTrue(additionalIndex >= 0, "Should include additional JVM args from config.jvm.additionalArgs");
    }

    @Test
    void buildCatalinaOpts_unsetMemory_omitsXmsAndXmx() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();
        config.jvm.maxMemory = null;
        config.jvm.minMemory = null;

        List<String> opts = invokeBuildCatalinaOpts(config, null);

        assertTrue(opts.stream().noneMatch(opt -> opt.startsWith("-Xms")),
                "Unset minMemory should not emit -Xms");
        assertTrue(opts.stream().noneMatch(opt -> opt.startsWith("-Xmx")),
                "Unset maxMemory should not emit -Xmx");
        assertTrue(opts.contains("-Dfoo=bar"),
                "additionalArgs should still be included when memory is unset");
    }

    @Test
    void buildCatalinaOpts_blankMemory_omitsXmsAndXmx() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();
        config.jvm.maxMemory = "   ";
        config.jvm.minMemory = "";

        List<String> opts = invokeBuildCatalinaOpts(config, null);

        assertTrue(opts.stream().noneMatch(opt -> opt.startsWith("-Xms")),
                "Blank minMemory should not emit -Xms");
        assertTrue(opts.stream().noneMatch(opt -> opt.startsWith("-Xmx")),
                "Blank maxMemory should not emit -Xmx");
    }

    @Test
    void buildCatalinaOpts_onlyMaxMemory_emitsXmxOnly() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();
        config.jvm.maxMemory = "4g";
        config.jvm.minMemory = null;

        List<String> opts = invokeBuildCatalinaOpts(config, null);

        assertTrue(opts.stream().noneMatch(opt -> opt.startsWith("-Xms")),
                "Unset minMemory should not emit -Xms");
        assertTrue(opts.contains("-Xmx4g"), "Set maxMemory should emit -Xmx");
    }

    @Test
    void buildCatalinaOpts_onlyMinMemory_emitsXmsOnly() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();
        config.jvm.maxMemory = null;
        config.jvm.minMemory = "256m";

        List<String> opts = invokeBuildCatalinaOpts(config, null);

        assertTrue(opts.contains("-Xms256m"), "Set minMemory should emit -Xms");
        assertTrue(opts.stream().noneMatch(opt -> opt.startsWith("-Xmx")),
                "Unset maxMemory should not emit -Xmx");
    }

    @Test
    void jvmConfig_defaultsAreUnset() {
        LuceeServerConfig.JvmConfig jvm = new LuceeServerConfig.JvmConfig();
        assertNull(jvm.maxMemory, "Default maxMemory should be unset so -Xmx is omitted");
        assertNull(jvm.minMemory, "Default minMemory should be unset so -Xms is omitted");
    }

    @Test
    void buildCatalinaOpts_enabledAgent_includedBeforeAdditionalArgs() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();

        // Configure a single enabled agent with one JVM arg
        LuceeServerConfig.AgentConfig agent = new LuceeServerConfig.AgentConfig();
        agent.enabled = true;
        agent.jvmArgs = new String[] { "-javaagent:/path/to/agent.jar" };
        config.agents.put("testAgent", agent);

        List<String> opts = invokeBuildCatalinaOpts(config, null);

        int agentIndex = opts.indexOf("-javaagent:/path/to/agent.jar");
        int additionalIndex = opts.indexOf("-Dfoo=bar");

        assertTrue(agentIndex >= 0, "Agent JVM arg should be present when enabled in config");
        assertTrue(additionalIndex > agentIndex,
                "additionalArgs should be appended after agent jvmArgs");
    }

    @Test
    void resolveActiveAgents_includeAgentsOverridesConfigEnabled() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();

        LuceeServerConfig.AgentConfig a = new LuceeServerConfig.AgentConfig();
        a.enabled = true;
        config.agents.put("a", a);

        LuceeServerConfig.AgentConfig b = new LuceeServerConfig.AgentConfig();
        b.enabled = false;
        config.agents.put("b", b);

        LuceeServerManager.AgentOverrides overrides = new LuceeServerManager.AgentOverrides();
        overrides.includeAgents = Set.of("b");

        Set<String> active = invokeResolveActiveAgents(config, overrides);

        assertTrue(active.contains("b"), "includeAgents should activate agent 'b'");
        assertFalse(active.contains("a"), "includeAgents should ignore config-enabled agent 'a'");
    }

    @Test
    void resolveActiveAgents_disableAllAgentsClearsEnabled() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();

        LuceeServerConfig.AgentConfig a = new LuceeServerConfig.AgentConfig();
        a.enabled = true;
        config.agents.put("a", a);

        LuceeServerManager.AgentOverrides overrides = new LuceeServerManager.AgentOverrides();
        overrides.disableAllAgents = true;

        Set<String> active = invokeResolveActiveAgents(config, overrides);

        assertTrue(active.isEmpty(), "disableAllAgents should clear all active agents");
    }

    @Test
    void resolveActiveAgents_enableAndDisableAdjustBaseSet() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();

        LuceeServerConfig.AgentConfig a = new LuceeServerConfig.AgentConfig();
        a.enabled = true;
        config.agents.put("a", a);

        LuceeServerConfig.AgentConfig b = new LuceeServerConfig.AgentConfig();
        b.enabled = false;
        config.agents.put("b", b);

        LuceeServerManager.AgentOverrides overrides = new LuceeServerManager.AgentOverrides();
        overrides.enableAgents = Set.of("b");
        overrides.disableAgents = Set.of("a");

        Set<String> active = invokeResolveActiveAgents(config, overrides);

        assertFalse(active.contains("a"), "Agent 'a' should be disabled by overrides");
        assertTrue(active.contains("b"), "Agent 'b' should be enabled by overrides");
    }

    @Test
    void applyStartConfigOverrides_warmupSetsEnvVarAndJvmProperty() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();
        config.envVars.put("APP_ENV", "test");
        config.jvm.additionalArgs = new String[] {
                "-Dfoo=bar",
                "-Dlucee.enable.warmup=false"
        };

        LuceeServerManager.StartConfigOverrides overrides = new LuceeServerManager.StartConfigOverrides();
        overrides.enableWarmupOverride = Boolean.TRUE;

        LuceeServerManager.applyStartConfigOverrides(config, overrides);

        assertEquals("true", config.envVars.get("LUCEE_ENABLE_WARMUP"),
                "Warmup override should inject LUCEE_ENABLE_WARMUP=true");
        assertTrue(config.envVars.containsKey("APP_ENV"),
                "Warmup override should preserve existing env vars");

        List<String> args = List.of(config.jvm.additionalArgs);
        assertTrue(args.contains("-Dlucee.enable.warmup=true"),
                "Warmup override should add lucee.enable.warmup=true");
        assertFalse(args.contains("-Dlucee.enable.warmup=false"),
                "Warmup override should replace existing lucee.enable.warmup values");

        List<String> catalinaOpts = invokeBuildCatalinaOpts(config, null);
        assertTrue(catalinaOpts.contains("-Dlucee.enable.warmup=true"),
                "CATALINA_OPTS should include warmup system property after override");
    }
}
