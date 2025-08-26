# 🎨 LuCLI JSON-Based Prompt System

LuCLI features a flexible JSON-based prompt configuration system that allows you to use built-in prompts or create completely custom ones. All prompts are now configured through JSON files, making them easy to customize and extend.

## 📋 Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [Built-in Prompts](#built-in-prompts)
- [Creating Custom Prompts](#creating-custom-prompts)
- [Managing Prompts](#managing-prompts)
- [Advanced Configuration](#advanced-configuration)
- [Migration Guide](#migration-guide)

## 🌟 Overview

The new JSON-based LuCLI prompt system provides:
- **Fully JSON-configurable prompts** - All prompts defined in JSON format
- **8+ built-in prompt templates** bundled as resources
- **Unlimited custom prompts** - Create your own in `~/.lucli/prompts/`
- **Dynamic placeholders** - `{path}`, `{time}`, `{git}` support
- **Automatic discovery** - Both built-in and custom prompts auto-detected
- **Template inheritance** - User prompts coexist with built-ins
- **Backward compatibility** - Existing settings continue to work

## ⚙️ How It Works

1. **Built-in Templates**: LuCLI ships with JSON prompt templates bundled as resources
2. **User Templates**: Create custom prompts by adding JSON files to `~/.lucli/prompts/`
3. **Smart Loading**: System loads built-ins first, then discovers user templates
4. **Live Templates**: Copy built-in templates to user directory for customization

## 🎯 Built-in Prompts

LuCLI includes these built-in prompt templates bundled as JSON resources:

| Name | Description | Example Output |
|------|-------------|----------------|
| `default` | Default LuCLI prompt | `🔧 lucli:~/Code$ ` |
| `minimal` | Simple minimal prompt | `$ ` |
| `zsh` | Zsh-style prompt | `~/Code ❯ ` |
| `time` | Shows current time | `[14:32:15] ~/Code $ ` |
| `colorful` | Colorful with emoji | `🌈 ~/Code ➤ ` |
| `dev` | Developer-focused with git | `💻 ~/Code [main]$ ` |
| `space` | Space-themed | `🚀 ~/Code » ` |
| `electric` | Electric terminal theme | `⚡ lucli:~/Code ⚡ ` |

### Built-in Template Details

Each built-in prompt is defined by a JSON file like this:

**default.json**:
```json
{
  "name": "default",
  "description": "Default LuCLI prompt",
  "template": "🔧 lucli:{path}$ ",
  "showPath": true,
  "showTime": false,
  "showGit": false,
  "useEmoji": true
}
```

## 🚀 Using the Prompt System

### Viewing Available Prompts
To see all available prompt styles:
```bash
prompt
```

This displays:
```
🎨 Available prompt styles:
• default - Classic LuCLI prompt
• minimal - Clean and simple
• zsh - ZSH-style with arrow
• time - Includes timestamp
• colorful - Colorful with emojis
• dev - Developer-focused
• space - Space theme with rocket
• electric - Electric theme

Current: default
Use 'prompt <style>' to change your prompt style.
```

### Switching Prompt Styles
To change your prompt style:
```bash
prompt <style-name>
```

Examples:
```bash
prompt zsh          # Switch to ZSH-style prompt
prompt colorful     # Switch to colorful emoji prompt
prompt minimal      # Switch to minimal prompt
```

Success message:
```
✨ Prompt style changed to 'zsh'! Settings saved.
```

### Invalid Style Handling
If you specify an invalid style:
```bash
prompt invalid-style
```

You'll see:
```
❌ Unknown prompt style 'invalid-style'. Use 'prompt' to see available styles.
```

## 📁 Configuration Files

### Settings File
- **Location**: `~/.lucli/settings.json`
- **Purpose**: Stores your current prompt preference and other LuCLI settings
- **Format**: JSON

Example `settings.json`:
```json
{
  "currentPrompt": "zsh",
  "promptsDirectory": "/Users/username/.lucli/prompts"
}
```

### Prompts Directory
- **Location**: `~/.lucli/prompts/`
- **Purpose**: Contains individual prompt template files
- **Files**: Each style has its own `.json` file (e.g., `zsh.json`, `colorful.json`)

Example prompt file (`zsh.json`):
```json
{
  "name": "zsh",
  "description": "ZSH-style with arrow",
  "template": "➜ {directory} ",
  "showFullPath": false,
  "emoji": "➜"
}
```

## 🛠️ Creating Custom Prompts

### Step 1: Create the JSON File

Create a JSON file in `~/.lucli/prompts/` with your prompt name, for example `~/.lucli/prompts/my-custom.json`:

```json
{
  "name": "my-custom",
  "description": "My custom prompt with time and git",
  "template": "[{time}] 🔥 {path} {git}➡️ ",
  "showPath": true,
  "showTime": true,
  "showGit": true,
  "useEmoji": true
}
```

### Step 2: Available Placeholders

You can use these placeholders in your `template` string:

- `{path}` - Current directory path (requires `showPath: true`)
- `{time}` - Current time HH:mm:ss (requires `showTime: true`)  
- `{git}` - Git branch info like `[main] ` (requires `showGit: true`)

### Step 3: Configuration Options

| Field | Type | Description |
|-------|------|-----------|
| `name` | string | Unique name for your prompt |
| `description` | string | Human-readable description |
| `template` | string | The prompt template with placeholders |
| `showPath` | boolean | Enable `{path}` placeholder |
| `showTime` | boolean | Enable `{time}` placeholder |
| `showGit` | boolean | Enable `{git}` placeholder |
| `useEmoji` | boolean | Whether prompt uses emojis (for emoji filtering) |

### Custom Prompt Examples

#### Simple Custom Prompt
```json
{
  "name": "simple",
  "description": "Simple path and arrow",
  "template": "{path} → ",
  "showPath": true,
  "showTime": false,
  "showGit": false,
  "useEmoji": false
}
```

#### Advanced Developer Prompt
```json
{
  "name": "advanced-dev", 
  "description": "Full-featured developer prompt",
  "template": "🔥 [{time}] {path} {git}⚡ ",
  "showPath": true,
  "showTime": true, 
  "showGit": true,
  "useEmoji": true
}
```

#### Minimalist Time Prompt
```json
{
  "name": "time-minimal",
  "description": "Just time and dollar sign",
  "template": "[{time}]$ ",
  "showPath": false,
  "showTime": true,
  "showGit": false,
  "useEmoji": false
}
```

## 💼 Managing Prompts

### Switching Prompts
Use the LuCLI settings system to change prompts:
```bash
lucli config set currentPrompt my-custom
```

### Listing Available Prompts
The system will automatically discover both built-in and custom prompts. You can programmatically access:
- `promptConfig.getBuiltinTemplateNames()` - Built-in prompts only
- `promptConfig.getCustomTemplateNames()` - User-defined prompts only  
- `promptConfig.getAvailableTemplateNames()` - All available prompts

### Template Priority
1. Built-in prompts are loaded first from JAR resources
2. Custom prompts in `~/.lucli/prompts/` are then discovered
3. Custom prompts with the same name as built-in prompts are treated as separate templates
4. User files do NOT override built-in prompts (they coexist)

### Directory Structure

```
~/.lucli/
├── settings.json          # Main settings file
└── prompts/               # Custom prompt directory
    ├── my-custom.json     # Your custom prompts
    ├── work-prompt.json   # Work-specific prompt
    └── ...
```

## 🔄 Migration Guide

### From Hardcoded to JSON-Based System

The system maintains backward compatibility:

1. **Existing Settings**: Your current `currentPrompt` setting continues to work
2. **Same Names**: Built-in prompts have the same names as before
3. **Same Behavior**: All existing functionality is preserved
4. **Auto-Creation**: Built-in prompts are auto-created as JSON files in `~/.lucli/prompts/` for reference

### What Changed

#### Before (Hardcoded):
- Prompts were defined in Java code
- Limited to built-in templates
- Required code changes to add new prompts

#### After (JSON-Based):
- All prompts defined in JSON format
- Built-ins loaded from JAR resources
- Unlimited custom prompts via user files
- Easy template modification and creation

### Technical Details

#### Resource Loading
- Built-in prompts are loaded from `/prompts/*.json` resources in the JAR
- User prompts are loaded from `~/.lucli/prompts/*.json` files
- Templates are cached for performance

#### Placeholder Processing
- Placeholders are replaced at render time, not load time
- Git information is detected by walking up directory tree for `.git` folder
- Time formatting uses `HH:mm:ss` format
- Path formatting uses the existing `FileSystemState.getDisplayPath()` logic

#### Error Handling
- Invalid JSON files are logged as warnings but don't break the system
- Missing placeholders are left as-is in the template
- Missing built-in resources fall back to hardcoded defaults
- The system always ensures a "default" prompt is available

## 🔧 Technical Implementation

### Core Classes

#### `PromptConfig`
- Manages prompt templates and generation
- Handles loading/saving prompt configurations
- Provides template variable substitution

#### `Settings`
- Manages persistent configuration storage
- Handles JSON serialization/deserialization
- Creates default configurations

### Key Methods

#### `generatePrompt(FileSystemState state)`
Generates the actual prompt string by:
1. Loading the current prompt template
2. Substituting template variables with current values
3. Returning the formatted prompt string

#### `setCurrentPrompt(String promptName)`
Changes the active prompt by:
1. Validating the prompt exists
2. Updating the current prompt setting
3. Saving settings to disk

### File System Integration
The prompt system integrates with LuCLI's file system state to provide:
- Current directory information
- Path display options (full path vs directory name)
- Dynamic updates as you navigate

## 🎨 Customization Tips

### Visual Elements
- Use emojis sparingly for better terminal compatibility
- Consider different terminal color schemes when designing
- Test prompts with long directory names

### Information Display
- Balance information density with readability
- Consider your workflow needs (timestamps, paths, etc.)
- Keep prompts concise to leave room for commands

### Performance
- Prompt generation is optimized for speed
- Template variables are cached where possible
- File system queries are minimal and efficient

## 🚀 Future Enhancements

Planned features for the prompt system:
- **Git branch integration** - Show current Git branch in prompts
- **Exit code indicators** - Visual feedback for command success/failure  
- **Custom color support** - ANSI color codes in prompt templates
- **Conditional templates** - Different prompts based on context
- **Plugin system** - Third-party prompt extensions

---

*For more information about LuCLI, see the main README.md file.*
