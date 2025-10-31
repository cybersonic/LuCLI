package org.lucee.lucli.debug;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug command handler - provides the k9s-style debug interface functionality
 * This is a Java implementation that wraps the DebugExplorer and DebugTUI classes
 */
public class DebugCommand {
    
    /**
     * Execute the debug command with the provided arguments
     */
    public static String executeDebug(String[] args) {
        Map<String, String> parsedArgs = parseArguments(args);
        
        // Show help if requested
        if (parsedArgs.containsKey("help") || parsedArgs.containsKey("h")) {
            return getHelpText();
        }
        
        // Get connection parameters
        String host = parsedArgs.getOrDefault("host", "localhost");
        int port;
        try {
            port = Integer.parseInt(parsedArgs.getOrDefault("port", "8999"));
        } catch (NumberFormatException e) {
            return "❌ Invalid port number: " + parsedArgs.get("port");
        }
        
        try (DebugExplorer explorer = new DebugExplorer(host, port)) {
            System.out.println("Connecting to Lucee server at " + host + ":" + port + "...");
            explorer.connect();
            System.out.println("Connected successfully!\n");
            
            if (parsedArgs.containsKey("interactive") || parsedArgs.containsKey("tui")) {
                // Interactive TUI mode temporarily disabled
                return "❌ Interactive TUI mode is temporarily disabled due to terminal compatibility issues.\n" +
                       "💡 Use --report for detailed information or run without flags for summary.";
                
            } else if (parsedArgs.containsKey("report")) {
                // Generate detailed report
                return explorer.generateDebugReport();
                
            } else {
                // Default: show summary
                StringBuilder result = new StringBuilder();
                result.append("=== Debug Explorer Summary ===\n");
                
                // Get Lucee MBeans
                List<DebugExplorer.LuceeMBeanInfo> luceeBeans = explorer.discoverLuceeMBeans();
                result.append("Lucee MBeans found: ").append(luceeBeans.size()).append("\n");
                
                for (DebugExplorer.LuceeMBeanInfo bean : luceeBeans) {
                    result.append("  - ").append(bean.objectName.toString()).append("\n");
                }
                
                result.append("\n");
                
                // Get request-related MBeans
                List<javax.management.ObjectName> requestBeans = explorer.findRequestMBeans();
                result.append("Request-related MBeans found: ").append(requestBeans.size()).append("\n");
                
                // Show first few
                int maxShow = Math.min(5, requestBeans.size());
                for (int i = 0; i < maxShow; i++) {
                    result.append("  - ").append(requestBeans.get(i).toString()).append("\n");
                }
                
                if (requestBeans.size() > 5) {
                    result.append("  ... and ").append(requestBeans.size() - 5).append(" more\n");
                }
                
                result.append("\nUse --report for detailed information\n");
                result.append("Use --interactive for TUI mode\n");
                
                return result.toString();
            }
            
        } catch (Exception e) {
            StringBuilder error = new StringBuilder();
            error.append("Error: ").append(e.getMessage()).append("\n");
            
            if (e.getCause() != null) {
                error.append("Cause: ").append(e.getCause().getMessage()).append("\n");
            }
            
            error.append("\nMake sure:\n");
            error.append("1. Lucee server is running\n");
            error.append("2. JMX is enabled on the server\n");
            error.append("3. Correct host and port are specified\n");
            error.append("4. Network connectivity to JMX port\n");
            
            return error.toString();
        }
    }
    
    /**
     * Parse command line arguments into a map
     */
    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> parsed = new HashMap<>();
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--") && !args[i + 1].startsWith("-")) {
                    // Has value
                    parsed.put(key, args[i + 1]);
                    i++; // Skip next argument
                } else {
                    // Flag without value
                    parsed.put(key, "true");
                }
            } else if (arg.startsWith("-") && arg.length() == 2) {
                // Short flag
                String key = arg.substring(1);
                parsed.put(key, "true");
            }
        }
        
        return parsed;
    }
    
    /**
     * Get help text for the debug command
     */
    private static String getHelpText() {
        return """
               
               Lucee Debug Explorer - k9s-style debugging interface
               
               Usage: lucli server debug [options]
               
               Options:
                 --host <host>     JMX server host (default: localhost)
                 --port <port>     JMX server port (default: 8999)
                 --report          Generate full debug report
                 --interactive     Start interactive TUI mode (temporarily disabled)
                 --help, -h        Show this help
               
               Examples:
                 lucli server debug                           # Connect to local server and explore
                 lucli server debug --host myserver --port 9999  # Connect to remote server
                 lucli server debug --report                  # Generate detailed report
                 lucli server debug --interactive             # Start interactive TUI mode
               
               """;
    }
    
    /**
     * Main method for testing debug command
     */
    public static void main(String[] args) {
        String result = executeDebug(args);
        if (!result.isEmpty()) {
            System.out.println(result);
        }
    }
}
