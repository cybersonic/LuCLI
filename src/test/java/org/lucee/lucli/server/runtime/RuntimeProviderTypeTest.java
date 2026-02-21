package org.lucee.lucli.server.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.lucee.lucli.server.LuceeServerConfig;

/**
 * Tests for runtime provider type identifiers and runtime configuration
 * resolution. These guard against accidentally breaking the provider
 * selection logic or changing type strings that external tooling may
 * depend on.
 */
public class RuntimeProviderTypeTest {

    // ── Provider type identifiers ────────────────────────────────────────

    @Test
    void luceeExpressProvider_typeIsLuceeExpress() {
        assertEquals("lucee-express", new LuceeExpressRuntimeProvider().getType());
    }

    @Test
    void tomcatProvider_typeIsTomcat() {
        assertEquals("tomcat", new TomcatRuntimeProvider().getType());
    }

    @Test
    void dockerProvider_typeIsDocker() {
        assertEquals("docker", new DockerRuntimeProvider().getType());
    }

    // ── getEffectiveRuntime: defaults ────────────────────────────────────

    @Test
    void getEffectiveRuntime_defaultsToLuceeExpress() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = null;

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertEquals("lucee-express", rt.type);
    }

    @Test
    void getEffectiveRuntime_defaultsTypeWhenBlank() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "   ";

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertEquals("lucee-express", rt.type);
    }

    @Test
    void getEffectiveRuntime_preservesExplicitType() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "tomcat";

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertEquals("tomcat", rt.type);
    }

    @Test
    void getEffectiveRuntime_preservesDockerType() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "docker";

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertEquals("docker", rt.type);
    }

    @Test
    void getEffectiveRuntime_defaultsSharedToFalse() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.shared = null;

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertFalse(rt.shared);
    }

    @Test
    void getEffectiveRuntime_preservesSharedFlag() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.shared = true;

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertTrue(rt.shared);
    }

    @Test
    void getEffectiveRuntime_defaultsRunModeToMount() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.runMode = null;

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertEquals("mount", rt.runMode);
    }

    @Test
    void getEffectiveRuntime_throwsForNullConfig() {
        assertThrows(IllegalArgumentException.class, () ->
                LuceeServerConfig.getEffectiveRuntime(null));
    }

    @Test
    void getEffectiveRuntime_assignsBackToConfig() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = null;

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertSame(rt, config.runtime,
                "Runtime should be assigned back to config for downstream callers");
    }

    // ── getEffectiveRuntime: Tomcat-specific fields ─────────────────────

    @Test
    void getEffectiveRuntime_preservesCatalinaHome() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "tomcat";
        config.runtime.catalinaHome = "/opt/tomcat11";

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertEquals("/opt/tomcat11", rt.catalinaHome);
    }

    @Test
    void getEffectiveRuntime_preservesVariant() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.variant = "light";

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertEquals("light", rt.variant);
    }

    // ── getEffectiveRuntime: Docker-specific fields ─────────────────────

    @Test
    void getEffectiveRuntime_preservesDockerImage() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "docker";
        config.runtime.image = "lucee/lucee";
        config.runtime.tag = "6.2";

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertEquals("lucee/lucee", rt.image);
        assertEquals("6.2", rt.tag);
    }

    @Test
    void getEffectiveRuntime_preservesContainerName() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "docker";
        config.runtime.containerName = "my-lucee-app";

        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        assertEquals("my-lucee-app", rt.containerName);
    }

    // ── RuntimeProvider interface contract ───────────────────────────────

    @Test
    void allProviders_implementRuntimeProvider() {
        // Verify all providers implement the interface (compile-time check + instanceof)
        assertTrue(new LuceeExpressRuntimeProvider() instanceof RuntimeProvider);
        assertTrue(new TomcatRuntimeProvider() instanceof RuntimeProvider);
        assertTrue(new DockerRuntimeProvider() instanceof RuntimeProvider);
    }

    @Test
    void allProviders_haveDistinctTypes() {
        String expressType = new LuceeExpressRuntimeProvider().getType();
        String tomcatType = new TomcatRuntimeProvider().getType();
        String dockerType = new DockerRuntimeProvider().getType();

        assertNotEquals(expressType, tomcatType);
        assertNotEquals(expressType, dockerType);
        assertNotEquals(tomcatType, dockerType);
    }
}
