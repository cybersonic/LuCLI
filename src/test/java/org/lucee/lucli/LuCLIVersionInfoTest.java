package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LuCLIVersionInfoTest {

    @Test
    void getVersion_returnsNonBlankVersion() {
        String version = LuCLI.getVersion();
        assertFalse(version == null || version.trim().isEmpty());
    }

    @Test
    void getVersionInfo_includesBuildMetadataLines() {
        String versionInfo = LuCLI.getVersionInfo(false);

        assertTrue(versionInfo.contains("Java Version: "));
        assertTrue(versionInfo.contains("Build Timestamp: "));
        assertTrue(versionInfo.contains("Build Commit: "));
        assertTrue(versionInfo.contains("Build Branch: "));
        assertTrue(versionInfo.contains("Build JDK: "));
    }

    @Test
    void getCompactVersionInfo_returnsSingleLineVersion() {
        String compactVersion = LuCLI.getCompactVersionInfo();
        assertTrue(compactVersion.startsWith("LuCLI Version: "));
        assertEquals(1, compactVersion.split("\\R").length);
    }

    @Test
    void getBuildInfo_containsOnlyBuildMetadataLines() {
        String buildInfo = LuCLI.getBuildInfo();

        assertTrue(buildInfo.contains("Build Timestamp: "));
        assertTrue(buildInfo.contains("Build Commit: "));
        assertTrue(buildInfo.contains("Build Branch: "));
        assertTrue(buildInfo.contains("Build JDK: "));
        assertFalse(buildInfo.contains("Lucee Version: "));
        assertFalse(buildInfo.contains("Java Version: "));
    }
}
