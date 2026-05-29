package org.lucee.lucli.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.lucee.lucli.config.DependencyConfig;

class DependencyShortcutRegistryTest {

    @Test
    void loadDefaultContainsExpectedShortcuts() {
        DependencyShortcutRegistry registry = DependencyShortcutRegistry.loadDefault();

        assertTrue(registry.listShortcuts().contains("testbox"));
        assertTrue(registry.listShortcuts().contains("fw1"));
    }

    @Test
    void resolveShortcutIsCaseInsensitiveAndReturnsConfiguredTemplate() {
        DependencyShortcutRegistry registry = DependencyShortcutRegistry.loadDefault();

        DependencyConfig dep = registry.resolveShortcut("TeStBoX");
        assertNotNull(dep);
        assertEquals("testbox", dep.getName());
        assertEquals("forgebox", dep.getSource());
        assertEquals("dependencies/testbox", dep.getInstallPath());
        assertEquals("/testbox", dep.getMapping());
    }

    @Test
    void resolveShortcutReturnsNullForUnknownShortcut() {
        DependencyShortcutRegistry registry = DependencyShortcutRegistry.loadDefault();
        assertNull(registry.resolveShortcut("unknown-shortcut"));
    }
}
