# Lang Module

A LuCLI module for managing language and locale settings. This module allows users to view current language settings, list available languages, and get instructions for switching between supported languages.

## Features

- 🌐 **Multi-language Support**: English, Spanish (Español), and French (Français)
- 🔍 **Current Locale Detection**: Shows current system and LuCLI locale settings
- 🗣️ **Language Listing**: Display all available languages with flags and native names
- ⚙️ **Switch Instructions**: Provides clear instructions for changing languages
- 🌍 **Environment Integration**: Works with LUCLI_LOCALE and LANG environment variables

## Usage

### Basic Commands

```bash
# Show current language and available languages
lucli lang

# List all available languages
lucli lang list
lucli lang ls

# Show current language settings
lucli lang current
lucli lang show

# Get instructions to set a language
lucli lang set es      # Spanish
lucli lang set fr      # French  
lucli lang set en      # English

# Show help
lucli lang help
```

### Language Switching

The module provides instructions for three ways to switch languages:

#### Option 1: Environment Variable (Recommended)
```bash
export LUCLI_LOCALE=es
lucli server start    # Will use Spanish
```

#### Option 2: Per-Command
```bash
LUCLI_LOCALE=fr lucli server start    # Use French for this command
```

#### Option 3: System Property
```bash
java -Dlucli.locale=es -jar lucli.jar server start
```

## Supported Languages

| Flag | Code | Language | Native Name |
|------|------|----------|-------------|
| 🇺🇸   | `en` | English  | English     |
| 🇪🇸   | `es` | Spanish  | Español     |
| 🇫🇷   | `fr` | French   | Français    |

## Examples

### Check Current Language
```bash
$ lucli lang current
🌐 Current Language Settings:
══════════════════════════════
Language Code: en
Country Code:  US
Locale:        en_US
Display Name:  English (United States)
LANG:          en_US.UTF-8
```

### List Available Languages
```bash
$ lucli lang list
🗣️  Available Languages:
═══════════════════════════
🇺🇸  en - English
🇪🇸  es - Español (Spanish)
🇫🇷  fr - Français (French)

💡 To switch language:
   lucli lang set <code>    # e.g., lucli lang set es
   LUCLI_LOCALE=<code>     # e.g., LUCLI_LOCALE=fr lucli ...
```

### Set Language to Spanish
```bash
$ lucli lang set es
🌐 Setting language to: es
════════════════════════════════════
Language: Español (Spanish)

💡 To use this language in LuCLI commands:

Option 1 - Environment Variable (recommended):
   export LUCLI_LOCALE=es
   lucli server start  # Will use Español (Spanish)

Option 2 - Per-command:
   LUCLI_LOCALE=es lucli server start

Option 3 - System Property:
   java -Dlucli.locale=es -jar lucli.jar server start

✅ Language preference noted! Use the commands above to apply it.
```

## How It Works

1. **Detection**: The module reads current locale settings from Java's `Locale.getDefault()`
2. **Environment Variables**: Checks `LUCLI_LOCALE`, `LANG`, and system properties
3. **Instructions**: Provides platform-appropriate commands for setting environment variables
4. **Integration**: Works with LuCLI's StringOutput internationalization framework

## Technical Details

- **File**: `Module.cfc` (CFML component)
- **Type**: Built-in LuCLI module
- **Location**: `src/main/resources/modules/lang/`
- **Dependencies**: None (uses built-in Java Locale classes)

## Extending Language Support

To add support for additional languages:

1. Add new language entries to the `languages` array in `Module.cfc`
2. Update the `supportedLanguages` array in the `setLanguage` function
3. Create corresponding `messages_[code].properties` files in `src/main/resources/messages/`
4. Update this README with the new language information

## Notes

- Language settings affect user interface messages throughout LuCLI
- Help text and documentation remain in English
- The module provides instructions rather than directly modifying environment variables for security reasons
- Changes take effect for new LuCLI command invocations

## Module Development

This module was created with LuCLI's module system and follows the standard module structure:
- `Module.cfc` - Main CFML component with business logic
- `module.json` - Module metadata and configuration
- `README.md` - Documentation and usage examples

Created by the LuCLI Team as part of the internationalization framework.
