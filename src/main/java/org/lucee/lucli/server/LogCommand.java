package org.lucee.lucli.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.lucee.lucli.StringOutput;

/**
 * Handles server log operations - viewing different types of logs with optional follow functionality
 */
public class LogCommand {
    
    public enum LogType {
        TOMCAT("tomcat", "Tomcat server logs"),
        SERVER("server", "Lucee server logs"),
        WEB("web", "Lucee web application logs");
        
        private final String name;
        private final String description;
        
        LogType(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        
        public static LogType fromString(String name) {
            for (LogType type : values()) {
                if (type.name.equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    /**
     * Execute the log command with the given arguments
     */
    public static void executeLog(String[] args) {
        LogOptions options = parseArguments(args);
        
        if (options.showHelp) {
            showHelp();
            return;
        }
        
        try {
            Path currentDir = Paths.get(System.getProperty("user.dir"));
            LuceeServerManager serverManager = new LuceeServerManager();
            
            // Get the server instance for the current directory
            LuceeServerManager.ServerInstance serverInstance = serverManager.getRunningServer(currentDir);
            if (serverInstance == null) {
                System.err.println("No running server found for the current directory.");
                System.err.println("Use 'lucli server start' to start a server first.");
                System.exit(1);
            }
            
            // Get the log file path based on the type and log name
            Path logFile = getLogFile(serverInstance.getServerDir(), options);
            
            if (!Files.exists(logFile)) {
                System.err.println("Log file not found: " + logFile);
                if (options.logType == LogType.WEB) {
                    System.err.println("Web logs may not exist until the web context is initialized.");
                    System.err.println("Try accessing your application first to generate web logs.");
                }
                System.exit(1);
            }
            
            // Display the log
            if (options.follow) {
                followLog(logFile, options.lines);
            } else {
                displayLog(logFile, options.lines);
            }
            
        } catch (Exception e) {
            System.err.println("Error accessing server logs: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Get the appropriate log file based on type and options
     */
    private static Path getLogFile(Path serverDir, LogOptions options) {
        switch (options.logType) {
            case TOMCAT:
                return getTomcatLogFile(serverDir, options.logName);
            case SERVER:
                return getServerLogFile(serverDir, options.logName);
            case WEB:
                return getWebLogFile(serverDir, options.logName);
            default:
                throw new IllegalArgumentException("Unknown log type: " + options.logType);
        }
    }
    
    /**
     * Get Tomcat log file
     */
    private static Path getTomcatLogFile(Path serverDir, String logName) {
        Path logsDir = serverDir.resolve("logs");
        
        if (logName != null) {
            // Specific log file requested
            Path specificLog = logsDir.resolve(logName);
            if (Files.exists(specificLog)) {
                return specificLog;
            }
            // Try with .log extension
            specificLog = logsDir.resolve(logName + ".log");
            if (Files.exists(specificLog)) {
                return specificLog;
            }
        }
        
        // Default to catalina log with current date
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path catalinaLog = logsDir.resolve("catalina." + today + ".log");
        if (Files.exists(catalinaLog)) {
            return catalinaLog;
        }
        
        // Fallback to server.err
        return logsDir.resolve("server.err");
    }
    
    /**
     * Get Lucee server log file
     */
    private static Path getServerLogFile(Path serverDir, String logName) {
        Path serverLogsDir = serverDir.resolve("lucee-server/context/logs");
        
        if (logName != null) {
            Path specificLog = serverLogsDir.resolve(logName);
            if (Files.exists(specificLog)) {
                return specificLog;
            }
            // Try with .log extension
            specificLog = serverLogsDir.resolve(logName + ".log");
            if (Files.exists(specificLog)) {
                return specificLog;
            }
        }
        
        // Default to application.log
        Path appLog = serverLogsDir.resolve("application.log");
        if (Files.exists(appLog)) {
            return appLog;
        }
        
        // Fallback to out.log which usually has content
        return serverLogsDir.resolve("out.log");
    }
    
    /**
     * Get Lucee web log file
     */
    private static Path getWebLogFile(Path serverDir, String logName) {
        // Check if lucee-web/logs exists (our new configuration)
        Path webLogsDir = serverDir.resolve("lucee-web/logs");
        
        // If it doesn't exist, web context hasn't been initialized yet
        if (!Files.exists(webLogsDir)) {
            // Return a path that will trigger the "not found" message
            return webLogsDir.resolve("application.log");
        }
        
        if (logName != null) {
            Path specificLog = webLogsDir.resolve(logName);
            if (Files.exists(specificLog)) {
                return specificLog;
            }
            // Try with .log extension
            specificLog = webLogsDir.resolve(logName + ".log");
            if (Files.exists(specificLog)) {
                return specificLog;
            }
        }
        
        // Default to application.log
        return webLogsDir.resolve("application.log");
    }
    
    /**
     * Display log content with tail functionality
     */
    private static void displayLog(Path logFile, int lines) throws IOException {
        if (lines > 0) {
            // Use tail command if available, otherwise read from end
            try {
                ProcessBuilder pb = new ProcessBuilder("tail", "-n", String.valueOf(lines), logFile.toString());
                Process process = pb.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                // Fallback to reading entire file (not ideal for large files)
                List<String> allLines = Files.readAllLines(logFile);
                int start = Math.max(0, allLines.size() - lines);
                for (int i = start; i < allLines.size(); i++) {
                    System.out.println(allLines.get(i));
                }
            }
        } else {
            // Display entire file
            List<String> allLines = Files.readAllLines(logFile);
            for (String line : allLines) {
                System.out.println(line);
            }
        }
    }
    
    /**
     * Follow log file (like tail -f)
     */
    private static void followLog(Path logFile, int initialLines) {
        try {
            // Show initial lines if requested
            if (initialLines > 0) {
                displayLog(logFile, initialLines);
            }
            
            // Use tail -f command
            ProcessBuilder pb = new ProcessBuilder("tail", "-f", logFile.toString());
            Process process = pb.start();
            
            // Handle Ctrl-C to stop following
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                process.destroy();
                System.out.println("\nStopped following log file.");
            }));
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error following log file: " + e.getMessage());
            System.err.println("Follow mode requires the 'tail' command to be available.");
        }
    }
    
    /**
     * Parse command line arguments
     */
    private static LogOptions parseArguments(String[] args) {
        LogOptions options = new LogOptions();
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            switch (arg) {
                case "--help":
                case "-h":
                    options.showHelp = true;
                    break;
                    
                case "--follow":
                case "-f":
                    options.follow = true;
                    break;
                    
                case "--type":
                case "-t":
                    if (i + 1 < args.length) {
                        LogType type = LogType.fromString(args[++i]);
                        if (type == null) {
                            System.err.println("Invalid log type: " + args[i]);
                            System.err.println("Valid types are: tomcat, server, web");
                            System.exit(1);
                        }
                        options.logType = type;
                    }
                    break;
                    
                case "--log-name":
                case "-l":
                    if (i + 1 < args.length) {
                        options.logName = args[++i];
                    }
                    break;
                    
                case "--lines":
                case "-n":
                    if (i + 1 < args.length) {
                        try {
                            options.lines = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid number of lines: " + args[i]);
                            System.exit(1);
                        }
                    }
                    break;
                    
                default:
                    // If it's not a flag, treat it as log name if none specified
                    if (!arg.startsWith("-") && options.logName == null) {
                        options.logName = arg;
                    }
            }
        }
        
        return options;
    }
    
    /**
     * Show help for the log command
     */
    private static void showHelp() {
        System.out.println(StringOutput.loadText("/text/log-help.txt"));
    }
    
    /**
     * Options for log command
     */
    private static class LogOptions {
        LogType logType = LogType.TOMCAT;
        String logName = null;
        boolean follow = false;
        int lines = 50;
        boolean showHelp = false;
    }
}
