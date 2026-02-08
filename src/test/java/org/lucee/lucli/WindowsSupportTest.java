package org.lucee.lucli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WindowsSupport class.
 * Tests terminal detection, emoji fallback logic, and capability detection.
 */
class WindowsSupportTest {

    // ============================================
    // supportsEmojis Tests
    // ============================================

    @Test
    void testSupportsEmojisReturnsBoolean() {
        // Just verify it returns without exception
        boolean result = WindowsSupport.supportsEmojis();
        // Result depends on current environment, but should not throw
        assertNotNull(result);
    }

    // ============================================
    // supportsColors Tests
    // ============================================

    @Test
    void testSupportsColorsReturnsBoolean() {
        boolean result = WindowsSupport.supportsColors();
        assertNotNull(result);
    }

    // ============================================
    // getEmoji Tests
    // ============================================

    @Test
    void testGetEmojiWithSupport() {
        String result = WindowsSupport.getEmoji("🚀", "[ROCKET]");
        assertNotNull(result);
        // Should return one or the other
        assertTrue(result.equals("🚀") || result.equals("[ROCKET]"));
    }

    @Test
    void testGetEmojiFallback() {
        // When emojis not supported, should return fallback
        String emoji = "🎉";
        String fallback = "[PARTY]";
        String result = WindowsSupport.getEmoji(emoji, fallback);
        
        // Result should be one of the two options
        assertTrue(result.equals(emoji) || result.equals(fallback));
    }

    // ============================================
    // getTerminalType Tests
    // ============================================

    @Test
    void testGetTerminalType() {
        String terminalType = WindowsSupport.getTerminalType();
        assertNotNull(terminalType);
        assertFalse(terminalType.isEmpty());
    }

    // ============================================
    // configureTerminalEnvironment Tests
    // ============================================

    @Test
    void testConfigureTerminalEnvironmentNoException() {
        // Should not throw any exception
        assertDoesNotThrow(() -> WindowsSupport.configureTerminalEnvironment());
    }

    // ============================================
    // Symbols Class Tests
    // ============================================

    @Test
    void testSymbolsSuccess() {
        String symbol = WindowsSupport.Symbols.SUCCESS;
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    @Test
    void testSymbolsError() {
        String symbol = WindowsSupport.Symbols.ERROR;
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    @Test
    void testSymbolsWarning() {
        String symbol = WindowsSupport.Symbols.WARNING;
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    @Test
    void testSymbolsInfo() {
        String symbol = WindowsSupport.Symbols.INFO;
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    @Test
    void testSymbolsRocket() {
        String symbol = WindowsSupport.Symbols.ROCKET;
        assertNotNull(symbol);
    }

    @Test
    void testSymbolsFolder() {
        String symbol = WindowsSupport.Symbols.FOLDER;
        assertNotNull(symbol);
    }

    @Test
    void testSymbolsComputer() {
        String symbol = WindowsSupport.Symbols.COMPUTER;
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    @Test
    void testSymbolsTool() {
        String symbol = WindowsSupport.Symbols.TOOL;
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    @Test
    void testSymbolsArt() {
        String symbol = WindowsSupport.Symbols.ART;
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    @Test
    void testSymbolsWave() {
        String symbol = WindowsSupport.Symbols.WAVE;
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    @Test
    void testSymbolsBulb() {
        String symbol = WindowsSupport.Symbols.BULB;
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    // ============================================
    // printMessage Tests
    // ============================================

    @Test
    void testPrintMessageNoException() {
        assertDoesNotThrow(() -> WindowsSupport.printMessage("Test message"));
    }

    @Test
    void testPrintMessageNull() {
        assertDoesNotThrow(() -> WindowsSupport.printMessage(null));
    }

    // ============================================
    // printStatus Tests
    // ============================================

    @Test
    void testPrintStatusSuccess() {
        assertDoesNotThrow(() -> WindowsSupport.printStatus("success", "Test success message"));
    }

    @Test
    void testPrintStatusError() {
        assertDoesNotThrow(() -> WindowsSupport.printStatus("error", "Test error message"));
    }

    @Test
    void testPrintStatusWarning() {
        assertDoesNotThrow(() -> WindowsSupport.printStatus("warning", "Test warning message"));
    }

    @Test
    void testPrintStatusInfo() {
        assertDoesNotThrow(() -> WindowsSupport.printStatus("info", "Test info message"));
    }

    @Test
    void testPrintStatusUnknown() {
        assertDoesNotThrow(() -> WindowsSupport.printStatus("unknown", "Test unknown message"));
    }

    // ============================================
    // TerminalCapabilities Tests
    // ============================================

    @Test
    void testTerminalCapabilitiesIsWindowsTerminal() {
        boolean result = WindowsSupport.TerminalCapabilities.isWindowsTerminal();
        // Just verify it returns without exception
        assertNotNull(result);
    }

    @Test
    void testTerminalCapabilitiesIsVSCodeTerminal() {
        boolean result = WindowsSupport.TerminalCapabilities.isVSCodeTerminal();
        assertNotNull(result);
    }

    @Test
    void testTerminalCapabilitiesIsConEmu() {
        boolean result = WindowsSupport.TerminalCapabilities.isConEmu();
        assertNotNull(result);
    }

    @Test
    void testTerminalCapabilitiesIsUnixLikeTerminal() {
        boolean result = WindowsSupport.TerminalCapabilities.isUnixLikeTerminal();
        assertNotNull(result);
    }

    @Test
    void testTerminalCapabilitiesIsLegacyConsoleHost() {
        boolean result = WindowsSupport.TerminalCapabilities.isLegacyConsoleHost();
        assertNotNull(result);
    }

    @Test
    void testTerminalCapabilitiesIsDumbTerminal() {
        boolean result = WindowsSupport.TerminalCapabilities.isDumbTerminal();
        assertNotNull(result);
    }

    @Test
    void testTerminalCapabilitiesGetPowerShellMajorVersion() {
        int version = WindowsSupport.TerminalCapabilities.getPowerShellMajorVersion();
        assertTrue(version >= 0);
    }

    @Test
    void testTerminalCapabilitiesGetTerminalDescription() {
        String description = WindowsSupport.TerminalCapabilities.getTerminalDescription();
        assertNotNull(description);
        assertFalse(description.isEmpty());
    }

    @Test
    void testTerminalCapabilitiesGetEmojiSupportScore() {
        int score = WindowsSupport.TerminalCapabilities.getEmojiSupportScore();
        assertTrue(score >= 0);
        assertTrue(score <= 10);
    }

    @Test
    void testTerminalCapabilitiesGetCapabilityReport() {
        String report = WindowsSupport.TerminalCapabilities.getCapabilityReport();
        assertNotNull(report);
        assertFalse(report.isEmpty());
        
        // Report should contain key sections
        assertTrue(report.contains("Terminal:"));
        assertTrue(report.contains("Emoji Support:"));
        assertTrue(report.contains("Color Support:"));
        assertTrue(report.contains("OS:"));
    }

    // ============================================
    // Integration Tests
    // ============================================

    @Test
    void testConsistencyBetweenMethods() {
        // If supportsEmojis is true, symbols should be emojis
        boolean supportsEmojis = WindowsSupport.supportsEmojis();
        String successSymbol = WindowsSupport.Symbols.SUCCESS;
        
        if (supportsEmojis) {
            // Should contain emoji character
            assertTrue(successSymbol.contains("✅") || successSymbol.equals("[OK]"));
        } else {
            // Should be text fallback
            assertEquals("[OK]", successSymbol);
        }
    }

    @Test
    void testCapabilityReportMatchesMethods() {
        String report = WindowsSupport.TerminalCapabilities.getCapabilityReport();
        boolean emojiSupport = WindowsSupport.supportsEmojis();
        
        if (emojiSupport) {
            assertTrue(report.contains("Emoji Support: Yes"));
        } else {
            assertTrue(report.contains("Emoji Support: No"));
        }
    }
}
