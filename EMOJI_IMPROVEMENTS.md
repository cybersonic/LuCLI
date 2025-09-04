# Emoji Handling Improvements for Windows Terminals

## Overview

This document describes the improvements made to LuCLI's emoji handling system to provide better compatibility with Windows terminals, particularly those that don't properly support Unicode emoji characters.

## Problem

Previously, LuCLI had limited emoji handling for Windows systems:
- Emojis were disabled for all Windows systems by default
- The emoji removal function was too simple and missed many Unicode characters
- No mechanism for users to easily test or configure emoji support
- No automatic detection of modern Windows terminals that do support emojis

## Solution

### 1. Enhanced Windows Terminal Detection (`WindowsCompatibility.java`)

**Improved `supportsEmojis()` method:**
- **Windows Terminal**: Auto-detected via `WT_SESSION` environment variable
- **VS Code Terminal**: Detected via `VSCODE_INJECTION` and `TERM_PROGRAM` environment variables
- **ConEmu/cmder**: Detected via `ConEmuPID` and `CMDER_ROOT` environment variables
- **Git Bash/MSYS2/Cygwin**: Detected via `TERM` environment variable patterns
- **PowerShell 6+**: Detected via `PSVersion` environment variable
- **Modern Console Host**: Windows 10+ with modern Unicode support

**New utility class `TerminalCapabilities`:**
- Individual detection methods for specific terminal types
- Emoji support scoring system (0-10)
- Human-readable terminal descriptions
- Comprehensive diagnostic reporting

### 2. Comprehensive Emoji Removal (`PromptConfig.java`)

**Enhanced `removeEmojis()` method:**
- Removes Unicode emoji categories:
  - Miscellaneous Symbols and Dingbats (`U+2600-U+26FF`, `U+2700-U+27BF`)
  - Miscellaneous Symbols and Pictographs (`U+1F300-U+1F5FF`)
  - Emoticons (`U+1F600-U+1F64F`)
  - Transport and Map Symbols (`U+1F680-U+1F6FF`)
  - Supplemental Symbols and Pictographs (`U+1F900-U+1F9FF`)
  - Symbols and Pictographs Extended-A (`U+1FA70-U+1FAFF`)
  - Arrows (`U+2194-U+21AA`)
  - Miscellaneous Symbols and Arrows (`U+2B00-U+2BFF`)
  - CJK symbols (`U+3030`, `U+303D`, `U+3297`, `U+3299`)
  - Variation selectors (`U+FE00-U+FE0F`)
  - Zero-width joiners (`U+200D`)
  - Unicode property classes (`\p{So}`, `\p{Sk}`, `\p{Sm}`)
  - Common problematic arrows and geometric shapes

### 3. Auto-Detection in Settings (`Settings.java`)

**Intelligent default settings:**
- Emoji support auto-detected on first run using `WindowsCompatibility.supportsEmojis()`
- Color support auto-detected using `WindowsCompatibility.supportsColors()`
- Settings persisted to `~/.lucli/settings.json`

**New methods:**
- `setShowEmojis(boolean)` for programmatic control
- `supportsColors()` for color capability checking

### 4. Enhanced Prompt Command (`CommandProcessor.java`)

**New `prompt` subcommands:**

#### `prompt emoji [status|on|off|test]`
- `prompt emoji` - Show current emoji status and terminal support
- `prompt emoji on` - Enable emoji display
- `prompt emoji off` - Disable emoji display (fallback to text symbols)
- `prompt emoji test` - Display test emoji characters

#### `prompt terminal`
- Shows comprehensive terminal capability report
- Displays detected terminal type, OS version, environment variables
- Shows emoji and color support status with scoring

**Improved compatibility:**
- All emoji output uses `WindowsCompatibility.getEmoji()` with fallbacks
- Graceful degradation for unsupported terminals

## Usage Examples

### Check Terminal Capabilities
```bash
prompt terminal
```
Output:
```
Terminal Capabilities Report:

Terminal: Windows Terminal
Emoji Support: Yes (Score: 10/10)
Color Support: Yes
OS: Windows 10
OS Version: 10.0
TERM: xterm-256color
```

### Manage Emoji Settings
```bash
# Check current status
prompt emoji

# Enable emojis
prompt emoji on

# Disable emojis
prompt emoji off

# Test emoji display
prompt emoji test
```

### Expected Behavior by Terminal Type

| Terminal | Emoji Support | Auto-Detected | Score |
|----------|---------------|---------------|-------|
| Windows Terminal | ✅ Yes | ✅ Yes | 10/10 |
| VS Code Integrated | ✅ Yes | ✅ Yes | 9/10 |
| ConEmu/cmder | ✅ Yes | ✅ Yes | 8/10 |
| Git Bash/MSYS2 | ✅ Yes | ✅ Yes | 9/10 |
| PowerShell 7+ | ✅ Yes | ✅ Yes | 7/10 |
| PowerShell 6+ | ✅ Yes | ✅ Yes | 6/10 |
| Windows 10+ Console | ⚠️ Limited | ✅ Yes | 4/10 |
| Legacy Console | ❌ No | ✅ Yes | 1/10 |
| Dumb Terminal | ❌ No | ✅ Yes | 0/10 |

## Technical Implementation

### Environment Variables Checked
- `WT_SESSION` - Windows Terminal
- `VSCODE_INJECTION`, `TERM_PROGRAM` - VS Code
- `ConEmuPID`, `CMDER_ROOT` - ConEmu/cmder
- `TERM` - Terminal type identification
- `COLORTERM` - Color capabilities
- `PSVersion` - PowerShell version
- `ANSICON` - ANSI support indicator

### Fallback Behavior
When emojis are disabled or unsupported:
- `🎨` → `[PROMPTS]`
- `✅` → `[OK]`
- `❌` → `[ERROR]`
- `⚠️` → `[WARNING]`
- `💡` → `[TIP]`
- `🔧` → `[CMD]`
- `📁` → `[DIR]`

## Benefits

1. **Better User Experience**: Emojis work properly on supported terminals, gracefully degrade on others
2. **Automatic Detection**: No manual configuration needed in most cases
3. **Easy Troubleshooting**: Diagnostic commands help users understand their terminal capabilities
4. **Comprehensive Coverage**: Handles wide range of Windows terminal environments
5. **User Control**: Manual override options for edge cases
6. **Future-Proof**: Easy to extend for new terminal types

## Files Modified

- `src/main/java/org/lucee/lucli/WindowsCompatibility.java` - Enhanced emoji/terminal detection
- `src/main/java/org/lucee/lucli/PromptConfig.java` - Improved emoji removal
- `src/main/java/org/lucee/lucli/Settings.java` - Auto-detection and new settings methods  
- `src/main/java/org/lucee/lucli/CommandProcessor.java` - Enhanced prompt command with emoji management
- `src/main/java/org/lucee/lucli/StringOutput.java` - NEW: Centralized output post-processor with emoji handling
- `src/main/resources/script_engine/*.cfs` - CFML script templates with emoji placeholder support

## Testing

The solution has been tested with:
- Build compilation (Maven): ✅ Success
- Code compilation: ✅ No errors
- Method signatures: ✅ Compatible with existing code
- Backward compatibility: ✅ Existing settings preserved

## StringOutput Integration

The emoji system now integrates with the new StringOutput post-processor:

### Centralized Emoji Handling
- **Placeholder System**: Use `${EMOJI_SUCCESS}`, `${EMOJI_ERROR}` etc. in output strings
- **Automatic Processing**: All output goes through emoji detection and replacement
- **Consistent Fallbacks**: Same fallback logic applied across all output

### Usage in Code
```java
// Old way
System.out.println(WindowsCompatibility.getEmoji("✅", "[OK]") + " Success!");

// New way
StringOutput.Quick.success("Success!");
// or
StringOutput.getInstance().println("${EMOJI_SUCCESS} Success!");
```

### Usage in CFML Templates
```cfml
// In externalized .cfs script templates
writeOutput('${EMOJI_ERROR} CFML Error: ' & e.message);
writeOutput('${EMOJI_WARNING} Main function not found');
```

## Future Enhancements

- Support for additional terminal emulators
- More granular emoji category controls
- User-customizable emoji→text mappings
- Terminal capability caching for performance
- Color placeholder support in StringOutput system
