package org.lucee.lucli.monitoring;

import org.lucee.lucli.monitoring.CliDashboard.ServerMetrics;
import org.lucee.lucli.monitoring.JmxConnection.*;

import java.io.IOException;
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
        System.out.println("┌─────────────────── HELP ───────────────────┐");
        System.out.println("│ Available Commands:                         │");
        System.out.println("│                                             │");
        System.out.println("│ q, quit, exit - Quit monitoring            │");
        System.out.println("│ r, refresh    - Force refresh              │");
        System.out.println("│ h, help       - Show this help             │");
        System.out.println("│ c, clear      - Clear screen               │");
        System.out.println("│                                             │");
        System.out.println("│ The dashboard auto-refreshes every few     │");
        System.out.println("│ seconds. Use 'q' to quit safely.           │");
        System.out.println("└─────────────────────────────────────────────┘");
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
     */
    public static void executeMonitor(String[] args) {
        String host = "localhost";
        int port = 8999;
        int refreshInterval = 3;
        
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
                            System.err.println("Invalid port number: " + args[i]);
                            return;
                        }
                    }
                    break;
                case "--refresh":
                case "-r":
                    if (i + 1 < args.length) {
                        try {
                            refreshInterval = Integer.parseInt(args[++i]);
                            if (refreshInterval < 1) {
                                System.err.println("Refresh interval must be at least 1 second");
                                return;
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid refresh interval: " + args[i]);
                            return;
                        }
                    }
                    break;
                case "--help":
                    showUsage();
                    return;
                default:
                    if (args[i].startsWith("-")) {
                        System.err.println("Unknown option: " + args[i]);
                        showUsage();
                        return;
                    }
                    break;
            }
        }
        
        // Start monitoring
        MonitorCommand monitor = new MonitorCommand();
        
        // Add shutdown hook for clean exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            monitor.cleanup();
            System.out.println("\nMonitoring stopped.");
        }));
        
        monitor.startMonitoring(host, port, refreshInterval);
    }
    
    /**
     * Show command usage
     */
    private static void showUsage() {
        System.out.println("Usage: lucli server monitor [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --host, -h     JMX host (default: localhost)");
        System.out.println("  --port, -p     JMX port (default: 8999)"); 
        System.out.println("  --refresh, -r  Refresh interval in seconds (default: 3)");
        System.out.println("  --help         Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  lucli server monitor");
        System.out.println("  lucli server monitor --host myserver --port 9999");
        System.out.println("  lucli server monitor --refresh 5");
        System.out.println();
        System.out.println("Note: The target Lucee server must have JMX enabled.");
    }
}
