# Proposal: Consolidate Version Methods

**Date:** 2025-12-20  
**Type:** Code Simplification  
**Status:** Proposal

---

## Current State

We have **4 methods** that handle version information:

```java
// 1. Raw version number
public static String getVersion() {
    // Returns: "0.1.207-SNAPSHOT"
}

// 2. Formatted LuCLI version
public static String getVersionInfo() {
    return "LuCLI " + getVersion();
    // Returns: "LuCLI 0.1.207-SNAPSHOT"
}

// 3. Lucee version with label
public static String getLuceeVersionInfo() throws Exception {
    return "Lucee Version: " + LuceeScriptEngine.getInstance(false,false).getVersion();
    // Returns: "Lucee Version: 6.2.2.91"
}

// 4. Combined version info
public static String getFullVersionInfo() {
    StringBuilder versionInfo = new StringBuilder();
    versionInfo.append(getVersionInfo());
    try {
        String luceeVersion = getLuceeVersionInfo();
        if (luceeVersion != null) {
            versionInfo.append("\n").append(luceeVersion);
        }
    } catch (Exception e) {
        versionInfo.append("\nLucee Version: Error retrieving version - ").append(e.getMessage());
    }
    return versionInfo.toString();
    // Returns: "LuCLI 0.1.207-SNAPSHOT\nLucee Version: 6.2.2.91"
}
```

---

## Analysis

### Usage Count
- **`getVersion()`**: 3 uses (Terminal, StringOutput, called by getVersionInfo)
- **`getVersionInfo()`**: 1 use (only called by getFullVersionInfo)
- **`getLuceeVersionInfo()`**: 2 uses (getFullVersionInfo, Terminal.showLuceeVersion)
- **`getFullVersionInfo()`**: 2 uses (Terminal version command, VersionProvider)

### Problems
1. **Over-abstraction**: `getVersionInfo()` is only used once, just adds "LuCLI " prefix
2. **Inconsistent formatting**: Some add labels, some don't
3. **Confusing names**: "Info" vs "Full" - which includes what?
4. **Maintenance burden**: 4 methods to maintain for simple string formatting

---

## Proposed Solution

**Consolidate to 2 methods with clear purposes:**

```java
/**
 * Get the LuCLI version number from the manifest
 * @return Version string (e.g., "0.1.207-SNAPSHOT")
 */
public static String getVersion() {
    // Existing implementation - unchanged
    // Used when you just need the version number
}

/**
 * Get complete version information (LuCLI + Lucee)
 * @param includeLucee Whether to include Lucee version
 * @return Formatted version string with labels
 */
public static String getVersionInfo(boolean includeLucee) {
    StringBuilder info = new StringBuilder();
    info.append("LuCLI ").append(getVersion());
    
    if (includeLucee) {
        try {
            String luceeVersion = LuceeScriptEngine.getInstance(false, false).getVersion();
            info.append("\nLucee Version: ").append(luceeVersion);
        } catch (Exception e) {
            info.append("\nLucee Version: Error - ").append(e.getMessage());
        }
    }
    
    return info.toString();
}
```

---

## Migration Plan

### Current Usages → New API

| Current Call | New Call | Why |
|-------------|----------|-----|
| `getVersion()` | `getVersion()` | No change - still need raw version |
| `getVersionInfo()` | `getVersionInfo(false)` | Just LuCLI version with label |
| `getLuceeVersionInfo()` | Extract Lucee logic inline | Only 1 external use |
| `getFullVersionInfo()` | `getVersionInfo(true)` | Both versions |

### Specific Changes

#### 1. Terminal.java Line 114 (Welcome message)
```java
// Current
terminal.writer().println("🚀 LuCLI Terminal " + LuCLI.getVersion() + "...");

// After (no change needed)
terminal.writer().println("🚀 LuCLI Terminal " + LuCLI.getVersion() + "...");
```

#### 2. Terminal.java Line 198 (version command)
```java
// Current
return LuCLI.getFullVersionInfo();

// After
return LuCLI.getVersionInfo(true);
```

#### 3. Terminal.java showLuceeVersion() method
```java
// Current
private static String showLuceeVersion() {
    try {
        return LuCLI.getLuceeVersionInfo();
    } catch (Exception e) {
        return "Error retrieving Lucee version: " + e.getMessage();
    }
}

// After - inline the logic
private static String showLuceeVersion() {
    try {
        String luceeVersion = LuceeScriptEngine.getInstance(false, false).getVersion();
        return "Lucee Version: " + luceeVersion;
    } catch (Exception e) {
        return "Lucee Version: Error - " + e.getMessage();
    }
}
```

#### 4. LuCLIVersionProvider.java Line 14
```java
// Current
return new String[]{ LuCLI.getFullVersionInfo() };

// After
return new String[]{ LuCLI.getVersionInfo(true) };
```

#### 5. StringOutput.java Line 90 (placeholder)
```java
// Current (no change needed)
placeholderReplacements.put("LUCLI_VERSION", () -> LuCLI.getVersion());
```

---

## Alternative: Even Simpler

If we want **maximum simplicity**, we could go with just **one** flexible method:

```java
/**
 * Get version information with optional components
 * @param includeLabel Whether to include "LuCLI " prefix
 * @param includeLucee Whether to include Lucee version
 * @return Formatted version string
 */
public static String getVersionInfo(boolean includeLabel, boolean includeLucee) {
    StringBuilder info = new StringBuilder();
    
    if (includeLabel) {
        info.append("LuCLI ");
    }
    info.append(getVersion());
    
    if (includeLucee) {
        try {
            String luceeVersion = LuceeScriptEngine.getInstance(false, false).getVersion();
            info.append("\nLucee Version: ").append(luceeVersion);
        } catch (Exception e) {
            info.append("\nLucee Version: Error - ").append(e.getMessage());
        }
    }
    
    return info.toString();
}
```

Then usage becomes:
- `getVersion()` - Raw version (for internal use)
- `getVersionInfo(true, false)` - "LuCLI 0.1.207-SNAPSHOT"
- `getVersionInfo(true, true)` - Full version info
- `getVersionInfo(false, false)` - Same as getVersion() but consistent API

---

## Recommendation

**Go with the 2-method approach:**
1. Keep `getVersion()` - needed for raw version access
2. Replace other 3 methods with `getVersionInfo(boolean includeLucee)`

**Reasoning:**
- Simple and clear
- Easy to understand usage: `getVersionInfo(true)` or `getVersionInfo(false)`
- Reduces from 4 methods to 2
- No boolean soup (only 1 parameter)

---

## Benefits

1. **Fewer methods**: 4 → 2 (50% reduction)
2. **Clearer intent**: Name + parameter tells you what you get
3. **Less maintenance**: One place to format version strings
4. **Flexible**: Easy to add more options later if needed
5. **Backward compatible path**: Old code can be migrated incrementally

---

## Impact

### Lines of Code
- **Remove:** ~20 lines (2 methods + their calls)
- **Modify:** ~10 lines (update call sites)
- **Net:** -10 lines

### Risk
**Low** - Simple string formatting, no business logic

### Testing
All existing tests should pass after migration (version format unchanged)

---

## Implementation Steps

1. Add new `getVersionInfo(boolean includeLucee)` method
2. Update call sites one by one:
   - Terminal.java (2 places)
   - LuCLIVersionProvider.java (1 place)
3. Mark old methods as `@Deprecated` temporarily
4. Run tests to verify
5. Remove deprecated methods
6. Commit

---

## Example Final API

```java
public class LuCLI {
    
    /**
     * Get the LuCLI version number
     * @return Version from manifest (e.g., "0.1.207-SNAPSHOT")
     */
    public static String getVersion() {
        // Existing implementation
    }
    
    /**
     * Get formatted version information
     * @param includeLucee Whether to include Lucee version
     * @return Formatted version string with labels
     * 
     * Examples:
     *   getVersionInfo(false) -> "LuCLI 0.1.207-SNAPSHOT"
     *   getVersionInfo(true)  -> "LuCLI 0.1.207-SNAPSHOT\nLucee Version: 6.2.2.91"
     */
    public static String getVersionInfo(boolean includeLucee) {
        StringBuilder info = new StringBuilder();
        info.append("LuCLI ").append(getVersion());
        
        if (includeLucee) {
            try {
                String luceeVersion = LuceeScriptEngine.getInstance(false, false).getVersion();
                info.append("\nLucee Version: ").append(luceeVersion);
            } catch (Exception e) {
                info.append("\nLucee Version: Error - ").append(e.getMessage());
            }
        }
        
        return info.toString();
    }
}
```

---

## Questions to Consider

1. **Should we keep `getVersion()` public?**
   - Yes - useful for placeholder substitution and welcome message
   
2. **Should we add more options (e.g., OS, Java version)?**
   - No - keep it simple for now, those are available via placeholders

3. **Should version info be cached?**
   - No - it's fast enough and rarely called

---

## Decision

**Approved?** Awaiting your input before implementation.

Would you like me to implement this consolidation?
