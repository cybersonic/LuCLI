# Lucee Debug Explorer (k9s-style)

## Overview

The LuCLI Debug Explorer is a new experimental feature inspired by [k9s](https://k9scli.io/), providing an interactive terminal-based interface for debugging and monitoring Lucee CFML servers. This feature leverages JMX (Java Management Extensions) to provide deep insights into server performance, request handling, and system state.

## Features

### Current Features
- **JMX Connection Management** - Connect to local or remote Lucee servers via JMX
- **MBean Discovery** - Automatically discover all Lucee-specific MBeans
- **Request Monitoring** - Browse request and session-related MBeans
- **Debug Operations** - Explore available debug operations on MBeans
- **Summary Reports** - Generate detailed debug reports
- **Command Line Interface** - Simple, reliable CLI for debug information

### Planned Features
- **Interactive TUI Mode** - Terminal UI navigation (temporarily disabled due to compatibility issues)
- **Real-time Performance Metrics** - Live monitoring of throughput, response times, memory usage
- **SQL Query Profiling** - Drill-down into database query performance
- **Request Timeline View** - Track individual requests through the system
- **Template Execution Timing** - Monitor CFML template performance
- **Log Stream Integration** - Live log monitoring within the TUI
- **Alert System** - Configurable alerts for performance thresholds

## Usage

### Basic Usage
```bash
# Connect to local Lucee server (default localhost:8999)
lucli server debug

# Connect to remote server
lucli server debug --host myserver.com --port 9999

# Generate detailed report
lucli server debug --report

# Start interactive TUI mode
lucli server debug --interactive
```

### Interactive TUI Navigation
- **↑/k** - Move selection up
- **↓/j** - Move selection down  
- **Enter** - Select current item
- **r** - Refresh current view
- **h** - Show help
- **q/Esc** - Quit application

## Architecture

### Components

1. **DebugExplorer.java** - Core JMX client for discovering and querying MBeans
2. **DebugTUI.java** - Interactive terminal UI using JLine for k9s-style navigation
3. **debug.cfm** - CFML command wrapper providing CLI interface

### JMX Integration
The debug explorer connects to Lucee's JMX server to access:
- Server metrics and performance data
- Request/session information
- Debug operations and configuration
- System and JVM statistics

## Requirements

- **Lucee Server** with JMX enabled
- **Java 17+** (for JLine terminal features)
- **Network access** to JMX port (default 8999)

## JMX Configuration

### Enabling JMX on Lucee
Add the following JVM arguments to enable JMX:

```bash
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=8999
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

For production environments, enable authentication:
```bash
-Dcom.sun.management.jmxremote.authenticate=true
-Dcom.sun.management.jmxremote.access.file=/path/to/jmxremote.access
-Dcom.sun.management.jmxremote.password.file=/path/to/jmxremote.password
```

## Development

### Adding New Views
1. Add new menu item in `DebugTUI.initializeMenus()`
2. Implement handler in `handleMainMenuSelection()`
3. Create view method (e.g., `showNewFeature()`)

### Extending MBean Discovery
1. Add new patterns in `DebugExplorer.findRequestMBeans()`
2. Implement custom MBean queries in `DebugExplorer`
3. Update `generateDebugReport()` to include new data

### CFML Integration
The CFML wrapper in `debug.cfm` provides:
- Command-line argument parsing
- Error handling and user feedback
- Integration with existing LuCLI command system

## Examples

### Basic Exploration
```bash
$ lucli server debug
Connecting to Lucee server at localhost:8999...
Connected successfully!

=== Debug Explorer Summary ===
Lucee MBeans found: 12
  - lucee.runtime:type=Config
  - lucee.runtime:type=Memory
  - lucee.runtime:type=Requests
  ...

Use --report for detailed information
Use --interactive for TUI mode
```

### Interactive Mode
The TUI provides a menu-driven interface:
```
🔍 Lucee Debug Explorer (k9s-style)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Main Menu - Select Debug Category

► 📊 Lucee MBeans
  🔍 Request MBeans  
  ⚙️  Debug Operations
  📈 Performance Metrics
  🔧 System Information
  ❌ Exit

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Navigation: ↑/k ↓/j | Select: Enter | Refresh: r | Help: h | Quit: q/Esc
```

## Troubleshooting

### Connection Issues
1. **JMX not enabled** - Add JMX JVM arguments to Lucee startup
2. **Port conflicts** - Check if port 8999 is available and not blocked
3. **Network access** - Ensure firewall allows connections to JMX port
4. **Authentication** - Verify JMX credentials if authentication is enabled

### Performance Considerations
- JMX queries can impact server performance under heavy load
- Use read-only operations when possible
- Consider connecting to development servers for detailed exploration

## Future Enhancements

### Phase 1: Core Monitoring
- [ ] Real-time request metrics dashboard
- [ ] SQL query execution time tracking
- [ ] Memory usage visualization
- [ ] Thread pool monitoring

### Phase 2: Advanced Features  
- [ ] Request tracing and profiling
- [ ] Custom metric collection
- [ ] Historical data storage
- [ ] Export functionality

### Phase 3: Integration
- [ ] Integration with Lucee logging
- [ ] Custom alert configuration
- [ ] REST API for external monitoring
- [ ] Dashboard web interface

## Contributing

The debug explorer is designed for extensibility. Key areas for contribution:

1. **New MBean integrations** - Discover and integrate additional Lucee MBeans
2. **UI enhancements** - Improve TUI navigation and visualization
3. **Performance features** - Add real-time monitoring capabilities
4. **Documentation** - Expand usage examples and troubleshooting guides

## License

This feature is part of LuCLI and follows the same licensing terms as the main project.
