package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertFalse(versionInfo.contains("Build Timestamp: "));
        assertFalse(versionInfo.contains("Build Commit: "));
        assertFalse(versionInfo.contains("Build Branch: "));
        assertFalse(versionInfo.contains("Build JDK: "));
    }

    @Test
    void getVersionLongInfo_includesBuildMetadataLines() {
        String versionLongInfo = LuCLI.getVersionLongInfo(true);
        assertTrue(versionLongInfo.contains("Build Timestamp: "));
        assertTrue(versionLongInfo.contains("Build Commit: "));
        assertTrue(versionLongInfo.contains("Build Branch: "));
        assertTrue(versionLongInfo.contains("Build JDK: "));
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
