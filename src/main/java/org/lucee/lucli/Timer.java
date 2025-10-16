package org.lucee.lucli;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for tracking execution times of various operations in LuCLI.
 * Provides both simple single-operation timing and hierarchical timing for nested operations.
 */
public class Timer {
    
    private static final Map<String, TimerEntry> timers = new ConcurrentHashMap<>();
    private static boolean enabled = false;
    private static final ThreadLocal<List<String>> timerStack = ThreadLocal.withInitial(ArrayList::new);
    
    /**
     * Enable or disable timing globally
     */
    public static void setEnabled(boolean enable) {
        enabled = enable;
        if (!enabled) {
            timers.clear();
        }
    }
    
    /**
     * Check if timing is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Start timing an operation
     */
    public static void start(String operationName) {
        if (!enabled) return;
        
        // Store the timer with the simple name for lookups
        timers.put(operationName, new TimerEntry(operationName, Instant.now()));
        timerStack.get().add(operationName);
    }
    
    /**
     * Stop timing an operation and record the duration
     */
    public static Duration stop(String operationName) {
        if (!enabled) return Duration.ZERO;
        
        TimerEntry entry = timers.get(operationName);
        
        if (entry == null) {
            // Only show warning if timing is enabled but no timer was found
            if (enabled) {
                System.err.println("Warning: Timer.stop() called for '" + operationName + "' but no corresponding start() was found");
            }
            return Duration.ZERO;
        }
        
        Duration duration = Duration.between(entry.startTime, Instant.now());
        entry.duration = duration;
        
        // Remove from stack
        List<String> stack = timerStack.get();
        stack.remove(operationName);
        
        return duration;
    }
    
    /**
     * Get a hierarchical operation name based on the current timer stack
     */
    private static String getFullOperationName(String operationName) {
        List<String> stack = timerStack.get();
        if (stack.isEmpty()) {
            return operationName;
        }
        
        StringBuilder sb = new StringBuilder();
        for (String parent : stack) {
            // Extract just the operation name part (after the last arrow)
            String parentOp = parent.contains(" → ") ? 
                parent.substring(parent.lastIndexOf(" → ") + 3) : parent;
            sb.append(parentOp).append(" → ");
        }
        sb.append(operationName);
        return sb.toString();
    }
    
    /**
     * Output all timing results
     */
    public static void printResults() {
        if (!enabled || timers.isEmpty()) {
            return;
        }
        
        System.out.println("\n⏱️  Timing Results:");
        System.out.println("─".repeat(60));
        
        // Sort by operation name for consistent output
        timers.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                TimerEntry timer = entry.getValue();
                if (timer.duration != null) {
                    String indent = getIndentForOperation(entry.getKey());
                    System.out.printf("%s%-40s %8s%n", 
                        indent,
                        timer.operationName, 
                        formatDuration(timer.duration)
                    );
                }
            });
            
        System.out.println("─".repeat(60));
    }
    
    /**
     * Get appropriate indentation for hierarchical display
     */
    private static String getIndentForOperation(String fullName) {
        int arrowCount = fullName.length() - fullName.replace(" → ", "").length();
        return "  ".repeat(arrowCount / 3); // Each " → " is 3 characters
    }
    
    /**
     * Format duration for display
     */
    private static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        
        if (millis < 1000) {
            return String.format("%d ms", millis);
        } else if (millis < 60000) {
            return String.format("%.2f s", millis / 1000.0);
        } else {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%d:%02d min", minutes, seconds);
        }
    }
    
    /**
     * Clear all timing data
     */
    public static void clear() {
        timers.clear();
        timerStack.remove();
    }
    
    /**
     * Get the duration of a specific operation (for programmatic access)
     */
    public static Duration getDuration(String operationName) {
        TimerEntry entry = timers.get(operationName);
        return entry != null ? entry.duration : Duration.ZERO;
    }
    
    /**
     * Internal class to hold timer information
     */
    private static class TimerEntry {
        final String operationName;
        final Instant startTime;
        Duration duration;
        
        TimerEntry(String operationName, Instant startTime) {
            this.operationName = operationName;
            this.startTime = startTime;
        }
    }
}
