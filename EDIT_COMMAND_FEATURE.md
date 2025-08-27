# Edit Command Feature

## Overview

The `edit` command provides seamless integration with system editors, allowing users to quickly edit files directly from the LuCLI terminal. This is particularly useful for editing configuration files like `lucee.json`, scripts, and other project files.

## Usage

```bash
edit <filename>
```

### Examples

```bash
# Edit existing files
edit lucee.json
edit src/main/java/MyClass.java
edit README.md

# Create and edit new files
edit config/new-config.json
edit ~/scripts/deploy.sh

# Edit files with tab completion
edit lu<TAB>  # Completes to lucee.json
edit src/<TAB>  # Shows available files in src/
```

## Features

### 🎯 **Smart Editor Detection**
The command automatically detects and uses the best available system editor in this order of preference:

1. **Environment Variable**: Uses `$EDITOR` if set
2. **VS Code**: `code` (if available)
3. **Nano**: `nano` (user-friendly for beginners)
4. **Vim**: `vim` (if available)
5. **Vi**: `vi` (fallback)
6. **Emacs**: `emacs` (if available)
7. **Gedit**: `gedit` (GNOME Text Editor)
8. **Notepad**: `notepad` (Windows)

### 📁 **File Creation**
- **Auto-Creation**: If the file doesn't exist, it will be created automatically
- **Directory Creation**: Parent directories are created if needed
- **Clear Feedback**: Shows when new files are created

### 🔧 **Path Resolution**
- **Relative Paths**: `edit config.json`
- **Absolute Paths**: `edit /etc/hosts`
- **Home Directory**: `edit ~/scripts/deploy.sh`
- **Tilde Expansion**: Full support for `~` expansion

### ⚡ **Tab Completion**
- **Command Completion**: Type `edi<TAB>` to complete to `edit`
- **File Path Completion**: Intelligent file and directory completion
- **Context Awareness**: Shows appropriate files and directories

### 🛡️ **Error Handling**
- **Directory Check**: Prevents editing directories
- **Permission Errors**: Clear error messages for permission issues
- **Editor Failures**: Graceful handling when editors fail to start

## Implementation Details

### Code Integration

**CommandProcessor.java**
- Added `editFile()` method with full editor integration
- Integrated with existing path resolution system
- Added to command switch statement

**LuCLICompleter.java**
- Added `edit` to commands array
- Added `edit` to file command list for path completion
- Full tab completion support for file paths

### Editor Detection Algorithm

```java
private String getSystemEditor() {
    // 1. Check EDITOR environment variable
    String editor = System.getenv("EDITOR");
    if (editor != null && !editor.trim().isEmpty()) {
        return editor.trim();
    }
    
    // 2. Try common editors in order
    String[] editors = {"code", "nano", "vim", "vi", "emacs", "gedit", "notepad"};
    for (String editorCmd : editors) {
        if (isCommandAvailable(editorCmd)) {
            return editorCmd;
        }
    }
    
    // 3. Fallback to nano
    return "nano";
}
```

### Cross-Platform Support

**Command Detection**
- **Unix/Linux/macOS**: Uses `which` command
- **Windows**: Uses `where` command  
- **Graceful Fallbacks**: Multiple editor options for different environments

**Process Handling**
- **Terminal Integration**: Uses `ProcessBuilder.inheritIO()` for full terminal control
- **Exit Code Handling**: Reports editor exit codes for debugging
- **Interrupt Handling**: Proper signal handling for editor sessions

## Use Cases

### 🔧 **Configuration Management**
```bash
# Edit server configuration
edit lucee.json

# Edit environment settings  
edit .env

# Edit build configurations
edit pom.xml
```

### 📝 **Development Workflow**
```bash
# Quick script editing
edit deploy.sh

# Edit source files
edit src/main/java/App.java

# Edit documentation
edit README.md
```

### 🚀 **DevOps Tasks**
```bash  
# Edit configuration files
edit config/application.properties

# Edit deployment scripts
edit scripts/deploy.sh

# Edit Docker configurations
edit Dockerfile
```

## Error Scenarios and Messages

| Scenario | Error Message | Resolution |
|----------|---------------|------------|
| No file specified | `❌ edit: missing file operand`<br>`💡 Usage: edit <file>` | Provide a filename |
| Directory specified | `❌ edit: 'dirname' is a directory` | Use a file path instead |
| Permission denied | `❌ edit: cannot create file 'filename': Permission denied` | Check file/directory permissions |
| Editor not found | `❌ edit: failed to open editor 'vim': No such file or directory`<br>`💡 Try setting EDITOR environment variable...` | Install editor or set EDITOR variable |

## Advanced Configuration

### Setting Custom Editor
```bash
# Set environment variable (permanent)
echo 'export EDITOR=code' >> ~/.bashrc

# Set for current session
export EDITOR=vim

# Use specific editor for this command
EDITOR=nano edit myfile.txt
```

### Editor-Specific Features

**VS Code Integration**
- Opens files in existing VS Code window if available
- Supports workspace integration
- Syntax highlighting and IntelliSense

**Nano Features**
- User-friendly for beginners
- Built-in help (Ctrl+G)
- Simple save and exit (Ctrl+X)

**Vim Integration**
- Full vim capabilities
- Plugin support if configured
- Advanced editing features

## Benefits

### 🚀 **Productivity**
- **No Context Switching**: Edit files without leaving LuCLI
- **Quick Access**: Immediate access to any project file
- **Smart Defaults**: Automatically uses best available editor

### 🎯 **User Experience**
- **Intuitive**: Familiar `edit` command syntax
- **Flexible**: Works with any text editor
- **Consistent**: Same behavior across platforms

### 🔧 **Developer Friendly**
- **Tab Completion**: Fast file navigation
- **Path Resolution**: Works with relative and absolute paths
- **Integration**: Seamless integration with existing LuCLI features

## Status: ✅ IMPLEMENTED

The edit command is now fully functional and ready for use in LuCLI. It provides a seamless text editing experience that integrates perfectly with the existing terminal workflow.
