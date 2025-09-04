# 🔄 StringOutput Post-Processing System

## Overview

The StringOutput system is LuCLI's centralized output post-processor that provides consistent handling of all application output with advanced features like emoji replacement, placeholder substitution, and terminal compatibility detection.

## 🎯 Key Features

### Centralized Output Management
- **Singleton Pattern**: One instance manages all output processing
- **Stream Configuration**: Configurable output and error streams
- **Processing Control**: Enable/disable post-processing as needed
- **Thread Safety**: Synchronized operations for concurrent access

### Smart Emoji Handling
- **Terminal Detection**: Automatic detection of emoji-capable terminals
- **Graceful Fallback**: Text symbols when emojis aren't supported
- **WindowsCompatibility Integration**: Leverages existing terminal detection
- **Extensive Emoji Library**: 20+ predefined emoji placeholders

### Advanced Placeholder System
- **Time Placeholders**: `${NOW}`, `${DATE}`, `${TIME}`, `${TIMESTAMP}`
- **System Info**: `${USER_HOME}`, `${OS_NAME}`, `${JAVA_VERSION}`
- **LuCLI Context**: `${LUCLI_VERSION}`, `${LUCLI_HOME}`, `${WORKING_DIR}`
- **Environment Variables**: `${ENV_PATH}`, `${ENV_USERNAME}`, etc.
- **Dynamic Time Formats**: `${TIME_yyyy-MM-dd HH:mm:ss}`
- **Custom Placeholders**: Extensible system for adding new types

## 📋 Usage Examples

### Basic Output
```java
StringOutput out = StringOutput.getInstance();
out.println("${EMOJI_SUCCESS} Operation completed successfully!");
out.println("Working directory: ${WORKING_DIR}");
out.println("Current time: ${NOW}");
```

### Quick Convenience Methods
```java
StringOutput.Quick.success("Module created successfully");
StringOutput.Quick.error("Configuration file not found");
StringOutput.Quick.warning("Using default settings");
StringOutput.Quick.info("Processing 15 files");
StringOutput.Quick.tip("Use --verbose for more details");
```

### Custom Status Messages
```java
StringOutput.Quick.status("PROCESSING", "Analyzing CFML files...");
StringOutput.Quick.status("COMPLETE", "Analysis finished");
```

## 🎨 Placeholder Reference

### Emoji Placeholders
| Placeholder | Emoji | Fallback | Usage |
|-------------|-------|----------|-------|
| `${EMOJI_SUCCESS}` | ✅ | `[OK]` | Success messages |
| `${EMOJI_ERROR}` | ❌ | `[ERROR]` | Error messages |
| `${EMOJI_WARNING}` | ⚠️ | `[WARNING]` | Warnings |
| `${EMOJI_INFO}` | ℹ️ | `[INFO]` | Information |
| `${EMOJI_ROCKET}` | 🚀 | `""` | Application startup |
| `${EMOJI_FOLDER}` | 📁 | `""` | Directory operations |
| `${EMOJI_COMPUTER}` | 💻 | `[CMD]` | Command execution |
| `${EMOJI_TOOL}` | 🔧 | `[TOOL]` | Configuration |
| `${EMOJI_ART}` | 🎨 | `[STYLE]` | Theming/prompts |
| `${EMOJI_WAVE}` | 👋 | `[BYE]` | Goodbye messages |
| `${EMOJI_BULB}` | 💡 | `[TIP]` | Tips and hints |
| `${EMOJI_FIRE}` | 🔥 | `[HOT]` | Performance/speed |
| `${EMOJI_STAR}` | ⭐ | `[*]` | Highlights |
| `${EMOJI_GEAR}` | ⚙️ | `[CONFIG]` | Settings |
| `${EMOJI_LIGHTNING}` | ⚡ | `[FAST]` | Speed indicators |
| `${EMOJI_SHIELD}` | 🛡️ | `[SECURE]` | Security features |
| `${EMOJI_MAGNIFYING_GLASS}` | 🔍 | `[SEARCH]` | Search operations |
| `${EMOJI_PACKAGE}` | 📦 | `[PKG]` | Package management |

### System Information Placeholders
| Placeholder | Description | Example |
|-------------|-------------|---------|
| `${NOW}` | ISO date-time | `2025-01-04T13:22:25` |
| `${DATE}` | ISO date | `2025-01-04` |
| `${TIME}` | ISO time | `13:22:25.123` |
| `${TIMESTAMP}` | Unix timestamp | `1735993345000` |
| `${USER_HOME}` | User home directory | `C:\Users\markd` |
| `${USER_NAME}` | Current username | `markd` |
| `${WORKING_DIR}` | Current working directory | `C:\Users\markd\Code\LuCLI` |
| `${OS_NAME}` | Operating system | `Windows 10` |
| `${JAVA_VERSION}` | Java version | `17.0.2` |
| `${LUCLI_VERSION}` | LuCLI version | `0.0.12-SNAPSHOT` |
| `${LUCLI_HOME}` | LuCLI home directory | `C:\Users\markd\.lucli` |

### Environment Variable Placeholders
| Pattern | Description | Example |
|---------|-------------|---------|
| `${ENV_PATH}` | PATH environment variable | `C:\Windows\system32;...` |
| `${ENV_USERNAME}` | USERNAME environment variable | `markd` |
| `${ENV_COMPUTERNAME}` | COMPUTERNAME environment variable | `DESKTOP-ABC123` |
| `${ENV_*}` | Any environment variable | Use `ENV_` prefix |

### Dynamic Time Format Placeholders
| Pattern | Description | Example |
|---------|-------------|---------|
| `${TIME_HH:mm:ss}` | Custom time format | `13:22:25` |
| `${TIME_yyyy-MM-dd}` | Custom date format | `2025-01-04` |
| `${TIME_MMM dd, yyyy}` | Verbose date | `Jan 04, 2025` |

## 🔧 Advanced Configuration

### Custom Placeholder Registration
```java
StringOutput out = StringOutput.getInstance();

// Static value
out.addPlaceholder("PROJECT_NAME", "LuCLI");

// Dynamic value with supplier
out.addPlaceholder("ACTIVE_SERVERS", () -> {
    return String.valueOf(getActiveServerCount());
});

// Usage
out.println("Project: ${PROJECT_NAME} has ${ACTIVE_SERVERS} running servers");
```

### Stream Configuration
```java
StringOutput out = StringOutput.getInstance();

// Redirect to custom streams
out.setOutputStream(customOutputStream);
out.setErrorStream(customErrorStream);

// Temporarily disable processing
out.setPostProcessingEnabled(false);
out.println("Raw output without processing");
out.setPostProcessingEnabled(true);
```

## 🎨 Integration with Script Templates

The externalized CFML script templates in `src/main/resources/script_engine/` use StringOutput placeholders:

### Example: cfmlOutput.cfs
```cfml
try {
    result = ${cfmlExpression};
    // ... process result ...
} catch (any e) {
    writeOutput('${EMOJI_ERROR} CFML Error: ' & e.message);
    if (len(e.detail)) {
        writeOutput(' - ' & e.detail);
    }
}
```

### Example: componentWrapper.cfs
```cfml
try {
    ${componentInstantiation}
    // ... component execution ...
} catch (any e) {
    writeOutput('${EMOJI_ERROR} Component execution error: ' & e.message & chr(10));
    writeOutput('${EMOJI_INFO} Detail: ' & e.detail & chr(10));
}
```

## 🚀 Terminal Compatibility

### Emoji Support Detection
StringOutput integrates with WindowsCompatibility to provide smart emoji handling:

```java
// Automatic detection
boolean hasEmojis = WindowsCompatibility.supportsEmojis();

// Emoji placeholders automatically fall back based on terminal
String message = "Operation ${EMOJI_SUCCESS} completed";
// Windows Terminal: "Operation ✅ completed"
// Legacy Console: "Operation [OK] completed"
```

### Supported Terminals
| Terminal | Emoji Support | Detection Method |
|----------|---------------|------------------|
| Windows Terminal | ✅ Full | `WT_SESSION` env var |
| VS Code Terminal | ✅ Full | `VSCODE_INJECTION` env var |
| ConEmu/cmder | ✅ Full | `ConEmuPID` env var |
| Git Bash/MSYS2 | ✅ Full | `TERM` env var patterns |
| PowerShell 6+ | ✅ Full | `PSVersion` env var |
| Legacy Console | ⚠️ Limited | OS version detection |
| Dumb Terminal | ❌ None | `TERM=dumb` |

## 📁 File Structure

### Core Classes
```
src/main/java/org/lucee/lucli/
├── StringOutput.java           # Main post-processor class
├── WindowsCompatibility.java   # Terminal detection integration
└── ...
```

### Script Templates
```
src/main/resources/script_engine/
├── cfmlOutput.cfs              # CFML expression evaluation
├── componentWrapper.cfs        # CFC component execution
├── moduleDirectExecution.cfs   # Direct module execution
├── componentToScript.cfs       # Component conversion
├── lucliMappings.cfs          # Component mappings
└── executeComponent.cfs        # General component execution
```

## 🔄 Processing Flow

1. **Input**: String with placeholders (e.g., `"${EMOJI_SUCCESS} Done!"`)
2. **Placeholder Detection**: Regex pattern matching `${placeholder}`
3. **Value Resolution**: Look up replacement values
4. **Emoji Processing**: Apply terminal-specific emoji handling
5. **Output**: Processed string ready for display

### Processing Order
1. Direct placeholder lookup (`${EMOJI_SUCCESS}`)
2. Environment variable expansion (`${ENV_PATH}`)
3. Time format processing (`${TIME_HH:mm:ss}`)
4. Dynamic emoji resolution (`${EMOJI_*}`)
5. Legacy emoji pattern replacement (backward compatibility)

## 📊 Performance Considerations

### Optimizations
- **Singleton Pattern**: One instance reduces memory overhead
- **Lazy Evaluation**: Placeholders computed only when needed
- **Caching**: Repeated placeholders reuse computed values
- **Compiled Patterns**: Regex patterns compiled once

### Best Practices
- Use `StringOutput.Quick` methods for common patterns
- Cache StringOutput instance reference for frequent use
- Disable processing for high-volume debug output
- Use specific placeholders rather than generic ones

## 🧪 Testing and Debugging

### Enable Debug Output
```java
StringOutput out = StringOutput.getInstance();
out.println("Debug: Processing ${TIMESTAMP} with ${OS_NAME}");
```

### Test Placeholder Resolution
```java
String processed = StringOutput.getInstance().process("Test: ${EMOJI_SUCCESS} ${NOW}");
System.out.println("Processed: " + processed);
```

### Verify Terminal Capabilities
```bash
# In LuCLI terminal
prompt terminal
prompt emoji test
```

## 🔮 Future Enhancements

### Planned Features
- **Color Placeholders**: `${COLOR_RED}`, `${COLOR_RESET}` for ANSI colors
- **Conditional Placeholders**: `${IF_EMOJI:✅:OK}` syntax
- **Nested Placeholders**: `${TIME_${DATE_FORMAT}}` dynamic formats
- **Performance Metrics**: `${TIMING_*}` placeholders for performance data
- **Localization**: Language-specific placeholder values
- **Template Caching**: Cache processed templates for better performance

### Extension Points
- Custom placeholder suppliers via functional interfaces
- Plugin system for third-party placeholder providers
- Theme-aware placeholder resolution
- Context-sensitive placeholder values

## 📚 Migration Guide

### From Direct System.out Calls
```java
// Before
System.out.println("✅ Success!");
System.err.println("❌ Error occurred");

// After
StringOutput.Quick.success("Success!");
StringOutput.Quick.error("Error occurred");
```

### From WindowsCompatibility Direct Usage
```java
// Before
System.out.println(WindowsCompatibility.getEmoji("✅", "[OK]") + " Done");

// After
StringOutput.getInstance().println("${EMOJI_SUCCESS} Done");
```

### Updating Existing Messages
1. Replace hardcoded emojis with placeholders
2. Use StringOutput methods instead of System.out
3. Add meaningful placeholder values
4. Test on different terminal types

---

*For more information about LuCLI's output system, see EMOJI_IMPROVEMENTS.md and WindowsCompatibility documentation.*
