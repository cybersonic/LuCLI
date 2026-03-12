package org.lucee.lucli.ui;

/**
 * Simple terminal progress bar for byte-based operations.
 */
public class ProgressBar {

    private static final int DEFAULT_WIDTH = 50;

    private final String label;
    private final long totalBytes;
    private final int width;

    private long currentBytes;
    private long lastRenderMillis;
    private boolean completed;

    public ProgressBar(String label, long totalBytes) {
        this(label, totalBytes, DEFAULT_WIDTH);
    }

    public ProgressBar(String label, long totalBytes, int width) {
        this.label = (label == null || label.isBlank()) ? "Progress" : label;
        this.totalBytes = totalBytes;
        this.width = Math.max(10, width);
        this.currentBytes = 0L;
        this.lastRenderMillis = 0L;
        this.completed = false;
        render(true);
    }

    public synchronized void update(long bytesCompleted) {
        if (completed) {
            return;
        }
        this.currentBytes = Math.max(0L, bytesCompleted);
        render(false);
    }

    public synchronized void complete(String completionText) {
        if (completed) {
            return;
        }
        if (totalBytes > 0) {
            this.currentBytes = Math.max(currentBytes, totalBytes);
        }
        completed = true;
        String suffix = (completionText == null || completionText.isBlank()) ? "" : " - " + completionText;

        if (totalBytes > 0) {
            String bar = "█".repeat(width);
            System.out.println(
                "\r" + label + " [" + bar + "] 100.0% (" + formatBytes(currentBytes) + " / " + formatBytes(totalBytes) + ")" + suffix
            );
        } else {
            System.out.println("\r" + label + " " + formatBytes(currentBytes) + suffix);
        }
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = new String[] { "KB", "MB", "GB", "TB", "PB" };
        int unitIndex = -1;
        while (value >= 1024.0d && unitIndex < units.length - 1) {
            value /= 1024.0d;
            unitIndex++;
        }
        return String.format("%.1f %s", value, units[unitIndex]);
    }

    private void render(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (now - lastRenderMillis) < 100L) {
            return;
        }
        lastRenderMillis = now;

        if (totalBytes > 0) {
            long safeCurrent = Math.min(currentBytes, totalBytes);
            double progress = (double) safeCurrent / (double) totalBytes;
            int filled = (int) Math.floor(progress * width);
            String bar = "█".repeat(Math.max(0, filled)) + " ".repeat(Math.max(0, width - filled));
            String percent = String.format("%.1f", progress * 100.0d);
            System.out.print(
                "\r" + label + " [" + bar + "] " + percent + "% (" + formatBytes(safeCurrent) + " / " + formatBytes(totalBytes) + ")"
            );
        } else {
            System.out.print("\r" + label + " " + formatBytes(currentBytes));
        }
        System.out.flush();
    }
}
