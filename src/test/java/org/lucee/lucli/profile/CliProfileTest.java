package org.lucee.lucli.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CliProfileTest {

    @Test
    void forBinaryName_returnsWheelsProfileForWheels() {
        CliProfile profile = CliProfile.forBinaryName("wheels");
        assertInstanceOf(WheelsProfile.class, profile);
        assertEquals("wheels", profile.name());
        assertEquals(".wheels", profile.homeDirName());
        assertEquals("wheels", profile.promptPrefix());
        assertEquals("Wheels", profile.displayName());
    }

    @Test
    void forBinaryName_returnsWheelsProfileCaseInsensitive() {
        CliProfile profile = CliProfile.forBinaryName("Wheels");
        assertInstanceOf(WheelsProfile.class, profile);
        assertEquals("wheels", profile.name());
    }

    @Test
    void forBinaryName_returnsMarkspressoProfile() {
        CliProfile profile = CliProfile.forBinaryName("markspresso");
        assertInstanceOf(MarkspressoProfile.class, profile);
        assertEquals("markspresso", profile.name());
        assertEquals(".markspresso", profile.homeDirName());
        assertEquals("markspresso", profile.promptPrefix());
        assertEquals("Markspresso", profile.displayName());
    }

    @Test
    void forBinaryName_returnsMarkspressoProfileCaseInsensitive() {
        CliProfile profile = CliProfile.forBinaryName("MarkSpresso");
        assertInstanceOf(MarkspressoProfile.class, profile);
        assertEquals("markspresso", profile.name());
    }

    @Test
    void forBinaryName_returnsDefaultProfileForLucli() {
        CliProfile profile = CliProfile.forBinaryName("lucli");
        assertInstanceOf(DefaultProfile.class, profile);
        assertEquals("lucli", profile.name());
        assertEquals(".lucli", profile.homeDirName());
        assertEquals("cfml", profile.promptPrefix());
        assertEquals("LuCLI", profile.displayName());
    }

    @Test
    void forBinaryName_returnsDefaultProfileForNull() {
        CliProfile profile = CliProfile.forBinaryName(null);
        assertInstanceOf(DefaultProfile.class, profile);
    }

    @Test
    void forBinaryName_returnsDefaultProfileForUnknown() {
        CliProfile profile = CliProfile.forBinaryName("someothertool");
        assertInstanceOf(DefaultProfile.class, profile);
    }

    @Test
    void forBinaryName_stripsPathFromBinaryName() {
        CliProfile profile = CliProfile.forBinaryName("/usr/local/bin/wheels");
        assertInstanceOf(WheelsProfile.class, profile);
    }

    @Test
    void forBinaryName_stripsShExtension() {
        CliProfile profile = CliProfile.forBinaryName("wheels.sh");
        assertInstanceOf(WheelsProfile.class, profile);
    }

    @Test
    void forBinaryName_stripsBatExtension() {
        CliProfile profile = CliProfile.forBinaryName("wheels.bat");
        assertInstanceOf(WheelsProfile.class, profile);
    }

    @Test
    void forBinaryName_stripsPathAndExtension() {
        CliProfile profile = CliProfile.forBinaryName("/opt/lucli/bin/wheels.sh");
        assertInstanceOf(WheelsProfile.class, profile);
    }

    @Test
    void forBinaryName_lucliShReturnsDefault() {
        CliProfile profile = CliProfile.forBinaryName("lucli.sh");
        assertInstanceOf(DefaultProfile.class, profile);
    }

    @Test
    void forBinaryName_stripsPathAndExtensionForMarkspresso() {
        CliProfile profile = CliProfile.forBinaryName("/opt/lucli/bin/markspresso.sh");
        assertInstanceOf(MarkspressoProfile.class, profile);
    }

    @Test
    void defaultProfile_bannerContainsLuCLI() {
        DefaultProfile profile = new DefaultProfile();
        // The banner should contain recognizable LuCLI ASCII art
        assertTrue(profile.bannerText().contains("___ "));
    }

    @Test
    void wheelsProfile_bannerContainsWheels() {
        WheelsProfile profile = new WheelsProfile();
        // The banner should contain recognizable Wheels ASCII art
        assertTrue(profile.bannerText().contains("\\__ \\"));
    }
}
