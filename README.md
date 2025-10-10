# 🚀 LuCLI - Lucee Command Line Interface

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]() [![Java 17+](https://img.shields.io/badge/java-17+-blue)]() [![License](https://img.shields.io/badge/license-MIT-green)]()

A modern, feature-rich command line interface for Lucee CFML that provides both interactive terminal mode and one-shot command execution. LuCLI integrates the powerful Lucee CFML engine into a modern terminal interface with advanced features like server management, JMX monitoring, module management, and intelligent output processing.

## ✨ Key Features

### 🎯 Core Capabilities
- **CFML Script Execution**: Run .cfs, .cfm, and .cfc files with full Lucee support
- **Server Management**: Start, stop, monitor, and manage Lucee server instances
- **Module System**: Create and manage reusable CFML modules

## 📦 Installation

### Quick Start
```bash
# Download the latest release
wget https://github.com/your-org/lucli/releases/latest/download/lucli.jar

# Run interactive terminal
java -jar lucli.jar

# Execute a CFML script
java -jar lucli.jar myscript.cfs arg1 arg2
```

### Binary Installation (Recommended)
```bash
# Build self-executing binary
mvn clean package -Pbinary

# Run without Java command
./target/lucli --version
./target/lucli terminal
```

## 🚀 Quick Usage Examples

### Interactive Terminal Mode
```bash
# Start interactive session
java -jar lucli.jar

# Execute CFML expressions
lucli> cfml now()
2025-01-04T13:22:25.123Z

lucli> cfml dateFormat(now(), 'yyyy-mm-dd')  
2025-01-04

# File system operations
lucli> ls -la
lucli> cd myproject
lucli> pwd
```

### Server Management
```bash
# Start Lucee server for current directory
lucli server start

# Start with specific version and custom webroot
lucli server start --version 7.0.0.123 --webroot ./public

# Monitor server with real-time JMX dashboard
lucli server monitor

# View server logs
lucli server log --follow
```

**Framework-Style URL Routing:**
LuCLI servers include built-in support for framework-style URL routing (extension-less URLs). Enable it in your `lucee.json`:

```json
{
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

This routes all requests through your router file (default: `index.cfm`) with `PATH_INFO` set correctly:
- `/hello` → `/index.cfm/hello` (PATH_INFO = `/hello`)
- `/api/users/123` → `/index.cfm/api/users/123` (PATH_INFO = `/api/users/123`)

Compatible with ColdBox, FW/1, CFWheels, ContentBox, and custom frameworks.

📖 **[Complete URL Rewriting Guide →](documentation/URL_REWRITING.md)**

### Module Management
```bash
# List available modules
lucli modules list

# Create new module
lucli modules init my-awesome-module

# Run a module
lucli my-module arg1 arg2
```

### Script Execution
```bash
# Execute CFML scripts
lucli script.cfs
lucli component.cfc
lucli template.cfm

# With arguments
lucli script.cfs --verbose --output=/tmp/results.json
```

## 🎨 Smart Output Processing

LuCLI features an advanced output processing system with intelligent emoji handling and placeholder substitution:

### Emoji Intelligence
- **Automatic Detection**: Detects terminal emoji capabilities
- **Graceful Fallback**: Text symbols on unsupported terminals  
- **Windows Optimized**: Special handling for Windows Terminal, VS Code, ConEmu, etc.

```bash
# On emoji-capable terminals
✅ Server started successfully on port 8080

# On legacy terminals  
[OK] Server started successfully on port 8080
```

### Placeholder System
- **Dynamic Variables**: `${NOW}`, `${USER_NAME}`, `${WORKING_DIR}`
- **Environment Access**: `${ENV_PATH}`, `${ENV_USERNAME}`
- **Smart Emojis**: `${EMOJI_SUCCESS}`, `${EMOJI_ERROR}`, `${EMOJI_WARNING}`

## 🔧 Configuration

### Global Configuration  
LuCLI stores configuration in `~/.lucli/`:
```
~/.lucli/
├── settings.json          # User preferences
├── history                # Command history
├── prompts/               # Custom prompt templates  
├── modules/               # User modules
├── servers/               # Server instances
└── express/               # Lucee Express downloads
```

### Project Configuration (lucee.json)
```json
{
  "name": "my-project",
  "version": "7.0.0.123",
  "port": 8080,
  "webroot": "./",
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  },
  "monitoring": {
    "enabled": true,
    "jmx": { "port": 8999 }
  },
  "jvm": {
    "maxMemory": "1024m",
    "minMemory": "256m"
  }
}
```

**URL Rewrite Configuration:**
- `enabled` (boolean, default: `true`) - Enable/disable framework-style URL routing
- `routerFile` (string, default: `"index.cfm"`) - Central router file for handling all routes

### Prompt Customization
```bash
# View available prompts
prompt

# Switch prompt style
prompt zsh
prompt colorful  
prompt minimal

# Manage emoji settings
prompt emoji on
prompt emoji test
prompt terminal  # Show terminal capabilities
```

## 🏗️ Architecture

### Core Components
- **StringOutput**: Centralized output post-processor with emoji and placeholder support
- **LuceeScriptEngine**: Lucee CFML engine integration with externalized script templates
- **WindowsCompatibility**: Smart terminal detection and emoji capability analysis
- **UnifiedCommandExecutor**: Consistent command handling across CLI and terminal modes

### Externalized Script System
LuCLI uses externalized CFML templates for better maintainability:
```
src/main/resources/script_engine/
├── cfmlOutput.cfs              # Expression evaluation
├── componentWrapper.cfs        # Component execution
├── moduleDirectExecution.cfs   # Module processing  
├── componentToScript.cfs       # Component conversion
└── lucliMappings.cfs          # Component mappings
```

### Template-Based Processing
Scripts use placeholder substitution and post-processing:
```cfml
// Template content
writeOutput('${EMOJI_SUCCESS} Processing completed at ${NOW}');

// Processed output (emoji-capable terminal)  
writeOutput('✅ Processing completed at 2025-01-04T13:22:25');

// Processed output (legacy terminal)
writeOutput('[OK] Processing completed at 2025-01-04T13:22:25');
```

## 📋 Commands Reference

### Global Commands
| Command | Description | Example |
|---------|-------------|---------|
| `--version` | Show version information | `lucli --version` |
| `--lucee-version` | Show Lucee engine version | `lucli --lucee-version` |
| `help` | Show help information | `lucli help` |
| `terminal` | Start interactive mode | `lucli terminal` |

### Server Commands
| Command | Description | Example |
|---------|-------------|---------|
| `server start` | Start Lucee server | `lucli server start --version 7.0.0.123` |
| `server stop` | Stop server | `lucli server stop --name myapp` |
| `server status` | Check server status | `lucli server status` |
| `server list` | List all servers | `lucli server list` |
| `server monitor` | JMX monitoring dashboard | `lucli server monitor` |
| `server log` | View server logs | `lucli server log --follow` |

### Module Commands  
| Command | Description | Example |
|---------|-------------|---------|
| `modules list` | List available modules | `lucli modules list` |
| `modules init` | Create new module | `lucli modules init my-module` |
| `modules run` | Execute module | `lucli modules run my-module arg1` |
| `<module-name>` | Direct module execution | `lucli my-module arg1 arg2` |

### Terminal Commands (Interactive Mode)
| Command | Description | Example |
|---------|-------------|---------|
| `cfml <expr>` | Execute CFML expression | `cfml now()` |
| `prompt` | Manage prompt styles | `prompt colorful` |
| `ls`, `cd`, `pwd` | File system operations | `cd /path/to/project` |
| `exit`, `quit` | Exit terminal | `exit` |

## 🛠️ Development

### Prerequisites
- Java 17+ (JDK required for building)
- Maven 3.x  
- Git

### Building from Source
```bash
# Clone repository
git clone https://github.com/your-org/lucli.git
cd lucli

# Build JAR
mvn clean package

# Build self-executing binary
mvn clean package -Pbinary

# Run tests
./test.sh

# Quick development cycle
./dev-lucli.sh
```

### Project Structure
```
lucli/
├── src/main/java/org/lucee/lucli/     # Core Java classes
├── src/main/resources/
│   ├── script_engine/                 # Externalized CFML templates
│   ├── prompts/                       # Built-in prompt themes
│   └── tomcat_template/               # Server configuration templates
├── test/                              # Test suites and examples
├── demo_servers/                      # Development test servers
└── docs/                              # Additional documentation
```

### Key Dependencies
- **Lucee 7.0.0.242-RC**: CFML engine
- **JLine 3.26.3**: Terminal interface and command-line handling
- **Jackson 2.15.2**: JSON configuration parsing

## 📚 Documentation

### Comprehensive Guides
- [**WARP.md**](WARP.md) - Developer guide and architecture overview
- [**URL Rewriting Guide**](documentation/URL_REWRITING.md) - Complete URL rewriting and framework routing documentation
- [**STRING_OUTPUT_SYSTEM.md**](STRING_OUTPUT_SYSTEM.md) - Output processing system documentation
- [**EXTERNALIZED_SCRIPTS.md**](EXTERNALIZED_SCRIPTS.md) - CFML script template system
- [**EMOJI_IMPROVEMENTS.md**](EMOJI_IMPROVEMENTS.md) - Emoji handling and terminal compatibility
- [**PROMPTS.md**](PROMPTS.md) - Prompt system and customization guide

### Feature Documentation
- [**UNIFIED_COMMAND_ARCHITECTURE.md**](UNIFIED_COMMAND_ARCHITECTURE.md) - Command consistency architecture
- [**TAB_COMPLETION_GUIDE.md**](TAB_COMPLETION_GUIDE.md) - Auto-completion system
- [**PIPELINE_SUMMARY.md**](PIPELINE_SUMMARY.md) - Development pipeline and processes

## 🧪 Testing

### Test Suites
```bash
# Comprehensive test suite
./test.sh

# Simple functionality tests  
./test-simple.sh

# Build and run binary test
./dev-lucli.sh
```

### Test Categories
- ✅ Basic functionality (help, version commands)
- ✅ CFML script execution (.cfs, .cfm, .cfc)
- ✅ Server management (start, stop, status, monitor)
- ✅ File system operations and navigation
- ✅ Module system (create, list, execute)
- ✅ Command consistency between CLI and terminal modes
- ✅ Binary executable functionality
- ✅ Configuration handling and persistence
- ✅ Error scenarios and edge cases

## 🌟 Advanced Features

### JMX Monitoring
Real-time server monitoring with ASCII dashboard:
```bash
lucli server monitor
```
- Memory usage (heap/non-heap) with progress bars
- Thread counts and garbage collection statistics  
- System load and CPU metrics
- Lucee-specific performance data

### Module System
Create reusable CFML components:
```bash
# Create module
lucli modules init data-processor

# Edit module (creates Module.cfc)
# Execute module
lucli data-processor --input=data.json --format=xml
```

### Custom Prompt Themes
JSON-based prompt system with 14+ built-in themes:
```json
{
  "name": "my-custom",
  "description": "My custom prompt",
  "template": "🔥 [{time}] {path} {git}⚡ ",
  "showPath": true,
  "showTime": true,
  "showGit": true,
  "useEmoji": true
}
```

## 🔮 Roadmap

### Near Term
- [ ] Color placeholder support (`${COLOR_RED}`, `${COLOR_RESET}`)
- [ ] Template caching for improved performance  
- [ ] Plugin system for third-party extensions
- [ ] Git integration in prompts (show branch, status)
- [ ] Enhanced module package management

### Future Vision
- [ ] Package repository for community modules
- [ ] Web-based monitoring dashboard
- [ ] Docker integration for containerized Lucee instances
- [ ] Cloud deployment integrations (AWS, Azure, GCP)
- [ ] IDE integrations (VS Code extension)

## 🤝 Contributing

We welcome contributions! Please see:
- [**CONTRIBUTING.md**](CONTRIBUTING.md) - Contribution guidelines
- [**RELEASE_PROCESS.md**](RELEASE_PROCESS.md) - Release and versioning process  
- [**WARP.md**](WARP.md) - Development setup and architecture

### Development Commands
```bash
# Format consistent with project
lucli <action> <subcommand> [options] [parameters]

# Examples
lucli server start --version 7.0.0.123
lucli modules init my-module  
lucli server log --type server --follow
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Lucee Association**: For the powerful CFML engine
- **JLine Project**: For excellent terminal interface capabilities
- **Community Contributors**: For feedback, testing, and improvements

## 🚀 Get Started

```bash
# Download and try it now
wget https://github.com/your-org/lucli/releases/latest/download/lucli.jar
java -jar lucli.jar --version

# Start your first CFML session  
java -jar lucli.jar
lucli> cfml "Hello, World!"
```

---

**LuCLI** - Making CFML development faster, easier, and more enjoyable from the command line.

*For detailed documentation and examples, explore the docs/ directory or visit our [GitHub repository](https://github.com/your-org/lucli).*
