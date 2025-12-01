# 📚 LuCLI Documentation

Welcome to the **LuCLI** (Lucee Command Line Interface) documentation. This collection provides comprehensive guides, technical references, and development resources for working with LuCLI.

## 🎯 What is LuCLI?

LuCLI is a modern, feature-rich command line interface that brings the power of Lucee CFML to your terminal. It integrates the Lucee CFML engine with advanced features including server management, JMX monitoring, module management, and intelligent output processing.

**Key Features:**
- Execute CFML scripts (.cfs, .cfm, .cfc) directly from the command line
- Start and manage Lucee server instances with built-in monitoring
- Create and run reusable CFML modules
- Interactive REPL for CFML development and testing
- Framework-style URL routing for modern web applications
- Comprehensive help system with context-sensitive guidance

## 📖 Documentation Index

### Getting Started

**[Main README](../README.md)** - Start here for installation, quick start guide, and basic usage examples.

### User Guides

**[Help System Guide](HELP_SYSTEM.md)**  
Complete guide to LuCLI's hierarchical help system. Learn how to navigate commands, access context-sensitive help, and discover features through the built-in help interface.

**[Shortcuts Guide](SHORTCUTS.md)**  
Quick reference for command shortcuts, aliases, and productivity tips. Includes direct CFML file execution and module shortcuts.

**[Executing Commands](EXECUTING_COMMANDS.md)**  
Detailed guide on command execution patterns, argument handling, and command-line parsing in LuCLI.

**[URL Rewriting Guide](URL_REWRITING.md)**  
Framework-style URL routing and rewriting configuration. Essential for building modern web applications with clean URLs using ColdBox, FW/1, CFWheels, or custom frameworks.

### Server Management

**[Configuration (lucee.json)](CONFIGURATION.md)**  
Full reference for `lucee.json` server configuration, including all available settings, defaults, and examples.

**[Server Agents](SERVER_AGENTS.md)**  
Configure and use Java agents with LuCLI servers, including the Lucee step debugger and custom JVM agents. Learn about agent configuration, JVM arguments, and debugging setup.

**[Server Command Development](SERVER_COMMAND_DEVELOPMENT.md)**  
Developer guide for extending and customizing server commands. Covers the server command architecture and how to add new server management features.

## 🚀 Quick Navigation

### By Task

**I want to...**

- **Get started with LuCLI** → [Main README](../README.md)
- **Learn command-line syntax** → [Executing Commands](EXECUTING_COMMANDS.md)
- **Use the help system** → [Help System Guide](HELP_SYSTEM.md)
- **Execute CFML files** → [Shortcuts Guide](SHORTCUTS.md)
- **Manage Lucee servers** → [Server Command Development](SERVER_COMMAND_DEVELOPMENT.md)
- **Configure URL routing** → [URL Rewriting Guide](URL_REWRITING.md)
- **Debug Lucee applications** → [Server Agents](SERVER_AGENTS.md)

### By User Type

**CFML Developer**
1. Start with the [Main README](../README.md) for installation
2. Learn [Shortcuts](SHORTCUTS.md) for quick CFML execution
3. Explore [URL Rewriting](URL_REWRITING.md) for web applications
4. Use [Help System](HELP_SYSTEM.md) to discover features

**DevOps Engineer**
1. Review [Server Command Development](SERVER_COMMAND_DEVELOPMENT.md)
2. Configure [Server Agents](SERVER_AGENTS.md) for monitoring
3. Use server management commands for deployments

**Framework Developer**
1. Understand [Executing Commands](EXECUTING_COMMANDS.md)
2. Implement [URL Rewriting](URL_REWRITING.md) patterns
3. Study the help system for command structure

**LuCLI Contributor**
1. Review [Server Command Development](SERVER_COMMAND_DEVELOPMENT.md)
2. Study the codebase architecture in [WARP.md](../WARP.md)
3. Check [TODO.md](../TODO.md) for contribution opportunities

## 🛠️ Additional Resources

### Main Project Files

- **[WARP.md](../WARP.md)** - Developer guide and architecture deep-dive
- **[CHANGELOG.md](../CHANGELOG.md)** - Version history and release notes

### Example Code

The `examples/` directory contains sample CFML scripts demonstrating LuCLI capabilities:
- Hello World examples (.cfc, .cfm, .cfs)
- Timing tests and performance examples
- Module examples

### Test Suite

The `tests/` directory includes comprehensive test scripts:
- Server management tests
- CFML execution tests
- URL rewriting integration tests
- Tab completion tests

## 🤝 Contributing to Documentation

Found a typo or want to improve the documentation? Contributions are welcome!

1. Documentation files use Markdown format
2. Keep examples clear and tested
3. Include practical use cases
4. Link between related documents
5. Update this index when adding new documentation

## 📞 Getting Help

- **Command-line help**: Run `lucli --help` or `lucli <command> --help`
- **GitHub Issues**: Report bugs or request features at [github.com/cybersonic/LuCLI](https://github.com/cybersonic/LuCLI)
- **Community**: Join discussions in GitHub Discussions

---

**LuCLI Documentation** - Last updated: November 2025  
*Making CFML development faster, easier, and more enjoyable from the command line.*
