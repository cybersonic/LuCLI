package org.lucee.lucli.monitoring;

import java.text.DecimalFormat;
import java.util.Deque;
import java.util.List;

import org.lucee.lucli.monitoring.JmxConnection.GcMetrics;
import org.lucee.lucli.monitoring.JmxConnection.LuceeMetrics;
import org.lucee.lucli.monitoring.JmxConnection.MemoryMetrics;
import org.lucee.lucli.monitoring.JmxConnection.OsMetrics;
import org.lucee.lucli.monitoring.JmxConnection.RuntimeMetrics;
import org.lucee.lucli.monitoring.JmxConnection.ThreadingMetrics;

/**
 * ASCII-based CLI dashboard for displaying Lucee server metrics
 */
public class CliDashboard {
    
    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String RED = "\033[31m";
    private static final String BLUE = "\033[34m";
    private static final String BOLD = "\033[1m";
    private static final String CLEAR_SCREEN = "\033[2J\033[H";
    
    private final DecimalFormat df = new DecimalFormat("#.##");
    
    /**
     * Clear the terminal screen
     */
    public void clearScreen() {
        System.out.print(CLEAR_SCREEN);
        System.out.flush();
    }
    
    /**
     * Render the complete dashboard (without timeline)
     */
    public void renderDashboard(String serverName, ServerMetrics metrics) {
        renderDashboard(serverName, metrics, null);
    }

    /**
     * Render the complete dashboard with optional timeline history
     */
    public void renderDashboard(String serverName, ServerMetrics metrics, Deque<TimelineSample> history) {
        clearScreen();
        
        renderHeader(serverName, metrics.runtime);
        renderMemorySection(metrics.memory);
        renderThreadingSection(metrics.threading);
        renderGcSection(metrics.gcMetrics);
        renderSystemSection(metrics.os);
        renderTimeline(history);
        renderFooter();
    }
    
    /**
     * Render dashboard header with server info
     */
    private void renderHeader(String serverName, RuntimeMetrics runtime) {
        System.out.println(BOLD + "LUCEE SERVER MONITOR" + RESET);
        System.out.println("═".repeat(60));
        System.out.printf("Server: %-20s Status: %s●RUNNING%s%n", 
            serverName, GREEN, RESET);
        System.out.printf("Uptime: %-20s JVM: %s%n", 
            runtime.getFormattedUptime(), 
            truncate(runtime.vmName + " " + runtime.vmVersion, 35));
        System.out.println();
    }
    
    /**
     * Render memory usage section
     */
    private void renderMemorySection(MemoryMetrics memory) {
        System.out.println(BOLD + "Memory Usage" + RESET);
        
        double heapPercent = memory.getHeapUsagePercent();
        String heapBar = renderProgressBar(heapPercent, 30);
        String heapColor = getColorForPercentage(heapPercent);
        
        System.out.printf("Heap:     %s%s%s %s%3.0f%%%s (%s/%s)%n",
            heapColor, heapBar, RESET,
            heapColor, heapPercent, RESET,
            formatBytes(memory.heapUsed), formatBytes(memory.heapMax));
        
        double nonHeapPercent = memory.getNonHeapUsagePercent();
        String nonHeapBar = renderProgressBar(nonHeapPercent, 30);
        String nonHeapColor = getColorForPercentage(nonHeapPercent);
        
        System.out.printf("Non-Heap: %s%s%s %s%3.0f%%%s (%s/%s)%n",
            nonHeapColor, nonHeapBar, RESET,
            nonHeapColor, nonHeapPercent, RESET,
            formatBytes(memory.nonHeapUsed), formatBytes(memory.nonHeapMax));
        
        System.out.println();
    }
    
    /**
     * Render threading information
     */
    private void renderThreadingSection(ThreadingMetrics threading) {
        System.out.println(BOLD + "Threading" + RESET);
        System.out.printf("Active:  %s%-3d%s threads%n",
            BLUE, threading.threadCount, RESET);
        System.out.printf("Peak:    %s%-3d%s threads%n",
            BLUE, threading.peakThreadCount, RESET);
        System.out.printf("Daemon:  %s%-3d%s threads%n",
            BLUE, threading.daemonThreadCount, RESET);
        System.out.println();
    }
    
    /**
     * Render garbage collection section
     */
    private void renderGcSection(List<GcMetrics> gcMetrics) {
        System.out.println(BOLD + "Garbage Collection" + RESET);
        
        long totalCollections = 0;
        long totalTime = 0;
        
        for (GcMetrics gc : gcMetrics) {
            totalCollections += gc.collectionCount;
            totalTime += gc.collectionTime;
            
            String gcName = truncate(gc.name, 15);
            System.out.printf("%-15s: %s%,d%s collections (%s%,d%sms)%n",
                gcName, GREEN, gc.collectionCount, RESET, 
                YELLOW, gc.collectionTime, RESET);
        }
        
        if (!gcMetrics.isEmpty()) {
            System.out.printf("Total GC:       %s%,d%s collections (%s%,d%sms)%n",
                GREEN, totalCollections, RESET, YELLOW, totalTime, RESET);
        }
        
        System.out.println();
    }
    
    /**
     * Render system metrics section
     */
    private void renderSystemSection(OsMetrics os) {
        System.out.println(BOLD + "System Resources" + RESET);
        
        if (os.processCpuLoad != null && os.processCpuLoad >= 0) {
            double cpuPercent = os.processCpuLoad * 100;
            String cpuBar = renderProgressBar(cpuPercent, 30);
            String cpuColor = getColorForPercentage(cpuPercent);
            
            System.out.printf("CPU:      %s%s%s %s%3.0f%%%s%n",
                cpuColor, cpuBar, RESET, cpuColor, cpuPercent, RESET);
        } else {
            System.out.println("CPU:      N/A");
        }
        
        if (os.systemLoadAverage >= 0) {
            System.out.printf("Load:     %s%.2f%s/%d cores%n",
                BLUE, os.systemLoadAverage, RESET, os.availableProcessors);
        } else {
            System.out.printf("Load:     N/A/%d cores%n",
                os.availableProcessors);
        }
        
        System.out.println();
    }

    /**
     * Render a simple timeline of recent heap and CPU usage
     */
    private void renderTimeline(Deque<TimelineSample> history) {
        if (history == null || history.isEmpty()) {
            return;
        }

        System.out.println(BOLD + "Timeline (recent)" + RESET);

        // Build sparkline lines with per-bar coloring
        String heapLine = renderTimelineLine(history, true);
        String cpuLine = renderTimelineLine(history, false);

        // Each bar in the timeline is already colorized based on its own value,
        // so we don't recolor the entire line based on the most recent sample.
        System.out.printf("Heap: %s%s%n", heapLine, RESET);
        System.out.printf("CPU : %s%s%n", cpuLine, RESET);
        System.out.println();
    }

    private static final int TIMELINE_WIDTH = 30;

    /**
     * Convert recent samples into a compact sparkline-style timeline.
     * Each bar is colorized individually using the same green/yellow/red
     * banding as the main progress bars, so colors are stable over time.
     */
    private String renderTimelineLine(Deque<TimelineSample> history, boolean heap) {
        TimelineSample[] samples = history.toArray(new TimelineSample[0]);
        int size = samples.length;
        int width = Math.min(TIMELINE_WIDTH, size);

        StringBuilder sb = new StringBuilder(width * 8); // extra space for ANSI codes
        for (int i = size - width; i < size; i++) {
            double value = heap ? samples[i].heapPercent : samples[i].cpuPercent;
            if (value < 0) {
                // No data for this sample
                sb.append(' ');
            } else {
                String color = getColorForPercentage(value);
                sb.append(color).append(valueToBlockChar(value));
            }
        }
        return sb.toString();
    }

    /**
     * Map a percentage to a block character for the timeline
     * Uses increasing-height block elements so higher values appear "taller".
     */
    private char valueToBlockChar(double percent) {
        if (percent < 0) {
            return ' ';
        }
        // Unicode block elements from low to high
        if (percent < 10) return '\u2581'; // ▁
        if (percent < 30) return '\u2582'; // ▂
        if (percent < 50) return '\u2583'; // ▃
        if (percent < 70) return '\u2584'; // ▄
        if (percent < 85) return '\u2585'; // ▅
        if (percent < 95) return '\u2586'; // ▆
        return '\u2587'; // ▇
    }
    
    /**
     * Render footer with controls
     */
    private void renderFooter() {
        System.out.println("─".repeat(60));
        System.out.println("Commands: 'q' + Enter to quit, 'r' + Enter to refresh, 'h' + Enter for help");
    }
    
    /**
     * Render a progress bar
     */
    private String renderProgressBar(double percentage, int width) {
        int filled = Math.min(width, Math.max(0, (int) (percentage / 100.0 * width)));
        int empty = width - filled;
        
        return "█".repeat(filled) + "░".repeat(empty);
    }
    
    /**
     * Get color based on percentage (green/yellow/red)
     */
    private String getColorForPercentage(double percentage) {
        if (percentage < 70) {
            return GREEN;
        } else if (percentage < 90) {
            return YELLOW;
        } else {
            return RED;
        }
    }
    
    /**
     * Format bytes to human readable format
     */
    private String formatBytes(long bytes) {
        if (bytes < 0) return "N/A";
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double size = bytes;
        int unitIndex = 0;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f%s", size, units[unitIndex]);
    }
    
    /**
     * Truncate string to specified length
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Display error message
     */
    public void renderError(String message) {
        clearScreen();
        System.out.println(BOLD + RED + "ERROR" + RESET);
        System.out.println("─".repeat(40));
        System.out.printf("%s%s%s%n", RED, message, RESET);
        System.out.println();
        System.out.println("Press any key to exit...");
    }
    
    /**
     * Display connection message
     */
    public void renderConnecting(String host, int port) {
        clearScreen();
        System.out.println(BOLD + "CONNECTING" + RESET);
        System.out.println("─".repeat(40));
        System.out.printf("Connecting to JMX server at %s%s:%d%s...%n", 
            BLUE, host, port, RESET);
        System.out.println("Please wait...");
    }
    
    /**
     * Container class for all server metrics
     */
    public static class ServerMetrics {
        public final MemoryMetrics memory;
        public final ThreadingMetrics threading;
        public final List<GcMetrics> gcMetrics;
        public final RuntimeMetrics runtime;
        public final OsMetrics os;
        public final LuceeMetrics lucee;
        
        public ServerMetrics(MemoryMetrics memory, ThreadingMetrics threading, 
                           List<GcMetrics> gcMetrics, RuntimeMetrics runtime, 
                           OsMetrics os, LuceeMetrics lucee) {
            this.memory = memory;
            this.threading = threading;
            this.gcMetrics = gcMetrics;
            this.runtime = runtime;
            this.os = os;
            this.lucee = lucee;
        }
    }

    /**
     * Sample used for timeline history (heap and CPU percentages)
     */
    public static class TimelineSample {
        public final double heapPercent;
        public final double cpuPercent;

        public TimelineSample(double heapPercent, double cpuPercent) {
            this.heapPercent = heapPercent;
            this.cpuPercent = cpuPercent;
        }
    }
}
