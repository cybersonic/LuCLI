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
    
    private static Timer instance;
    
    // Instance fields (non-static so each instance has its own state)
    private final Map<String, TimerEntry> timers = new ConcurrentHashMap<>();
    private boolean enabled = false;
    private final ThreadLocal<List<String>> timerStack = ThreadLocal.withInitial(ArrayList::new);

    private Timer() {
    }

    
    /**
     * Enable or disable timing globally
     */
    public static void setEnabled(boolean enable) {
        getInstance()._setEnabled(enable);
    }

    /** Instance implementation of setEnabled */
    public void _setEnabled(boolean enable) {
        enabled = enable;
        if (!enabled) {
            timers.clear();
        }
    }
    
    /**
     * Check if timing is enabled
     */
    public static boolean isEnabled() {
        return getInstance()._isEnabled();
    }

    /** Instance implementation of isEnabled */
    public boolean _isEnabled() {
        return enabled;
    }


    public static Timer getInstance(){
        if(instance == null){
            synchronized (Timer.class) {
                if(instance == null){
                    instance = new Timer();
                }
            }
        }
        return instance;
    }
    /**
     * Instance implementation for starting a timer
     */
    public void _start(String operationName) {
        if (!enabled) return;
        timers.put(operationName, new TimerEntry(operationName, Instant.now()));
        timerStack.get().add(operationName);
    }

    /**
     * Start timing an operation (static facade)
     */
    public static void start(String operationName) {
        getInstance()._start(operationName);
    }
    
    /**
     * Instance implementation for stopping a timer
     */
    public Duration _stop(String operationName) {
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
     * Stop timing an operation and record the duration (static facade)
     */
    public static Duration stop(String operationName) {
        return getInstance()._stop(operationName);
    }
    
    /**
     * Get a hierarchical operation name based on the current timer stack
     */
    private static String getFullOperationName(String operationName) {
        List<String> stack = getInstance().timerStack.get();
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
        getInstance()._printResults();
    }

    /** Instance implementation of printResults */
    public void _printResults() {
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
        getInstance()._clear();
    }

    /** Instance implementation of clear */
    public void _clear() {
        timers.clear();
        timerStack.remove();
    }
    
    /**
     * Get the duration of a specific operation (for programmatic access)
     */
    public static Duration getDuration(String operationName) {
        return getInstance()._getDuration(operationName);
    }

    /** Instance implementation of getDuration */
    public Duration _getDuration(String operationName) {
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
