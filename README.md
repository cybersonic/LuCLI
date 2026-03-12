# 🚀 LuCLI - Lucee Command Line Interface

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]() [![Java 17+](https://img.shields.io/badge/java-17+-blue)]() [![License](https://img.shields.io/badge/license-MIT-green)]() [![Alpha](https://img.shields.io/badge/status-alpha-orange)]

⚠️ **This is an alpha release** - Expect breaking changes and limitations. The API and features may change significantly before v1.0. We appreciate feedback and contributions!

A modern, feature-rich command line interface for Lucee CFML that brings the power of CFML to your terminal. LuCLI integrates the Lucee CFML engine with advanced features like server management, JMX monitoring, module management, and intelligent output processing.

## ✨ Key Features

### 🎯 Core Capabilities
- **CFML Script Execution**: Run .cfs, .cfm, and .cfc files with full Lucee support
- **Server Management**: Start, stop, monitor, and manage Lucee server instances  
- **Module System**: Create and manage reusable CFML modules
## 📦 Installation

### Quick Start
```bash
# Install latest (macOS/Linux)
curl -LsSf https://lucli.dev/install.sh | sh

# Install latest (Windows PowerShell)
powershell -ExecutionPolicy Bypass -NoProfile -Command "irm https://lucli.dev/install.ps1 | iex"
```

Pin a specific version:
```bash
LUCLI_VERSION=0.2.1 curl -LsSf https://lucli.dev/install.sh | sh
```
```bash
# Download the latest JAR release
wget https://github.com/cybersonic/LuCLI/releases/latest/download/lucli.jar

# Start using LuCLI
java -jar lucli.jar

# Execute CFML scripts directly
java -jar lucli.jar myscript.cfs arg1 arg2
```

Or with Docker

```
# Start using LuCLI interactively 
docker run --interactive markdrew/lucli:latest repl 
```

### Download Options
Visit the [Releases Page](https://github.com/cybersonic/LuCLI/releases) to download:
- **lucli.jar** - Universal JAR file (requires Java 17+)
- **lucli** - Self-executing binary for Linux/macOS
- **lucli.bat** - Windows batch file
- **install.sh** - Automated installation script

Also available as a [Docker image](https://hub.docker.com/r/markdrew/lucli)

### Binary Installation (Recommended)
```bash
# Build self-executing binary
mvn clean package -Pbinary

# Use directly without Java command
./target/lucli --version
./target/lucli
```

### Running from Docker
```bash
docker run markdrew/lucli:latest --version
```
## 🚀 Usage Examples

### CFML Development
```bash
# Start interactive CFML session
java -jar lucli.jar

# Execute CFML expressions directly
lucli> cfml now()
2025-01-04T13:22:25.123Z

lucli> cfml dateFormat(now(), 'yyyy-mm-dd')  
2025-01-04

# Navigate and work with files
lucli> ls -la
lucli> cd myproject
lucli> pwd
```

### Server Management
```bash
# Start Lucee server for current directory
lucli server start

# Start with specific version and port
lucli server start --version 6.2.2.91 --port 8080

# Start with custom name and force replacement
lucli server start --name myapp --port 8888 --force

# Monitor server with real-time JMX dashboard
lucli server monitor

# View server logs
lucli server log --follow
```

### Help System
```bash
# Get general help
lucli --help

# Get help for specific commands
lucli server --help
lucli server start --help
lucli modules --help

# Get help for CFML commands
lucli cfml --help
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

## 🆘 Help System

LuCLI features a comprehensive, hierarchical help system built on Picocli that provides context-sensitive assistance at every level:

### Universal Help Access
```bash
# Any command supports --help
lucli --help                    # Root application help
lucli server --help             # Server command overview  
lucli server start --help       # Specific subcommand help
lucli modules run --help        # Module execution help
lucli cfml --help               # CFML expression help
```

### Key Help Features
- **Hierarchical Structure**: From general overview to specific command details
- **Consistent Interface**: `--help` works on every command and subcommand
- **Rich Examples**: Practical usage examples in every help screen
- **Error Guidance**: Smart error messages with helpful suggestions
- **Interactive Discovery**: Built-in `help` command and tab completion for easy exploration

📖 **[Complete Help System Guide →](documentation/HELP_SYSTEM.md)**

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

### Smart Output Processing
LuCLI includes an intelligent placeholder system that enhances internal messages and error handling:

**Available Placeholders:**
- **Time Variables**: `${NOW}`, `${DATE}`, `${TIME}`, `${TIMESTAMP}`
- **System Info**: `${USER_NAME}`, `${WORKING_DIR}`, `${USER_HOME}`, `${OS_NAME}`, `${JAVA_VERSION}`
- **LuCLI Info**: `${LUCLI_VERSION}`, `${LUCLI_HOME}`
- **Environment**: `${ENV_PATH}`, `${ENV_USERNAME}`, etc.
- **Smart Emojis**: `${EMOJI_SUCCESS}`, `${EMOJI_ERROR}`, `${EMOJI_WARNING}`, `${EMOJI_INFO}`

**Note**: These placeholders work in LuCLI's internal templates and error messages, not in user CFML script output.

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
  },
  "agents": {
    "luceedebug": {
      "enabled": false,
      "jvmArgs": [
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:9999",
        "-javaagent:${LUCLI_HOME}/dependencies/luceedebug.jar=jdwpHost=localhost,jdwpPort=9999,debugHost=0.0.0.0,debugPort=10000,jarPath=${LUCLI_HOME}/dependencies/luceedebug.jar"
      ],
      "description": "Lucee step debugger agent"
    }
  }
}
```

**URL Rewrite Configuration:**
- `enabled` (boolean, default: `true`) - Enable/disable framework-style URL routing
- `routerFile` (string, default: `"index.cfm"`) - Central router file for handling all routes

**Agent Configuration:**
- `agents` (object, optional) - Named Java agents with `enabled` and `jvmArgs` fields.
- See `documentation/SERVER_AGENTS.md` for detailed examples and startup flags.

**CFConfig Integration:**
- `configurationFile` (string, optional) - Path (relative to the project directory or absolute) to a base CFConfig JSON file. This is loaded first as the foundation for your server's Lucee configuration.
- `configuration` (object, optional) - Inline `.CFConfig.json` content that overrides/extends the base from `configurationFile`. The merged result is written to `lucee-server/context/.CFConfig.json` on `lucli server start`. Allows per-project customization of shared base configurations.

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
- **WindowsSupport**: Smart terminal detection and emoji capability analysis
- **ServerCommandHandler**: Consistent server command handling across CLI and terminal modes

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
LuCLI's internal script templates use placeholder substitution for consistent error handling and messaging:
```cfml
// Internal template content (cfmlOutput.cfs)
writeOutput('${EMOJI_ERROR} CFML Error: ' & e.message);

// Processed for emoji-capable terminal
writeOutput('❌ CFML Error: ' & e.message);

// Processed for legacy terminal
writeOutput('[ERROR] CFML Error: ' & e.message);
```

## 📋 Commands Reference

### Global Commands
| Command | Description | Example |
|---------|-------------|---------|
| `--version` | Show version information | `lucli --version` |
| `--lucee-version` | Show Lucee engine version | `lucli --lucee-version` |
| `--help` | Show help information | `lucli --help` |
| `help` | Show help for specific commands | `lucli help server` |

### CFML Commands
| Command | Description | Example |
|---------|-------------|---------|
| `cfml` | Execute CFML expressions | `lucli cfml 'now()'` |
| `script.cfs` | Execute CFML script file | `lucli script.cfs arg1 arg2` |

### Server Commands
| Command | Description | Example |
|---------|-------------|---------|
| `server start` | Start Lucee server | `lucli server start --version 6.2.2.91 --port 8080` |
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

### Interactive Commands
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
./tests/test.sh

# Quick development cycle
./dev-lucli.sh
```

### Project Structure
```
lucli/
├── src/main/java/org/lucee/lucli/     # Core Java classes
├── src/main/resources/
│   ├── script_engine/                 # Externalized CFML templates
│   └── prompts/                       # Built-in prompt themes
├── tests/                             # Test suites and examples
├── demo_servers/                      # Development test servers
└── documentation/                     # Additional documentation
```

### Key Dependencies
- **Lucee 7.x**: CFML engine
- **JLine 3.x**: Terminal interface and command-line handling
- **Jackson 2.15.2**: JSON configuration parsing
- **Picocli 4.7.5**: Command-line parsing and help system

## 📚 Documentation

### Core Documentation
- [**Help System Guide**](documentation/HELP_SYSTEM.md) - Complete help system documentation
- [**URL Rewriting Guide**](documentation/URL_REWRITING.md) - Framework routing and URL rewriting

### Additional Guides
- [**SHORTCUTS.md**](SHORTCUTS.md) - Quick reference for shortcuts and commands


## 🧪 Testing

### Test Suites
```bash
# Comprehensive test suite (52 tests)
./tests/test.sh

# Server and CFML integration tests
./tests/test-server-cfml.sh

# URL rewrite integration tests
./tests/test-urlrewrite-integration.sh

# Build and run binary test
./dev-lucli.sh
```

### Test Categories
- ✅ Basic functionality (help, version commands)
- ✅ Comprehensive help system (all commands and subcommands)  
- ✅ CFML script execution (.cfs, .cfm, .cfc)
- ✅ Server management (start, stop, status, monitor) with --port option
- ✅ File system operations and navigation
- ✅ Module system (create, list, execute)
- ✅ Command consistency across all execution modes
- ✅ Binary executable functionality
- ✅ Configuration handling and persistence
- ✅ URL rewrite and framework routing
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

### Daemon Mode (JSON over TCP)
Run LuCLI as a long-lived daemon that accepts JSON commands over a local TCP socket.

```bash
# Start daemon on default port 10000 (blocks current terminal)
lucli daemon

# Start daemon on a custom port
lucli daemon --port 11000
```

Each client connection sends a single JSON line with the argv to execute:

```json
{"id":"1","argv":["server","list"]}
```

The daemon executes the command through the normal Picocli pipeline and replies with JSON containing the exit code and combined stdout/stderr:

```json
{"id":"1","exitCode":0,"output":"..."}
```

You can talk to the daemon from any language that can open a TCP socket to `127.0.0.1:<port>` and read/write UTF-8 lines.

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
- **PicoCLI Project**: For excellent command-line argument parsing capabilities
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

*For detailed documentation and examples, explore the documentation/ directory or visit our [GitHub repository](https://github.com/your-org/lucli).*
