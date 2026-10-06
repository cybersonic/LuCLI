package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The module shortcut ({@code lucli <module> ...} and an aliased binary) re-executes
 * {@code modules run <module> ...} on the root command, which resets the root's
 * options. Root flags the module must see are re-injected (#136).
 */
class ModuleShortcutArgsTest {

    @Test
    void noRootFlagsGiveThePlainModulesRunArgs() {
        assertEquals(
            List.of("modules", "run", "wheels", "start", "--dry-run"),
            LuCLI.moduleShortcutArgs("wheels", new String[] {"start", "--dry-run"}, false, false, null, null)
        );
    }

    @Test
    void envAndEnvFileAreReinjectedAtTheRootPosition() {
        assertEquals(
            List.of("--env=prod", "--envfile=/tmp/x.env", "modules", "run", "wheels", "start"),
            LuCLI.moduleShortcutArgs("wheels", new String[] {"start"}, false, false, "prod", "/tmp/x.env")
        );
    }

    @Test
    void verboseAndDebugStayAfterTheModuleArgs() {
        assertEquals(
            List.of("--env=staging", "modules", "run", "wheels", "doctor", "--verbose", "--debug"),
            LuCLI.moduleShortcutArgs("wheels", new String[] {"doctor"}, true, true, "staging", "")
        );
    }

    @Test
    void emptyEnvIsNotReinjected() {
        assertEquals(
            List.of("modules", "run", "mod"),
            LuCLI.moduleShortcutArgs("mod", new String[0], false, false, "", null)
        );
    }
}
