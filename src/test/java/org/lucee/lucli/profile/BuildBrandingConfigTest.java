package org.lucee.lucli.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class BuildBrandingConfigTest {

    @Test
    void fromProperties_buildsProfileWithConfiguredValues() {
        Properties props = new Properties();
        props.setProperty("branding.enabled", "true");
        props.setProperty("branding.binaryName", "markspresso.sh");
        props.setProperty("branding.profileName", "markspresso");
        props.setProperty("branding.displayName", "Markspresso");
        props.setProperty("branding.promptPrefix", "mark");
        props.setProperty("branding.homeDirName", ".markspresso");
        props.setProperty("branding.backupsDirName", ".markspresso_bak");
        props.setProperty("branding.bannerText", "Line 1\\nPowered by LuCLI Version: ${LUCLI_VERSION}");
        props.setProperty("branding.productVersion", "0.6");

        BuildBrandingConfig config = BuildBrandingConfig.fromProperties(props);
        CliProfile profile = config.toProfileForTests();

        assertEquals("markspresso", profile.name());
        assertEquals("Markspresso", profile.displayName());
        assertEquals("mark", profile.promptPrefix());
        assertEquals(".markspresso", profile.homeDirName());
        assertEquals(".markspresso_bak", profile.backupsDirName());
        assertTrue(profile.bannerText().contains("Line 1"));
        assertTrue(profile.bannerText().contains("Powered by LuCLI Version:"));
        assertFalse(profile.bannerText().contains("${LUCLI_VERSION}"));
        assertTrue(profile.bannerText().contains("\n"));
        assertEquals("0.6", profile.productVersionOverride());
    }

    @Test
    void fromProperties_usesSafeDefaultsWhenOptionalValuesMissing() {
        Properties props = new Properties();
        props.setProperty("branding.enabled", "true");
        props.setProperty("branding.binaryName", "bitbucket");

        BuildBrandingConfig config = BuildBrandingConfig.fromProperties(props);
        CliProfile profile = config.toProfileForTests();

        assertEquals("bitbucket", profile.name());
        assertEquals("Bitbucket", profile.displayName());
        assertEquals("bitbucket", profile.promptPrefix());
        assertEquals(".bitbucket", profile.homeDirName());
        assertEquals(".bitbucket_backups", profile.backupsDirName());
        assertNull(profile.productVersionOverride());
    }
}
