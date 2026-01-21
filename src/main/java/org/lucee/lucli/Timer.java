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

    private static final int MAX_OPERATION_NAME_LENGTH = 100;
    private static final int MIN_OPERATION_NAME_LENGTH = 20;
    private static final int BASE_LINE_WIDTH = 60;
    private static final int BAR_WIDTH = 40;
    
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
        
        Instant endTime = Instant.now();
        Duration duration = Duration.between(entry.startTime, endTime);
        entry.duration = duration;
        entry.endTime = endTime;
        
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
        getInstance()._printResultsBar();
    }

    /**
     * Output timing results as a simple timeline/bar chart to visualize overlaps.
     */
    private static void printResultsBar() {
        getInstance()._printResultsBar();
    }

    /** Instance implementation of printResults */
    public void _printResults() {
        if (!enabled || timers.isEmpty()) {
            return;
        }
        
        // Determine the column width based on the longest operation name,
        // but cap it so excessively long names don't break the layout.
        int longestName = timers.values().stream()
            .filter(timer -> timer.duration != null)
            .mapToInt(timer -> timer.operationName.length())
            .max()
            .orElse(0);

        int nameColumnWidth = Math.min(
            MAX_OPERATION_NAME_LENGTH,
            Math.max(longestName, MIN_OPERATION_NAME_LENGTH)
        );

        int lineWidth = Math.max(BASE_LINE_WIDTH, nameColumnWidth + 15);
        String format = "%s%-" + nameColumnWidth + "." + nameColumnWidth + "s %8s%n";
        
        System.out.println("\n⏱️  Timing Results:");
        System.out.println("─".repeat(lineWidth));
        
        // Sort by operation name for consistent output
        timers.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                TimerEntry timer = entry.getValue();
                if (timer.duration != null) {
                    String indent = getIndentForOperation(entry.getKey());
                    System.out.printf(
                        format,
                        indent,
                        timer.operationName,
                        formatDuration(timer.duration)
                    );
                }
            });
            
        System.out.println("─".repeat(lineWidth));
    }

    /**
     * Instance implementation of the bar/timeline view.
     */
    public void _printResultsBar() {
        if (!enabled || timers.isEmpty()) {
            return;
        }

        // Only consider completed timers
        List<TimerEntry> completed = timers.values().stream()
            .filter(t -> t.duration != null)
            .toList();

        if (completed.isEmpty()) {
            return;
        }

        // Determine earliest start and latest end to build a global timeline
        Instant earliestStart = completed.stream()
            .map(t -> t.startTime)
            .min(Instant::compareTo)
            .orElse(null);

        Instant latestEnd = completed.stream()
            .map(t -> t.getEndTime())
            .max(Instant::compareTo)
            .orElse(null);

        if (earliestStart == null || latestEnd == null) {
            return;
        }

        long totalMillis = Math.max(1, Duration.between(earliestStart, latestEnd).toMillis());


        // Determine name column width similar to _printResults
        int longestName = completed.stream()
            .mapToInt(timer -> timer.operationName.length())
            .max()
            .orElse(0);

        int nameColumnWidth = Math.min(
            MAX_OPERATION_NAME_LENGTH,
            Math.max(longestName, MIN_OPERATION_NAME_LENGTH)
        );

        // Determine duration column width so everything lines up nicely
        int maxDurationWidth = completed.stream()
            .map(timer -> formatDuration(timer.duration))
            .mapToInt(String::length)
            .max()
            .orElse(0);

        // Fixed width for percentage column such as "100.0%"
        int percentColumnWidth = 7;

        // name + " |" + bar + space + duration + space + percent
        int lineWidth = nameColumnWidth + 3 + BAR_WIDTH + 1 + maxDurationWidth + 1 + percentColumnWidth;

        System.out.println("\n⏱️  Timing Timeline:");
        System.out.println("─".repeat(lineWidth));
        System.out.println("(▓ = wrapper, █ = inner/leaf (% of total wall-clock time))\n");

        // Precompute which timers are wrappers: those that fully contain at least one other interval
        Map<TimerEntry, Boolean> isWrapper = new ConcurrentHashMap<>();
        for (TimerEntry outer : completed) {
            boolean wrapper = completed.stream()
                .filter(inner -> inner != outer)
                .anyMatch(inner ->
                    !outer.startTime.isAfter(inner.startTime) &&
                    !outer.getEndTime().isBefore(inner.getEndTime())
                );
            isWrapper.put(outer, wrapper);
        }

        // Sort by start time so the visual order roughly follows execution order
        completed.stream()
            .sorted((a, b) -> a.startTime.compareTo(b.startTime))
            .forEach(timer -> {
                long offsetMillis = Duration.between(earliestStart, timer.startTime).toMillis();
                long durationMillis = timer.duration.toMillis();

                int offsetChars = (int) Math.round((offsetMillis * 1.0 * BAR_WIDTH) / totalMillis);
                int lenChars = (int) Math.max(1, Math.round((durationMillis * 1.0 * BAR_WIDTH) / totalMillis));

                // Clamp to bar width
                if (offsetChars > BAR_WIDTH) {
                    offsetChars = BAR_WIDTH;
                }
                if (offsetChars + lenChars > BAR_WIDTH) {
                    lenChars = Math.max(0, BAR_WIDTH - offsetChars);
                }

        boolean wrapper = Boolean.TRUE.equals(isWrapper.get(timer));
        char blockChar = wrapper ? '▓' : '█';

        // Percentage for this timer relative to overall wall-clock period
        double pct = (totalMillis > 0)
            ? (timer.duration.toMillis() * 100.0) / totalMillis
            : 0.0;
        String pctStr = String.format("%5.1f%%", pct);

                // Build uncoloured bar first so width math ignores ANSI sequences
                String spaces = " ".repeat(Math.max(0, offsetChars));
                String rawBlock = lenChars > 0 ? ("" + blockChar).repeat(lenChars) : "";
                String rawBar = spaces + rawBlock;

                // Pad with spaces (still uncoloured) to full width so lines align
                if (rawBar.length() < BAR_WIDTH) {
                    rawBar = rawBar + " ".repeat(BAR_WIDTH - rawBar.length());
                }

                // Apply colour
                String RESET = "\u001B[0m";
                String colouredBar;

                if (wrapper && !rawBlock.isEmpty()) {
                    // Wrapper: "silver" / light grey
                    String SILVER = "\u001B[37m"; // white/grey on dark background
                    colouredBar = spaces + SILVER + rawBlock + RESET;
                    // Preserve trailing padding spaces (after the coloured block)
                    if (rawBar.length() > spaces.length() + rawBlock.length()) {
                        colouredBar += rawBar.substring(spaces.length() + rawBlock.length());
                    }
                } else if (!rawBlock.isEmpty()) {
                    // Leaf operations: colour by percentage of total time
                    String GREEN = "\u001B[32m";
                    String YELLOW = "\u001B[33m"; // amber-ish
                    String RED = "\u001B[31m";

                    String colour;
                    if (pct >= 70.0) {
                        colour = RED;
                    } else if (pct >= 50.0) {
                        colour = YELLOW;
                    } else {
                        colour = GREEN;
                    }

                    colouredBar = spaces + colour + rawBlock + RESET;
                    if (rawBar.length() > spaces.length() + rawBlock.length()) {
                        colouredBar += rawBar.substring(spaces.length() + rawBlock.length());
                    }
                } else {
                    colouredBar = rawBar;
                }

                String durationStr = formatDuration(timer.duration);

                System.out.printf(
                    "%-" + nameColumnWidth + "." + nameColumnWidth + "s |%s %" + maxDurationWidth + "s %" + percentColumnWidth + "s%n",
                    timer.operationName,
                    colouredBar,
                    durationStr,
                    pctStr
                );
            });

        System.out.println("─".repeat(lineWidth));
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
        Instant endTime;
        
        TimerEntry(String operationName, Instant startTime) {
            this.operationName = operationName;
            this.startTime = startTime;
        }

        Instant getEndTime() {
            if (endTime != null) {
                return endTime;
            }
            if (duration != null) {
                return startTime.plus(duration);
            }
            return startTime;
        }
    }
}
