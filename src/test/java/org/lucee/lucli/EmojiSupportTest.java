package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EmojiSupportTest {

    @AfterEach
    void restoreEmojiMode() {
        EmojiSupport.setEnabled(true);
    }

    @Test
    void explicitFallbackMapping_shouldApplyFirst() {
        EmojiSupport.setEnabled(false);
        String result = EmojiSupport.process("❌ Unknown command");
        assertEquals("[ERROR] Unknown command", result);
    }

    @Test
    void genericUnknownEmoji_shouldFallbackToGenericToken() {
        EmojiSupport.setEnabled(false);
        String result = EmojiSupport.process("Status: 🫨");
        assertEquals("Status: [EMOJI]", result);
    }

    @Test
    void zwjSequenceWithoutExplicitMapping_shouldFallbackToSingleGenericToken() {
        EmojiSupport.setEnabled(false);
        String result = EmojiSupport.process("Astronaut: 🧑‍🚀 ready");
        assertEquals("Astronaut: [EMOJI] ready", result);
    }

    @Test
    void keycapSequenceWithoutExplicitMapping_shouldFallbackToGenericToken() {
        EmojiSupport.setEnabled(false);
        String result = EmojiSupport.process("Press 1️⃣ now");
        assertEquals("Press [EMOJI] now", result);
    }

    @Test
    void nonEmojiCharacters_shouldRemainUnchanged() {
        EmojiSupport.setEnabled(false);
        String result = EmojiSupport.process("abc 123 _-+");
        assertEquals("abc 123 _-+", result);
    }

    @Test
    void explicitArrowFallback_shouldStillWork() {
        EmojiSupport.setEnabled(false);
        String result = EmojiSupport.process("Path ➤ next");
        assertEquals("Path > next", result);
    }

    @Test
    void regionalFlagPair_shouldUseGenericFallback() {
        EmojiSupport.setEnabled(false);
        String result = EmojiSupport.process("Flag: 🇺🇸");
        assertTrue(result.equals("Flag: [EMOJI]"));
    }
}
