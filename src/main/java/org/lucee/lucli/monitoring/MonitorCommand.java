package org.lucee.lucli.monitoring;

import org.lucee.lucli.monitoring.CliDashboard.ServerMetrics;
import org.lucee.lucli.monitoring.JmxConnection.*;
import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Interactive monitoring command for Lucee servers via JMX
 */
public class MonitorCommand {
    
    private final CliDashboard dashboard;
    private final ScheduledExecutorService scheduler;
    private JmxConnection jmxConnection;
    private volatile boolean running;
    
    public MonitorCommand() {
        this.dashboard = new CliDashboard();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = false;
    }
    
    /**
     * Start monitoring a Lucee server
     */
    public void startMonitoring(String host, int port, int refreshInterval) {
        try {
            // Show connecting message
            dashboard.renderConnecting(host, port);
            
            // Establish JMX connection
            jmxConnection = new JmxConnection(host, port);
            jmxConnection.connect();
            
            String serverName = host + ":" + port;
            running = true;
            
            // Start the refresh loop
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    refreshDashboard(serverName);
                } catch (Exception e) {
                    dashboard.renderError("Failed to refresh metrics: " + e.getMessage());
                    running = false;
                }
            }, 0, refreshInterval, TimeUnit.SECONDS);
            
            // Handle user input
            handleUserInput();
            
        } catch (Exception e) {
            dashboard.renderError("Connection failed: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    /**
     * Refresh dashboard with current metrics
     */
    private void refreshDashboard(String serverName) throws Exception {
        if (!running) return;
        
        // Gather all metrics
        MemoryMetrics memory = jmxConnection.getMemoryMetrics();
        ThreadingMetrics threading = jmxConnection.getThreadingMetrics();
        List<GcMetrics> gcMetrics = jmxConnection.getGcMetrics();
        RuntimeMetrics runtime = jmxConnection.getRuntimeMetrics();
        OsMetrics os = jmxConnection.getOsMetrics();
        LuceeMetrics lucee = jmxConnection.getLuceeMetrics();
        
        // Create metrics container
        ServerMetrics metrics = new ServerMetrics(memory, threading, gcMetrics, 
                                                runtime, os, lucee);
        
        // Render dashboard
        dashboard.renderDashboard(serverName, metrics);
    }
    
    /**
     * Handle user input for interactive controls
     */
    private void handleUserInput() {
        try {
            // Set up non-blocking input reading
            new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while (running) {
                    try {
                        if (scanner.hasNextLine()) {
                            String input = scanner.nextLine().toLowerCase().trim();
                            handleCommand(input);
                        }
                        Thread.sleep(100); // Small delay to prevent busy waiting
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                scanner.close();
            }).start();
            
            // Keep main thread alive while monitoring
            while (running) {
                Thread.sleep(1000);
            }
            
        } catch (InterruptedException e) {
            // Monitoring stopped
        }
    }
    
    /**
     * Handle user commands
     */
    private void handleCommand(String command) {
        switch (command) {
            case "q":
            case "quit":
            case "exit":
                running = false;
                break;
            case "r":
            case "refresh":
                // Force immediate refresh (will happen on next scheduled cycle)
                break;
            case "h":
            case "help":
                showHelp();
                break;
            case "c":
            case "clear":
                dashboard.clearScreen();
                break;
            default:
                // Ignore unknown commands
                break;
        }
    }
    
    /**
     * Show help information
     */
    private void showHelp() {
        dashboard.clearScreen();
        System.out.println("\033[1mHELP\033[0m");
        System.out.println("─".repeat(40));
        System.out.println("Available Commands:");
        System.out.println();
        System.out.println("q, quit, exit - Quit monitoring");
        System.out.println("r, refresh    - Force refresh");
        System.out.println("h, help       - Show this help");
        System.out.println("c, clear      - Clear screen");
        System.out.println();
        System.out.println("\033[33mNote:\033[0m Type the command and press Enter.");
        System.out.println("The dashboard auto-refreshes every few seconds.");
        System.out.println("Use 'q' + Enter to quit safely.");
        System.out.println();
        System.out.println("Press Enter to return to dashboard...");
        
        try {
            System.in.read();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    /**
     * Clean up resources
     */
    private void cleanup() {
        running = false;
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        
        if (jmxConnection != null) {
            try {
                jmxConnection.close();
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * Command line entry point for monitoring
     * Returns null on success (monitoring started) or error message on failure
     */
    public static String executeMonitor(String[] args) {
        String host = null;
        Integer port = null;
        String serverName = null;
        int refreshInterval = 3;
        boolean useCurrentDirectory = false;
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                case "-h":
                    if (i + 1 < args.length) {
                        host = args[++i];
                    }
                    break;
                case "--port":
                case "-p":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            return "❌ Invalid port number: " + args[i];
                        }
                    }
                    break;
                case "--name":
                case "-n":
                    if (i + 1 < args.length) {
                        serverName = args[++i];
                    }
                    break;
                case "--refresh":
                case "-r":
                    if (i + 1 < args.length) {
                        try {
                            refreshInterval = Integer.parseInt(args[++i]);
                            if (refreshInterval < 1) {
                                return "❌ Refresh interval must be at least 1 second";
                            }
                        } catch (NumberFormatException e) {
                            return "❌ Invalid refresh interval: " + args[i];
                        }
                    }
                    break;
                case "--help":
                    showUsage();
                    return null; // Help shown, normal exit
                default:
                    if (args[i].startsWith("-")) {
                        return "❌ Unknown option: " + args[i] + "\n\n" + getUsageString();
                    }
                    break;
            }
        }
        
        // If no specific server or host/port specified, try current directory
        if (serverName == null && host == null && port == null) {
            useCurrentDirectory = true;
        }
        
        // Resolve connection details
        ConnectionDetails connectionDetails = resolveConnectionDetails(serverName, host, port, useCurrentDirectory);
        if (connectionDetails == null) {
            // Error message already set in resolveConnectionDetails
            return lastErrorMessage;
        }
        
        // Start monitoring
        MonitorCommand monitor = new MonitorCommand();
        
        // Add shutdown hook for clean exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            monitor.cleanup();
            System.out.println("\nMonitoring stopped.");
        }));
        
        monitor.startMonitoring(connectionDetails.host, connectionDetails.port, refreshInterval);
        
        // If we reach here, monitoring completed normally
        return null;
    }
    
    // Thread-local storage for error messages to be returned to terminal
    private static String lastErrorMessage = null;
    
    /**
     * Connection details for JMX monitoring
     */
    private static class ConnectionDetails {
        public final String host;
        public final int port;
        public final String serverDisplayName;
        
        public ConnectionDetails(String host, int port, String serverDisplayName) {
            this.host = host;
            this.port = port;
            this.serverDisplayName = serverDisplayName;
        }
    }
    
    /**
     * Resolve JMX connection details based on arguments
     */
    private static ConnectionDetails resolveConnectionDetails(String serverName, String host, Integer port, boolean useCurrentDirectory) {
        try {
            LuceeServerManager serverManager = new LuceeServerManager();
            
            // Case 1: Named server specified
            if (serverName != null) {
                LuceeServerManager.ServerInfo serverInfo = serverManager.getServerInfoByName(serverName);
                if (serverInfo == null) {
                    lastErrorMessage = "❌ Server '" + serverName + "' not found.\n💡 Use 'lucli server list' to see available servers.";
                    return null;
                }
                
                if (!serverInfo.isRunning()) {
                    lastErrorMessage = "❌ Server '" + serverName + "' is not running.\n💡 Start the server with: lucli server start --name " + serverName;
                    return null;
                }
                
                // Get JMX port from server config
                JmxDetails jmxDetails = getJmxDetailsForServer(serverInfo, serverManager);
                if (jmxDetails == null) {
                    return null; // Error already set in getJmxDetailsForServer
                }
                
                System.out.println("🔍 Monitoring server: " + serverName);
                return new ConnectionDetails(jmxDetails.host, jmxDetails.port, serverName);
            }
            
            // Case 2: Current directory server
            if (useCurrentDirectory) {
                Path currentDir = Paths.get(System.getProperty("user.dir"));
                LuceeServerManager.ServerInstance runningServer = serverManager.getRunningServer(currentDir);
                
                if (runningServer == null) {
                    lastErrorMessage = "❌ No running server found in current directory.\n💡 Start a server with: lucli server start";
                    return null;
                }
                
                // Get JMX port from server config
                LuceeServerManager.ServerInfo serverInfo = serverManager.getServerInfoByName(runningServer.getServerName());
                if (serverInfo == null) {
                    lastErrorMessage = "❌ Could not get server information for: " + runningServer.getServerName();
                    return null;
                }
                
                JmxDetails jmxDetails = getJmxDetailsForServer(serverInfo, serverManager);
                if (jmxDetails == null) {
                    return null; // Error already set in getJmxDetailsForServer
                }
                
                System.out.println("🔍 Monitoring current directory server: " + runningServer.getServerName());
                return new ConnectionDetails(jmxDetails.host, jmxDetails.port, runningServer.getServerName());
            }
            
            // Case 3: Explicit host/port
            String resolvedHost = host != null ? host : "localhost";
            int resolvedPort = port != null ? port : 8999;
            
            System.out.println("🔍 Monitoring JMX endpoint: " + resolvedHost + ":" + resolvedPort);
            return new ConnectionDetails(resolvedHost, resolvedPort, resolvedHost + ":" + resolvedPort);
            
        } catch (Exception e) {
            lastErrorMessage = "❌ Failed to resolve server details: " + e.getMessage();
            return null;
        }
    }
    
    /**
     * JMX connection details
     */
    private static class JmxDetails {
        public final String host;
        public final int port;
        
        public JmxDetails(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
    
    /**
     * Get JMX connection details for a server
     */
    private static JmxDetails getJmxDetailsForServer(LuceeServerManager.ServerInfo serverInfo, LuceeServerManager serverManager) {
        try {
            // Try to load the server's configuration to get JMX port
            if (serverInfo.getProjectDir() != null) {
                LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(serverInfo.getProjectDir());
                
                if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
                    return new JmxDetails("localhost", config.monitoring.jmx.port);
                } else {
                    lastErrorMessage = "❌ JMX monitoring is not enabled for server '" + serverInfo.getServerName() + "'.\n💡 Enable JMX in lucee.json: \"monitoring\": { \"enabled\": true, \"jmx\": { \"port\": 8999 } }";
                    return null;
                }
            } else {
                // Fallback: use default JMX port (this shouldn't normally happen)
                System.out.println("⚠️  Using default JMX port 8999 for server '" + serverInfo.getServerName() + "'");
                return new JmxDetails("localhost", 8999);
            }
        } catch (Exception e) {
            lastErrorMessage = "❌ Failed to get JMX configuration for server '" + serverInfo.getServerName() + "': " + e.getMessage();
            return null;
        }
    }
    
    /**
     * Show command usage
     */
    private static void showUsage() {
        System.out.println("Usage: lucli server monitor [options]");
        System.out.println();
        System.out.println("Monitor Lucee servers via JMX in three different ways:");
        System.out.println("  1. Auto-detect server in current directory (default)");
        System.out.println("  2. Monitor a named server instance");
        System.out.println("  3. Connect to arbitrary JMX endpoint");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --name, -n     Monitor a named server instance");
        System.out.println("  --host, -h     JMX host (for arbitrary endpoints)");
        System.out.println("  --port, -p     JMX port (for arbitrary endpoints)");
        System.out.println("  --refresh, -r  Refresh interval in seconds (default: 3)");
        System.out.println("  --help         Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Monitor server in current directory (auto-detected)");
        System.out.println("  lucli server monitor");
        System.out.println();
        System.out.println("  # Monitor a specific named server");
        System.out.println("  lucli server monitor --name my-app");
        System.out.println();
        System.out.println("  # Monitor arbitrary JMX endpoint");
        System.out.println("  lucli server monitor --host myserver --port 9999");
        System.out.println();
        System.out.println("  # Customize refresh interval");
        System.out.println("  lucli server monitor --refresh 5");
        System.out.println();
        System.out.println("Note: JMX monitoring must be enabled in the target server's lucee.json.");
        System.out.println("      Use 'lucli server list' to see available managed servers.");
    }
    
    /**
     * Get usage string for error messages
     */
    private static String getUsageString() {
        return "Usage: lucli server monitor [options]\n" +
               "\n" +
               "Monitor Lucee servers via JMX in three different ways:\n" +
               "  1. Auto-detect server in current directory (default)\n" +
               "  2. Monitor a named server instance\n" +
               "  3. Connect to arbitrary JMX endpoint\n" +
               "\n" +
               "Options:\n" +
               "  --name, -n     Monitor a named server instance\n" +
               "  --host, -h     JMX host (for arbitrary endpoints)\n" +
               "  --port, -p     JMX port (for arbitrary endpoints)\n" +
               "  --refresh, -r  Refresh interval in seconds (default: 3)\n" +
               "  --help         Show this help message";
    }
}
