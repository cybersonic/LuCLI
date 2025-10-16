package org.lucee.lucli.interactive;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sample data models and generators for interactive table demonstrations
 */
public class SampleData {
    
    /**
     * Sample server data model
     */
    public static class Server {
        private final String name;
        private final String status;
        private final int port;
        private final String version;
        private final double cpuUsage;
        private final long memoryMB;
        private final LocalDateTime lastStarted;
        
        public Server(String name, String status, int port, String version, double cpuUsage, long memoryMB, LocalDateTime lastStarted) {
            this.name = name;
            this.status = status;
            this.port = port;
            this.version = version;
            this.cpuUsage = cpuUsage;
            this.memoryMB = memoryMB;
            this.lastStarted = lastStarted;
        }
        
        // Getters
        public String getName() { return name; }
        public String getStatus() { return status; }
        public int getPort() { return port; }
        public String getVersion() { return version; }
        public double getCpuUsage() { return cpuUsage; }
        public long getMemoryMB() { return memoryMB; }
        public LocalDateTime getLastStarted() { return lastStarted; }
        
        public String getFormattedMemory() {
            if (memoryMB >= 1024) {
                return String.format("%.1fGB", memoryMB / 1024.0);
            }
            return memoryMB + "MB";
        }
        
        public String getFormattedStartTime() {
            return lastStarted.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
        
        public String getDetailedInfo() {
            StringBuilder details = new StringBuilder();
            details.append("🖥️  Server Details\\n");
            details.append("═══════════════════════════════════════════════════════\\n\\n");
            details.append("📋 Name:           ").append(name).append("\\n");
            details.append("🔴 Status:         ").append(status).append("\\n");
            details.append("🌐 Port:           ").append(port).append("\\n");
            details.append("⚡ Version:        ").append(version).append("\\n");
            details.append("📊 CPU Usage:      ").append(String.format("%.1f%%", cpuUsage)).append("\\n");
            details.append("💾 Memory:         ").append(getFormattedMemory()).append("\\n");
            details.append("⏰ Last Started:   ").append(getFormattedStartTime()).append("\\n\\n");
            
            details.append("🔧 Configuration\\n");
            details.append("─────────────────────────────────────────────────────\\n");
            details.append("• Web Root:        ./webroot/\\n");
            details.append("• Admin Port:      ").append(port + 100).append("\\n");
            details.append("• SSL Enabled:     ").append(port == 8443 ? "Yes" : "No").append("\\n");
            details.append("• Debug Mode:      ").append(status.equals("RUNNING") ? "Enabled" : "Disabled").append("\\n\\n");
            
            details.append("📈 Performance Metrics\\n");
            details.append("─────────────────────────────────────────────────────\\n");
            details.append("• Request Count:   ").append(new Random().nextInt(10000) + 1000).append("\\n");
            details.append("• Error Rate:      ").append(String.format("%.2f%%", Math.random() * 2)).append("\\n");
            details.append("• Avg Response:    ").append(String.format("%.0fms", 50 + Math.random() * 200)).append("\\n");
            details.append("• Uptime:          ").append(String.format("%.1f days", Math.random() * 30)).append("\\n");
            
            return details.toString();
        }
    }
    
    /**
     * Sample log entry data model
     */
    public static class LogEntry {
        private final LocalDateTime timestamp;
        private final String level;
        private final String source;
        private final String message;
        private final String thread;
        
        public LogEntry(LocalDateTime timestamp, String level, String source, String message, String thread) {
            this.timestamp = timestamp;
            this.level = level;
            this.source = source;
            this.message = message;
            this.thread = thread;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getLevel() { return level; }
        public String getSource() { return source; }
        public String getMessage() { return message; }
        public String getThread() { return thread; }
        
        public String getFormattedTime() {
            return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
        
        public String getShortMessage() {
            return message.length() > 50 ? message.substring(0, 47) + "..." : message;
        }
        
        public String getDetailedInfo() {
            StringBuilder details = new StringBuilder();
            details.append("📋 Log Entry Details\\n");
            details.append("═══════════════════════════════════════════════════════\\n\\n");
            details.append("⏰ Timestamp:      ").append(timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))).append("\\n");
            details.append("📊 Level:          ").append(level).append("\\n");
            details.append("🔧 Source:         ").append(source).append("\\n");
            details.append("🧵 Thread:         ").append(thread).append("\\n\\n");
            details.append("💬 Message\\n");
            details.append("─────────────────────────────────────────────────────\\n");
            details.append(message).append("\\n\\n");
            
            // Add some contextual info
            if (level.equals("ERROR")) {
                details.append("🚨 Error Context\\n");
                details.append("─────────────────────────────────────────────────────\\n");
                details.append("• Error Type:      RuntimeException\\n");
                details.append("• Stack Trace:     Available (truncated for demo)\\n");
                details.append("• Resolution:      Check application configuration\\n");
            } else if (level.equals("WARN")) {
                details.append("⚠️  Warning Context\\n");
                details.append("─────────────────────────────────────────────────────\\n");
                details.append("• Impact:          Low\\n");
                details.append("• Action:          Monitor for patterns\\n");
                details.append("• Related:         Performance optimization\\n");
            }
            
            return details.toString();
        }
    }
    
    /**
     * Sample file system entry
     */
    public static class FileEntry {
        private final String name;
        private final String type;
        private final long size;
        private final String permissions;
        private final LocalDateTime modified;
        
        public FileEntry(String name, String type, long size, String permissions, LocalDateTime modified) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.permissions = permissions;
            this.modified = modified;
        }
        
        // Getters
        public String getName() { return name; }
        public String getType() { return type; }
        public long getSize() { return size; }
        public String getPermissions() { return permissions; }
        public LocalDateTime getModified() { return modified; }
        
        public String getFormattedSize() {
            if (type.equals("directory")) return "-";
            if (size >= 1024 * 1024) return String.format("%.1fMB", size / (1024.0 * 1024));
            if (size >= 1024) return String.format("%.1fKB", size / 1024.0);
            return size + "B";
        }
        
        public String getFormattedDate() {
            return modified.format(DateTimeFormatter.ofPattern("MMM dd HH:mm"));
        }
        
        public String getDetailedInfo() {
            StringBuilder details = new StringBuilder();
            details.append("📁 File Details\\n");
            details.append("═══════════════════════════════════════════════════════\\n\\n");
            details.append("📄 Name:           ").append(name).append("\\n");
            details.append("📂 Type:           ").append(type).append("\\n");
            details.append("📏 Size:           ").append(getFormattedSize()).append(" (").append(size).append(" bytes)\\n");
            details.append("🔐 Permissions:    ").append(permissions).append("\\n");
            details.append("⏰ Modified:       ").append(modified.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\\n\\n");
            
            if (!type.equals("directory")) {
                details.append("📄 File Info\\n");
                details.append("─────────────────────────────────────────────────────\\n");
                String extension = name.contains(".") ? name.substring(name.lastIndexOf(".") + 1) : "none";
                details.append("• Extension:       ").append(extension).append("\\n");
                details.append("• MIME Type:       ").append(getMimeType(extension)).append("\\n");
                details.append("• Readable:        ").append(permissions.charAt(0) == 'r' ? "Yes" : "No").append("\\n");
                details.append("• Writable:        ").append(permissions.charAt(1) == 'w' ? "Yes" : "No").append("\\n");
                details.append("• Executable:      ").append(permissions.charAt(2) == 'x' ? "Yes" : "No").append("\\n");
            }
            
            return details.toString();
        }
        
        private String getMimeType(String extension) {
            switch (extension.toLowerCase()) {
                case "txt": return "text/plain";
                case "java": return "text/x-java";
                case "json": return "application/json";
                case "xml": return "application/xml";
                case "cfm": case "cfc": case "cfs": return "application/cfml";
                case "js": return "application/javascript";
                case "html": return "text/html";
                case "css": return "text/css";
                default: return "application/octet-stream";
            }
        }
    }
    
    /**
     * Generate sample server data
     */
    public static List<Server> generateServers() {
        List<Server> servers = new ArrayList<>();
        Random random = new Random();
        
        String[] names = {"web-server-1", "api-gateway", "auth-service", "data-processor", "file-server", 
                         "cms-backend", "user-portal", "admin-dashboard", "monitoring-service", "cache-cluster"};
        String[] versions = {"6.2.2.91", "6.1.5.34", "6.2.0.123", "5.9.8.45", "6.2.1.67"};
        String[] statuses = {"RUNNING", "STOPPED", "STARTING", "ERROR", "MAINTENANCE"};
        
        for (int i = 0; i < names.length; i++) {
            String status = statuses[random.nextInt(statuses.length)];
            servers.add(new Server(
                names[i],
                status,
                8000 + i * 10 + random.nextInt(5),
                versions[random.nextInt(versions.length)],
                random.nextDouble() * 100,
                256 + random.nextInt(2048),
                LocalDateTime.now().minusHours(random.nextInt(24 * 7))
            ));
        }
        
        return servers;
    }
    
    /**
     * Generate sample log entries
     */
    public static List<LogEntry> generateLogEntries() {
        List<LogEntry> logs = new ArrayList<>();
        Random random = new Random();
        
        String[] levels = {"INFO", "WARN", "ERROR", "DEBUG", "TRACE"};
        String[] sources = {"WebEngine", "DatabasePool", "AuthFilter", "CacheManager", "FileUpload", 
                           "SessionManager", "RequestHandler", "ConfigLoader", "SecurityManager"};
        String[] messages = {
            "Request processed successfully in 45ms",
            "Database connection pool exhausted, creating new connection",
            "Authentication failed for user admin@example.com",
            "Cache miss for key: user_session_12345",
            "File upload completed: document.pdf (2.3MB)",
            "Session expired for user ID 789",
            "Configuration reloaded from lucee.json",
            "SSL certificate will expire in 30 days",
            "Memory usage: 512MB / 1024MB (50%)",
            "Scheduled task completed: daily_backup"
        };
        
        for (int i = 0; i < 25; i++) {
            logs.add(new LogEntry(
                LocalDateTime.now().minusMinutes(i * 5 + random.nextInt(300)),
                levels[random.nextInt(levels.length)],
                sources[random.nextInt(sources.length)],
                messages[random.nextInt(messages.length)],
                "http-thread-" + (random.nextInt(10) + 1)
            ));
        }
        
        return logs;
    }
    
    /**
     * Generate sample file entries
     */
    public static List<FileEntry> generateFiles() {
        List<FileEntry> files = new ArrayList<>();
        Random random = new Random();
        
        // Directories
        files.add(new FileEntry("src", "directory", 0, "drwxr-xr-x", LocalDateTime.now().minusDays(5)));
        files.add(new FileEntry("target", "directory", 0, "drwxr-xr-x", LocalDateTime.now().minusHours(2)));
        files.add(new FileEntry("docs", "directory", 0, "drwxr-xr-x", LocalDateTime.now().minusDays(10)));
        
        // Files
        String[] fileNames = {"lucee.json", "pom.xml", "README.md", "Application.cfc", "index.cfm", 
                             "config.properties", "database.sql", "deploy.sh", "Dockerfile", "build.xml"};
        String[] fileTypes = {"config", "build", "documentation", "application", "template", 
                             "configuration", "database", "script", "container", "build"};
        long[] fileSizes = {2048, 15432, 8765, 12345, 6789, 3456, 25689, 1234, 4567, 9876};
        
        for (int i = 0; i < fileNames.length; i++) {
            files.add(new FileEntry(
                fileNames[i],
                fileTypes[i],
                fileSizes[i] + random.nextInt(10000),
                "rw-r--r--",
                LocalDateTime.now().minusHours(random.nextInt(24 * 7))
            ));
        }
        
        return files;
    }
}
