package org.lucee.lucli.interactive;

import org.lucee.lucli.interactive.SampleData.*;

import java.io.IOException;
import java.util.List;

/**
 * Test command to demonstrate interactive table functionality
 */
public class InteractiveTestCommand {
    
    public static String execute(String[] args) {
        if (args.length == 0) {
            return showHelp();
        }
        
        String subCommand = args[0].toLowerCase();
        
        try {
            switch (subCommand) {
                case "servers":
                    showServersTable();
                    break;
                    
                case "logs":
                    showLogsTable();
                    break;
                    
                case "files":
                    showFilesTable();
                    break;
                    
                case "help":
                    return showHelp();
                    
                default:
                    return "❌ Unknown test command: " + subCommand + "\n" + showHelp();
            }
        } catch (IOException e) {
            return "❌ Error running interactive test: " + e.getMessage();
        }
        
        return ""; // Interactive mode handles its own output
    }
    
    private static void showServersTable() throws IOException {
        List<Server> servers = SampleData.generateServers();
        
        SimpleInteractiveTable<Server> table = new SimpleInteractiveTable.Builder<Server>()
            .title("🖥️  Server Management Dashboard")
            .data(servers)
            .addColumn("Name", Server::getName, 20)
            .addColumn("Status", Server::getStatus, 12)
            .addColumn("Port", server -> String.valueOf(server.getPort()), 6)
            .addColumn("Version", Server::getVersion, 12)
            .addColumn("CPU", server -> String.format("%.1f%%", server.getCpuUsage()), 8)
            .addColumn("Memory", Server::getFormattedMemory, 10)
            .addColumn("Started", Server::getFormattedStartTime, 16)
            .detailProvider(Server::getDetailedInfo)
            .build();
            
        table.run();
    }
    
    private static void showLogsTable() throws IOException {
        List<LogEntry> logs = SampleData.generateLogEntries();
        
        SimpleInteractiveTable<LogEntry> table = new SimpleInteractiveTable.Builder<LogEntry>()
            .title("📋 Application Log Viewer")
            .data(logs)
            .addColumn("Time", LogEntry::getFormattedTime, 10)
            .addColumn("Level", LogEntry::getLevel, 8)
            .addColumn("Source", LogEntry::getSource, 15)
            .addColumn("Thread", LogEntry::getThread, 14)
            .addColumn("Message", LogEntry::getShortMessage, 50)
            .detailProvider(LogEntry::getDetailedInfo)
            .build();
            
        table.run();
    }
    
    private static void showFilesTable() throws IOException {
        List<FileEntry> files = SampleData.generateFiles();
        
        SimpleInteractiveTable<FileEntry> table = new SimpleInteractiveTable.Builder<FileEntry>()
            .title("📁 File System Browser")
            .data(files)
            .addColumn("Name", FileEntry::getName, 20)
            .addColumn("Type", FileEntry::getType, 12)
            .addColumn("Size", FileEntry::getFormattedSize, 10)
            .addColumn("Permissions", FileEntry::getPermissions, 12)
            .addColumn("Modified", FileEntry::getFormattedDate, 12)
            .detailProvider(FileEntry::getDetailedInfo)
            .build();
            
        table.run();
    }
    
    private static String showHelp() {
        return "🔧 Interactive Test Commands\n\n" +
               "Usage: interactive <command>\n\n" +
               "Commands:\n" +
               "  servers     Show interactive server management table\n" +
               "  logs        Show interactive application logs table\n" +
               "  files       Show interactive file browser table\n" +
               "  help        Show this help message\n\n" +
               "Navigation:\n" +
               "  ↑↓ or j/k   Navigate between rows\n" +
               "  Enter       View detailed information\n" +
               "  q or Esc    Quit interactive mode\n" +
               "  r           Refresh display\n\n" +
               "Examples:\n" +
               "  interactive servers   # Browse server dashboard\n" +
               "  interactive logs      # View application logs\n" +
               "  interactive files     # Browse file system";
    }
}
