package org.lucee.lucli.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry mapping extension names/slugs to Lucee extension IDs.
 * 
 * This provides a developer-friendly way to reference extensions by name
 * rather than requiring the full UUID.
 * 
 * Example:
 *   "redis" -> "ECC268AA-4B25-4DBC-A57CA2E7A929A83E"
 *   "h2" -> "465E1E35-2425-4F4E-8B3FAB638BD7280A"
 */
public class ExtensionRegistry {
    
    private static final Map<String, ExtensionInfo> EXTENSIONS = new HashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    static {
        loadExtensions();
    }
    
    public static class ExtensionInfo {
        public final String id;
        public final String name;
        public final String[] aliases;
        
        public ExtensionInfo(String id, String name, String... aliases) {
            this.id = id;
            this.name = name;
            this.aliases = aliases;
        }
    }
    
    /**
     * Load extension mappings from bundled resource file.
     * This file can be updated periodically from the Lucee extension provider.
     */
    private static void loadExtensions() {
        try {
            InputStream is = ExtensionRegistry.class.getResourceAsStream("/extensions/lucee-extensions.json");
            if (is != null) {
                JsonNode root = MAPPER.readTree(is);
                JsonNode extensions = root.get("extensions");
                
                if (extensions != null && extensions.isArray()) {
                    for (JsonNode ext : extensions) {
                        String id = ext.get("id").asText();
                        String name = ext.get("name").asText();
                        String slug = ext.has("slug") ? ext.get("slug").asText() : null;
                        
                        JsonNode aliasesNode = ext.get("aliases");
                        String[] aliases = new String[aliasesNode != null ? aliasesNode.size() : 0];
                        if (aliasesNode != null && aliasesNode.isArray()) {
                            for (int i = 0; i < aliasesNode.size(); i++) {
                                aliases[i] = aliasesNode.get(i).asText();
                            }
                        }
                        
                        ExtensionInfo info = new ExtensionInfo(id, name, aliases);
                        
                        // Register by slug (primary key)
                        if (slug != null) {
                            EXTENSIONS.put(slug.toLowerCase(), info);
                        }
                        
                        // Register by name (for lookups)
                        EXTENSIONS.put(name.toLowerCase(), info);
                        
                        // Register aliases
                        for (String alias : aliases) {
                            EXTENSIONS.put(alias.toLowerCase(), info);
                        }
                    }
                }
            } else {
                System.err.println("Warning: Extension registry file not found (lucee-extensions.json)");
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to load extension registry: " + e.getMessage());
        }
    }
    
    /**
     * Resolve an extension name/slug to its ID.
     * 
     * @param nameOrSlug Extension name, slug, or alias
     * @return Extension ID (UUID) or null if not found
     */
    public static String resolveId(String nameOrSlug) {
        if (nameOrSlug == null || nameOrSlug.trim().isEmpty()) {
            return null;
        }
        
        // If it's already a UUID format, return as-is
        if (isUUID(nameOrSlug)) {
            return nameOrSlug;
        }
        
        ExtensionInfo info = EXTENSIONS.get(nameOrSlug.toLowerCase().trim());
        return info != null ? info.id : null;
    }
    
    /**
     * Get extension info by name/slug.
     * 
     * @param nameOrSlug Extension name, slug, or alias
     * @return ExtensionInfo or null if not found
     */
    public static ExtensionInfo getInfo(String nameOrSlug) {
        if (nameOrSlug == null || nameOrSlug.trim().isEmpty()) {
            return null;
        }
        return EXTENSIONS.get(nameOrSlug.toLowerCase().trim());
    }
    
    /**
     * Check if a string is a UUID format.
     */
    private static boolean isUUID(String str) {
        // UUID format: 8-4-4-4-12 hex digits
        return str.matches("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$") ||
               // Also support without dashes (Lucee style)
               str.matches("^[0-9A-Fa-f]{32}$");
    }
    
    /**
     * List all registered extensions.
     */
    public static Map<String, ExtensionInfo> listAll() {
        return new HashMap<>(EXTENSIONS);
    }
}
