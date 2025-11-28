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
        return manager.buildCatalinaOpts(config, overrides);
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
}
