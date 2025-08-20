package org.lucee.lucli;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unicode icon support using standard Unicode symbols
 * These icons will work in any terminal without requiring special fonts
 */
public class UnicodeIcons {
    
    // Standard Unicode symbols that work in most terminals
    private static final Map<String, String> ICONS = new HashMap<>();
    
    static {
        // Navigation & System
        ICONS.put("folder", "📁");
        ICONS.put("folder-open", "📂");
        ICONS.put("home", "🏠");
        ICONS.put("user", "👤");
        ICONS.put("cog", "⚙️");
        ICONS.put("gear", "⚙️");
        ICONS.put("terminal", "💻");
        ICONS.put("desktop", "🖥️");
        ICONS.put("laptop", "💻");
        ICONS.put("mobile", "📱");
        ICONS.put("server", "🖥️");
        ICONS.put("database", "🗃️");
        
        // Development & Code
        ICONS.put("code", "💻");
        ICONS.put("code-branch", "🌿");
        ICONS.put("git", "📝");
        ICONS.put("github", "🐙");
        ICONS.put("gitlab", "🦊");
        ICONS.put("bitbucket", "🪣");
        ICONS.put("bug", "🐛");
        ICONS.put("wrench", "🔧");
        ICONS.put("tools", "🛠️");
        ICONS.put("hammer", "🔨");
        ICONS.put("screwdriver-wrench", "🛠️");
        ICONS.put("file-code", "📄");
        ICONS.put("brackets-curly", "{ }");
        
        // Status & Indicators  
        ICONS.put("check", "✅");
        ICONS.put("check-circle", "✅");
        ICONS.put("times", "❌");
        ICONS.put("times-circle", "❌");
        ICONS.put("exclamation", "❗");
        ICONS.put("exclamation-triangle", "⚠️");
        ICONS.put("info", "ℹ️");
        ICONS.put("info-circle", "ℹ️");
        ICONS.put("question", "❓");
        ICONS.put("question-circle", "❓");
        ICONS.put("warning", "⚠️");
        ICONS.put("shield", "🛡️");
        ICONS.put("shield-alt", "🛡️");
        
        // Time & Calendar
        ICONS.put("clock", "🕐");
        ICONS.put("calendar", "📅");
        ICONS.put("calendar-alt", "📅");
        ICONS.put("stopwatch", "⏱️");
        ICONS.put("hourglass", "⏳");
        
        // Arrows & Movement (using ASCII and Unicode arrows)
        ICONS.put("arrow-right", "→");
        ICONS.put("arrow-left", "←");
        ICONS.put("arrow-up", "↑");
        ICONS.put("arrow-down", "↓");
        ICONS.put("chevron-right", "›");
        ICONS.put("chevron-left", "‹");
        ICONS.put("chevron-up", "^");
        ICONS.put("chevron-down", "v");
        ICONS.put("angle-right", ">");
        ICONS.put("angle-left", "<");
        ICONS.put("caret-right", "▶");
        ICONS.put("caret-left", "◀");
        
        // Files & Documents
        ICONS.put("file", "📄");
        ICONS.put("file-alt", "📃");
        ICONS.put("file-text", "📃");
        ICONS.put("archive", "📦");
        ICONS.put("download", "⬇️");
        ICONS.put("upload", "⬆️");
        ICONS.put("save", "💾");
        ICONS.put("edit", "✏️");
        ICONS.put("trash", "🗑️");
        ICONS.put("copy", "📋");
        ICONS.put("cut", "✂️");
        ICONS.put("paste", "📋");
        
        // Network & Cloud
        ICONS.put("wifi", "📶");
        ICONS.put("cloud", "☁️");
        ICONS.put("cloud-download", "☁️⬇️");
        ICONS.put("cloud-upload", "☁️⬆️");
        ICONS.put("globe", "🌐");
        ICONS.put("link", "🔗");
        ICONS.put("unlink", "🔗💔");
        ICONS.put("network-wired", "🔌");
        
        // Media & Communication
        ICONS.put("play", "▶️");
        ICONS.put("pause", "⏸️");
        ICONS.put("stop", "⏹️");
        ICONS.put("microphone", "🎤");
        ICONS.put("volume-up", "🔊");
        ICONS.put("volume-down", "🔉");
        ICONS.put("volume-mute", "🔇");
        ICONS.put("bell", "🔔");
        ICONS.put("envelope", "✉️");
        ICONS.put("comment", "💬");
        ICONS.put("comments", "💬");
        
        // Shapes & UI Elements
        ICONS.put("star", "⭐");
        ICONS.put("heart", "❤️");
        ICONS.put("circle", "⚫");
        ICONS.put("square", "⬛");
        ICONS.put("diamond", "💎");
        ICONS.put("dot-circle", "🔘");
        ICONS.put("plus", "➕");
        ICONS.put("minus", "➖");
        ICONS.put("equals", "=");
        ICONS.put("hashtag", "#");
        ICONS.put("at", "@");
        
        // Weather & Nature
        ICONS.put("sun", "☀️");
        ICONS.put("moon", "🌙");
        ICONS.put("cloud-sun", "⛅");
        ICONS.put("cloud-moon", "☁️🌙");
        ICONS.put("bolt", "⚡");
        ICONS.put("snowflake", "❄️");
        ICONS.put("fire", "🔥");
        ICONS.put("water", "💧");
        ICONS.put("leaf", "🍃");
        ICONS.put("tree", "🌳");
        
        // Transportation
        ICONS.put("car", "🚗");
        ICONS.put("plane", "✈️");
        ICONS.put("ship", "🚢");
        ICONS.put("train", "🚆");
        ICONS.put("truck", "🚚");
        ICONS.put("bicycle", "🚲");
        ICONS.put("rocket", "🚀");
        ICONS.put("space-shuttle", "🚀");
        
        // Business & Commerce
        ICONS.put("briefcase", "💼");
        ICONS.put("chart-bar", "📊");
        ICONS.put("chart-line", "📈");
        ICONS.put("chart-pie", "📊");
        ICONS.put("dollar-sign", "💲");
        ICONS.put("euro-sign", "€");
        ICONS.put("pound-sign", "£");
        ICONS.put("yen-sign", "¥");
        ICONS.put("credit-card", "💳");
        ICONS.put("shopping-cart", "🛒");
        ICONS.put("store", "🏪");
        
        // Gaming & Entertainment
        ICONS.put("gamepad", "🎮");
        ICONS.put("dice", "🎲");
        ICONS.put("chess", "♟️");
        ICONS.put("puzzle-piece", "🧩");
        ICONS.put("trophy", "🏆");
        ICONS.put("medal", "🥇");
        ICONS.put("crown", "👑");
        
        // Science & Technology
        ICONS.put("atom", "⚛️");
        ICONS.put("dna", "🧬");
        ICONS.put("microscope", "🔬");
        ICONS.put("flask", "🧪");
        ICONS.put("magnet", "🧲");
        ICONS.put("radiation", "☢️");
        ICONS.put("satellite", "🛰️");
        ICONS.put("robot", "🤖");
        
        // Security & Privacy
        ICONS.put("lock", "🔒");
        ICONS.put("unlock", "🔓");
        ICONS.put("key", "🔑");
        ICONS.put("eye", "👁️");
        ICONS.put("eye-slash", "🙈");
        ICONS.put("fingerprint", "👆");
        ICONS.put("user-secret", "🕵️");
        ICONS.put("mask", "🎭");
    }
    
    /**
     * Get Unicode icon by name
     * @param iconName The name of the icon
     * @return Unicode emoji/symbol, or empty string if not found
     */
    public static String getIcon(String iconName) {
        if (iconName == null) return "";
        return ICONS.getOrDefault(iconName.toLowerCase().trim(), "");
    }
    
    /**
     * Check if an icon exists
     * @param iconName The name of the icon
     * @return true if the icon exists
     */
    public static boolean hasIcon(String iconName) {
        return iconName != null && ICONS.containsKey(iconName.toLowerCase().trim());
    }
    
    /**
     * Get all available icon names
     * @return Set of all icon names
     */
    public static Set<String> getAvailableIcons() {
        return ICONS.keySet();
    }
    
    /**
     * Get icon count
     * @return Number of available icons
     */
    public static int getIconCount() {
        return ICONS.size();
    }
    
    /**
     * Replace icon placeholders in text with actual Unicode symbols
     * Supports syntax: {fa:icon-name} or {icon:icon-name}
     * @param text Text containing icon placeholders
     * @return Text with placeholders replaced by actual Unicode symbols
     */
    public static String replaceIcons(String text) {
        if (text == null || text.isEmpty()) return text;
        
        String result = text;
        
        // Replace {fa:icon-name} syntax
        java.util.regex.Pattern faPattern = java.util.regex.Pattern.compile("\\{fa:([^}]+)\\}");
        java.util.regex.Matcher faMatcher = faPattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        
        while (faMatcher.find()) {
            String iconName = faMatcher.group(1);
            String icon = getIcon(iconName);
            faMatcher.appendReplacement(sb, icon.isEmpty() ? java.util.regex.Matcher.quoteReplacement(faMatcher.group(0)) : java.util.regex.Matcher.quoteReplacement(icon));
        }
        faMatcher.appendTail(sb);
        result = sb.toString();
        
        // Replace {icon:icon-name} syntax
        java.util.regex.Pattern iconPattern = java.util.regex.Pattern.compile("\\{icon:([^}]+)\\}");
        java.util.regex.Matcher iconMatcher = iconPattern.matcher(result);
        sb = new StringBuffer();
        
        while (iconMatcher.find()) {
            String iconName = iconMatcher.group(1);
            String icon = getIcon(iconName);
            iconMatcher.appendReplacement(sb, icon.isEmpty() ? java.util.regex.Matcher.quoteReplacement(iconMatcher.group(0)) : java.util.regex.Matcher.quoteReplacement(icon));
        }
        iconMatcher.appendTail(sb);
        
        return sb.toString();
    }
}
