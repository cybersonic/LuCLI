package org.lucee.lucli;

import java.util.LinkedHashMap;
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
    
    // Emoji to text fallback mappings (order matters - longer sequences first)
    private static final Map<String, String> EMOJI_FALLBACKS = new LinkedHashMap<>();
    
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
        
        String result = text;
        for (Map.Entry<String, String> entry : EMOJI_FALLBACKS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        
        // Clean up variation selectors and zero-width joiners that might be left over
        result = result.replaceAll("[\uFE00-\uFE0F]", "");  // Variation selectors
        result = result.replace("\u200D", "");              // Zero-width joiner
        
        return result;
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
    }
}
