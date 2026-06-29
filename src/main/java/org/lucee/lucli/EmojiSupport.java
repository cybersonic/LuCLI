package org.lucee.lucli;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Simple global emoji support - controls whether emojis are displayed or replaced with text.
 * 
 * Usage:
 *   EmojiSupport.setEnabled(false);  // Disable emojis globally
 *   String output = EmojiSupport.process(text);  // Replaces emojis if disabled
 */
public class EmojiSupport {
    
    private static boolean enabled = true;
    private static final String GENERIC_EMOJI_FALLBACK = "[EMOJI]";
    
    // Emoji to text fallback mappings (order matters - longer sequences first)
    private static final Map<String, String> EMOJI_FALLBACKS = new LinkedHashMap<>();
    private static final List<String> SORTED_FALLBACK_KEYS = new ArrayList<>();
    
    static {
        // Multi-character emojis first (ZWJ sequences, etc.)
        EMOJI_FALLBACKS.put("👨‍💻", "[DEV]");
        EMOJI_FALLBACKS.put("🛠️", "[TOOLS]");
        EMOJI_FALLBACKS.put("⚙️", "[GEAR]");
        EMOJI_FALLBACKS.put("🖥️", "[PC]");
        EMOJI_FALLBACKS.put("⚠️", "[WARN]");
        EMOJI_FALLBACKS.put("ℹ️", "[INFO]");
        EMOJI_FALLBACKS.put("✔️", "[OK]");
        EMOJI_FALLBACKS.put("▶️", ">");
        EMOJI_FALLBACKS.put("⏸️", "||");
        EMOJI_FALLBACKS.put("⏹️", "[]");
        EMOJI_FALLBACKS.put("☁️", "[CLOUD]");
        EMOJI_FALLBACKS.put("✉️", "[MAIL]");
        EMOJI_FALLBACKS.put("❤️", "<3");
        EMOJI_FALLBACKS.put("☀️", "[SUN]");
        EMOJI_FALLBACKS.put("✈️", "[PLANE]");
        EMOJI_FALLBACKS.put("⚛️", "[ATOM]");
        EMOJI_FALLBACKS.put("☢️", "[RAD]");
        EMOJI_FALLBACKS.put("✂️", "[CUT]");
        EMOJI_FALLBACKS.put("✏️", "[EDIT]");
        EMOJI_FALLBACKS.put("♟️", "[CHESS]");
        EMOJI_FALLBACKS.put("👁️", "[EYE]");
        EMOJI_FALLBACKS.put("🕵️", "[SPY]");
        EMOJI_FALLBACKS.put("⬇️", "v");
        EMOJI_FALLBACKS.put("⬆️", "^");
        
        // Status indicators
        EMOJI_FALLBACKS.put("✅", "[OK]");
        EMOJI_FALLBACKS.put("❌", "[ERROR]");
        EMOJI_FALLBACKS.put("❗", "[!]");
        EMOJI_FALLBACKS.put("❓", "[?]");
        EMOJI_FALLBACKS.put("✓", "[OK]");
        
        // Common UI emojis
        EMOJI_FALLBACKS.put("🚀", "[LAUNCH]");
        EMOJI_FALLBACKS.put("📁", "[DIR]");
        EMOJI_FALLBACKS.put("📂", "[DIR]");
        EMOJI_FALLBACKS.put("💻", ">");
        EMOJI_FALLBACKS.put("🔧", "[TOOL]");
        EMOJI_FALLBACKS.put("🎨", "[ART]");
        EMOJI_FALLBACKS.put("👋", "Bye!");
        EMOJI_FALLBACKS.put("💡", "[TIP]");
        EMOJI_FALLBACKS.put("🏠", "[HOME]");
        EMOJI_FALLBACKS.put("👤", "[USER]");
        EMOJI_FALLBACKS.put("📱", "[MOBILE]");
        EMOJI_FALLBACKS.put("🗃️", "[DB]");
        
        // Development
        EMOJI_FALLBACKS.put("🌿", "[BRANCH]");
        EMOJI_FALLBACKS.put("📝", "[GIT]");
        EMOJI_FALLBACKS.put("🐙", "[GH]");
        EMOJI_FALLBACKS.put("🦊", "[GL]");
        EMOJI_FALLBACKS.put("🪣", "[BB]");
        EMOJI_FALLBACKS.put("🐛", "[BUG]");
        EMOJI_FALLBACKS.put("🔨", "[BUILD]");
        EMOJI_FALLBACKS.put("📄", "[FILE]");
        EMOJI_FALLBACKS.put("📃", "[DOC]");
        
        // Time
        EMOJI_FALLBACKS.put("🕐", "[TIME]");
        EMOJI_FALLBACKS.put("📅", "[CAL]");
        EMOJI_FALLBACKS.put("⏱️", "[TIMER]");
        EMOJI_FALLBACKS.put("⏳", "[WAIT]");
        EMOJI_FALLBACKS.put("⏰", "[ALARM]");
        
        // Arrows (simple replacements)
        EMOJI_FALLBACKS.put("→", "->");
        EMOJI_FALLBACKS.put("←", "<-");
        EMOJI_FALLBACKS.put("↑", "^");
        EMOJI_FALLBACKS.put("↓", "v");
        EMOJI_FALLBACKS.put("➤", ">");
        EMOJI_FALLBACKS.put("▶", ">");
        EMOJI_FALLBACKS.put("►", ">");
        EMOJI_FALLBACKS.put("◀", "<");
        EMOJI_FALLBACKS.put("›", ">");
        EMOJI_FALLBACKS.put("‹", "<");
        
        // Misc
        EMOJI_FALLBACKS.put("⚡", "[ZAP]");
        EMOJI_FALLBACKS.put("✨", "*");
        EMOJI_FALLBACKS.put("🔥", "[FIRE]");
        EMOJI_FALLBACKS.put("🌈", "[PROMPT]");
        EMOJI_FALLBACKS.put("💧", "[DROP]");
        EMOJI_FALLBACKS.put("🍃", "[LEAF]");
        EMOJI_FALLBACKS.put("🌳", "[TREE]");
        EMOJI_FALLBACKS.put("🌙", "[MOON]");
        EMOJI_FALLBACKS.put("❄️", "[SNOW]");
        EMOJI_FALLBACKS.put("⛅", "[CLOUDY]");
        EMOJI_FALLBACKS.put("📦", "[PKG]");
        EMOJI_FALLBACKS.put("💾", "[SAVE]");
        EMOJI_FALLBACKS.put("🗑️", "[DEL]");
        EMOJI_FALLBACKS.put("📋", "[CLIP]");
        EMOJI_FALLBACKS.put("📶", "[WIFI]");
        EMOJI_FALLBACKS.put("🌐", "[WEB]");
        EMOJI_FALLBACKS.put("🔗", "[LINK]");
        EMOJI_FALLBACKS.put("🔌", "[PLUG]");
        EMOJI_FALLBACKS.put("🎤", "[MIC]");
        EMOJI_FALLBACKS.put("🔊", "[VOL+]");
        EMOJI_FALLBACKS.put("🔉", "[VOL]");
        EMOJI_FALLBACKS.put("🔇", "[MUTE]");
        EMOJI_FALLBACKS.put("🔔", "[BELL]");
        EMOJI_FALLBACKS.put("💬", "[MSG]");
        EMOJI_FALLBACKS.put("⭐", "*");
        EMOJI_FALLBACKS.put("⚫", "o");
        EMOJI_FALLBACKS.put("⬛", "[]");
        EMOJI_FALLBACKS.put("💎", "[GEM]");
        EMOJI_FALLBACKS.put("🔘", "(o)");
        EMOJI_FALLBACKS.put("➕", "+");
        EMOJI_FALLBACKS.put("➖", "-");
        
        // Transport
        EMOJI_FALLBACKS.put("🚗", "[CAR]");
        EMOJI_FALLBACKS.put("🚢", "[SHIP]");
        EMOJI_FALLBACKS.put("🚆", "[TRAIN]");
        EMOJI_FALLBACKS.put("🚂", "[TRAIN]");
        EMOJI_FALLBACKS.put("🚊", "[TRAIN]");
        EMOJI_FALLBACKS.put("🚚", "[TRUCK]");
        EMOJI_FALLBACKS.put("🚲", "[BIKE]");
        
        // Business
        EMOJI_FALLBACKS.put("💼", "[BIZ]");
        EMOJI_FALLBACKS.put("📊", "[CHART]");
        EMOJI_FALLBACKS.put("📈", "[UP]");
        EMOJI_FALLBACKS.put("💲", "$");
        EMOJI_FALLBACKS.put("💳", "[CARD]");
        EMOJI_FALLBACKS.put("🛒", "[CART]");
        EMOJI_FALLBACKS.put("🏪", "[STORE]");
        EMOJI_FALLBACKS.put("🏢", "[BLDG]");
        
        // Gaming
        EMOJI_FALLBACKS.put("🎮", "[GAME]");
        EMOJI_FALLBACKS.put("🕹️", "[JOY]");
        EMOJI_FALLBACKS.put("🎲", "[DICE]");
        EMOJI_FALLBACKS.put("🧩", "[PUZZLE]");
        EMOJI_FALLBACKS.put("🏆", "[TROPHY]");
        EMOJI_FALLBACKS.put("🥇", "[1ST]");
        EMOJI_FALLBACKS.put("👑", "[CROWN]");
        
        // Science
        EMOJI_FALLBACKS.put("🧬", "[DNA]");
        EMOJI_FALLBACKS.put("🔬", "[MICRO]");
        EMOJI_FALLBACKS.put("🧪", "[LAB]");
        EMOJI_FALLBACKS.put("🧲", "[MAG]");
        EMOJI_FALLBACKS.put("🛰️", "[SAT]");
        EMOJI_FALLBACKS.put("🤖", "[BOT]");
        
        // Security
        EMOJI_FALLBACKS.put("🔒", "[LOCK]");
        EMOJI_FALLBACKS.put("🔓", "[OPEN]");
        EMOJI_FALLBACKS.put("🔑", "[KEY]");
        EMOJI_FALLBACKS.put("🙈", "[HIDE]");
        EMOJI_FALLBACKS.put("🎭", "[MASK]");
        EMOJI_FALLBACKS.put("🛡️", "[SHIELD]");
        EMOJI_FALLBACKS.put("🔋", "[BAT]");
        rebuildSortedFallbackKeys();
    }
    
    /**
     * Check if emojis are enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Enable or disable emoji display globally
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }
    
    /**
     * Process a string - replaces emojis with text fallbacks if emojis are disabled
     */
    public static String process(String text) {
        if (text == null || text.isEmpty() || enabled) {
            return text;
        }

        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            String explicitMatch = findExplicitFallbackMatch(text, index);
            if (explicitMatch != null) {
                out.append(EMOJI_FALLBACKS.get(explicitMatch));
                index += explicitMatch.length();
                continue;
            }

            int emojiEnd = findGenericEmojiSequenceEnd(text, index);
            if (emojiEnd > index) {
                out.append(GENERIC_EMOJI_FALLBACK);
                index = emojiEnd;
                continue;
            }

            int cp = text.codePointAt(index);
            out.appendCodePoint(cp);
            index += Character.charCount(cp);
        }
        return out.toString();
    }
    
    /**
     * Get emoji or fallback based on current enabled state
     */
    public static String emoji(String emoji, String fallback) {
        return enabled ? emoji : fallback;
    }
    
    /**
     * Add or update an emoji fallback mapping
     */
    public static void addFallback(String emoji, String fallback) {
        EMOJI_FALLBACKS.put(emoji, fallback);
        rebuildSortedFallbackKeys();
    }

    private static void rebuildSortedFallbackKeys() {
        SORTED_FALLBACK_KEYS.clear();
        SORTED_FALLBACK_KEYS.addAll(EMOJI_FALLBACKS.keySet());
        SORTED_FALLBACK_KEYS.sort(Comparator.comparingInt(String::length).reversed());
    }

    private static String findExplicitFallbackMatch(String text, int index) {
        for (String key : SORTED_FALLBACK_KEYS) {
            if (text.startsWith(key, index)) {
                return key;
            }
        }
        return null;
    }

    private static int findGenericEmojiSequenceEnd(String text, int index) {
        int cp = text.codePointAt(index);

        if (isKeycapBase(cp)) {
            int next = index + Character.charCount(cp);
            if (next < text.length()) {
                int nextCp = text.codePointAt(next);
                if (isVariationSelector(nextCp)) {
                    next += Character.charCount(nextCp);
                }
            }
            if (next < text.length()) {
                int nextCp = text.codePointAt(next);
                if (isCombiningEnclosingKeycap(nextCp)) {
                    return next + Character.charCount(nextCp);
                }
            }
            return -1;
        }

        if (isRegionalIndicator(cp)) {
            int next = index + Character.charCount(cp);
            if (next < text.length()) {
                int nextCp = text.codePointAt(next);
                if (isRegionalIndicator(nextCp)) {
                    return next + Character.charCount(nextCp);
                }
            }
            return -1;
        }

        if (!isLikelyEmojiBase(cp)) {
            return -1;
        }

        int current = index + Character.charCount(cp);

        if (current < text.length()) {
            int maybeVs = text.codePointAt(current);
            if (isVariationSelector(maybeVs)) {
                current += Character.charCount(maybeVs);
            }
        }

        if (current < text.length()) {
            int maybeModifier = text.codePointAt(current);
            if (isEmojiModifier(maybeModifier)) {
                current += Character.charCount(maybeModifier);
            }
        }

        if (cp == 0x1F3F4) {
            int tagCursor = current;
            boolean sawTag = false;
            while (tagCursor < text.length()) {
                int tagCp = text.codePointAt(tagCursor);
                if (isTagCharacter(tagCp)) {
                    sawTag = true;
                    tagCursor += Character.charCount(tagCp);
                    continue;
                }
                if (tagCp == 0xE007F && sawTag) {
                    current = tagCursor + Character.charCount(tagCp);
                }
                break;
            }
        }

        while (current < text.length()) {
            int zwj = text.codePointAt(current);
            if (!isZeroWidthJoiner(zwj)) {
                break;
            }
            int nextBaseIndex = current + Character.charCount(zwj);
            if (nextBaseIndex >= text.length()) {
                break;
            }
            int nextBase = text.codePointAt(nextBaseIndex);
            if (!isLikelyEmojiBase(nextBase)) {
                break;
            }

            current = nextBaseIndex + Character.charCount(nextBase);
            if (current < text.length()) {
                int maybeVs = text.codePointAt(current);
                if (isVariationSelector(maybeVs)) {
                    current += Character.charCount(maybeVs);
                }
            }
            if (current < text.length()) {
                int maybeModifier = text.codePointAt(current);
                if (isEmojiModifier(maybeModifier)) {
                    current += Character.charCount(maybeModifier);
                }
            }
        }

        return current;
    }

    private static boolean isLikelyEmojiBase(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF)
            || (cp >= 0x2600 && cp <= 0x27BF)
            || (cp >= 0x2300 && cp <= 0x23FF);
    }

    private static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    private static boolean isEmojiModifier(int cp) {
        return cp >= 0x1F3FB && cp <= 0x1F3FF;
    }

    private static boolean isVariationSelector(int cp) {
        return cp == 0xFE0F || cp == 0xFE0E;
    }

    private static boolean isZeroWidthJoiner(int cp) {
        return cp == 0x200D;
    }

    private static boolean isKeycapBase(int cp) {
        return (cp >= '0' && cp <= '9') || cp == '#' || cp == '*';
    }

    private static boolean isCombiningEnclosingKeycap(int cp) {
        return cp == 0x20E3;
    }

    private static boolean isTagCharacter(int cp) {
        return cp >= 0xE0020 && cp <= 0xE007E;
    }
}
