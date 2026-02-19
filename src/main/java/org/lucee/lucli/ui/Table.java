package org.lucee.lucli.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lucee.lucli.StringOutput;

/**
 * A simple, reusable table renderer for CLI output.
 * 
 * Supports title, headers, data rows, optional footer, and automatic column sizing.
 * Integrates with StringOutput for emoji/placeholder handling.
 * 
 * Usage:
 * <pre>
 * Table table = Table.builder()
 *     .title("LuCLI Modules")
 *     .headers("NAME", "STATUS", "VERSION")
 *     .row("my-module", "${EMOJI_SUCCESS}", "1.0.0")
 *     .row("other-module", "${EMOJI_WARNING}", "2.1.0")
 *     .footer("Total: 2 modules")
 *     .build();
 * table.print();
 * </pre>
 */
public class Table {
    
    private static final int DEFAULT_MIN_WIDTH = 80;
    private static final int COLUMN_PADDING = 2; // Space between columns
    
    private final List<String> titleRows;
    private final List<String> headers;
    private final List<List<String>> rows;
    private final String footer;
    private final int[] columnWidths;
    private final boolean[] rightAlign;
    private final BorderStyle borderStyle;
    private final int tableWidth;
    
    /**
     * Border styles for table rendering
     */
    public enum BorderStyle {
        /** No borders, just spacing */
        NONE("", " "),
        /** Simple ASCII borders */
        ASCII("=", "-"),
        /** Unicode box-drawing characters */
        BOX("=", "─");
        
        final String titleUnderline;
        final String separator;
        
        BorderStyle(String titleUnderline, String separator) {
            this.titleUnderline = titleUnderline;
            this.separator = separator;
        }
    }
    
    private Table(Builder builder) {
        this.titleRows = new ArrayList<>(builder.titleRows);
        this.headers = new ArrayList<>(builder.headers);
        this.rows = new ArrayList<>(builder.rows);
        this.footer = builder.footer;
        this.borderStyle = builder.borderStyle;
        this.rightAlign = builder.rightAlign;
        this.columnWidths = calculateColumnWidths(builder.minWidths);
        this.tableWidth = calculateTableWidth(builder.maxWidth);
    }
    
    /**
     * Calculate optimal column widths based on content
     */
    private int[] calculateColumnWidths(int[] minWidths) {
        int columnCount = headers.size();
        int[] widths = new int[columnCount];
        
        // Start with header widths + padding
        for (int i = 0; i < columnCount; i++) {
            widths[i] = getDisplayWidth(headers.get(i)) + COLUMN_PADDING;
        }
        
        // Check all rows for wider content
        for (List<String> row : rows) {
            for (int i = 0; i < Math.min(row.size(), columnCount); i++) {
                int cellWidth = getDisplayWidth(row.get(i)) + COLUMN_PADDING;
                if (cellWidth > widths[i]) {
                    widths[i] = cellWidth;
                }
            }
        }
        
        // Apply minimum widths
        if (minWidths != null) {
            for (int i = 0; i < Math.min(minWidths.length, columnCount); i++) {
                if (minWidths[i] > widths[i]) {
                    widths[i] = minWidths[i];
                }
            }
        }
        
        return widths;
    }
    
    /**
     * Calculate the total table width
     */
    private int calculateTableWidth(int maxWidth) {
        int contentWidth = 0;
        for (int w : columnWidths) {
            contentWidth += w;
        }
        
        // Use at least the content width, or maxWidth if specified, or DEFAULT_MIN_WIDTH
        int width = Math.max(contentWidth, DEFAULT_MIN_WIDTH);
        if (maxWidth > 0) {
            width = Math.max(contentWidth, maxWidth);
        }
        
        return width;
    }
    
    /**
     * Get display width of a string, accounting for emoji placeholders
     */
    private int getDisplayWidth(String text) {
        if (text == null) return 0;
        
        // Process through StringOutput to resolve placeholders
        String processed = StringOutput.getInstance().process(text);
        
        // Account for emoji width (most emojis display as 2 characters wide)
        int width = 0;
        for (int i = 0; i < processed.length(); ) {
            int codePoint = processed.codePointAt(i);
            if (isEmoji(codePoint)) {
                width += 2; // Emojis are typically 2 characters wide
            } else {
                width += 1;
            }
            i += Character.charCount(codePoint);
        }
        return width;
    }
    
    /**
     * Check if a code point is an emoji
     */
    private boolean isEmoji(int codePoint) {
        return (codePoint >= 0x1F300 && codePoint <= 0x1F9FF) || // Miscellaneous Symbols and Pictographs, Emoticons, etc.
               (codePoint >= 0x2600 && codePoint <= 0x26FF) ||   // Miscellaneous Symbols
               (codePoint >= 0x2700 && codePoint <= 0x27BF) ||   // Dingbats
               (codePoint >= 0x1F600 && codePoint <= 0x1F64F);   // Emoticons
    }
    
    /**
     * Print the table to stdout via StringOutput
     */
    public void print() {
        StringOutput out = StringOutput.getInstance();
        
        // Print title rows if present
        if (!titleRows.isEmpty()) {
            for (String titleRow : titleRows) {
                out.println(titleRow);
            }
            out.println(borderStyle.titleUnderline.repeat(tableWidth));
        }
        
        // Print header row
        out.println(formatRow(headers));
        
        // Print separator
        out.println(formatSeparator());
        
        // Print data rows
        for (List<String> row : rows) {
            out.println(formatRow(row));
        }
        
        // Print closing separator
        out.println(formatSeparator());
        
        // Print footer if present
        if (footer != null && !footer.isEmpty()) {
            out.println(footer);
        }
    }
    
    /**
     * Render the table to a string
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        
        // Title rows
        if (!titleRows.isEmpty()) {
            for (String titleRow : titleRows) {
                sb.append(titleRow).append("\n");
            }
            sb.append(borderStyle.titleUnderline.repeat(tableWidth)).append("\n");
        }
        
        // Header row
        sb.append(formatRow(headers)).append("\n");
        
        // Separator
        sb.append(formatSeparator()).append("\n");
        
        // Data rows
        for (List<String> row : rows) {
            sb.append(formatRow(row)).append("\n");
        }
        
        // Closing separator
        sb.append(formatSeparator()).append("\n");
        
        // Footer
        if (footer != null && !footer.isEmpty()) {
            sb.append(footer).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Format a single row
     */
    private String formatRow(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < columnWidths.length; i++) {
            String value = i < cells.size() ? cells.get(i) : "";
            if (value == null) value = "";
            
            // Calculate padding needed
            int displayWidth = getDisplayWidth(value);
            int padding = columnWidths[i] - displayWidth;
            
            // Apply alignment
            boolean alignRight = rightAlign != null && i < rightAlign.length && rightAlign[i];
            if (alignRight) {
                sb.append(" ".repeat(Math.max(0, padding)));
                sb.append(value);
            } else {
                sb.append(value);
                sb.append(" ".repeat(Math.max(0, padding)));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Format the separator line (full width)
     */
    private String formatSeparator() {
        if (borderStyle == BorderStyle.NONE) {
            return "";
        }
        return borderStyle.separator.repeat(tableWidth);
    }
    
    /**
     * Get the total width of the table
     */
    public int getTotalWidth() {
        int width = 0;
        for (int colWidth : columnWidths) {
            width += colWidth;
        }
        // Add separator widths
        width += (columnWidths.length - 1) * borderStyle.separator.length();
        return width;
    }
    
    /**
     * Create a new table builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for creating Table instances
     */
    public static class Builder {
        private List<String> titleRows = new ArrayList<>();
        private List<String> headers = new ArrayList<>();
        private List<List<String>> rows = new ArrayList<>();
        private String footer;
        private int[] minWidths;
        private boolean[] rightAlign;
        private BorderStyle borderStyle = BorderStyle.BOX;
        private int maxWidth = 0;
        
        /**
         * Add a title row (can be called multiple times for multiple rows)
         */
        public Builder title(String title) {
            this.titleRows.add(title);
            return this;
        }
        
        /**
         * Set the table headers
         */
        public Builder headers(String... headers) {
            this.headers = Arrays.asList(headers);
            return this;
        }
        
        /**
         * Set the table headers from a list
         */
        public Builder headers(List<String> headers) {
            this.headers = new ArrayList<>(headers);
            return this;
        }
        
        /**
         * Add a data row
         */
        public Builder row(String... values) {
            this.rows.add(Arrays.asList(values));
            return this;
        }
        
        /**
         * Add a data row from a list
         */
        public Builder row(List<String> values) {
            this.rows.add(new ArrayList<>(values));
            return this;
        }
        
        /**
         * Add multiple rows at once
         */
        public Builder rows(List<List<String>> rows) {
            for (List<String> row : rows) {
                this.rows.add(new ArrayList<>(row));
            }
            return this;
        }
        
        /**
         * Set an optional footer
         */
        public Builder footer(String footer) {
            this.footer = footer;
            return this;
        }
        
        /**
         * Set minimum column widths
         */
        public Builder minWidths(int... widths) {
            this.minWidths = widths;
            return this;
        }
        
        /**
         * Set column alignment (true = right-align)
         */
        public Builder rightAlign(boolean... align) {
            this.rightAlign = align;
            return this;
        }
        
        /**
         * Set the border style
         */
        public Builder borderStyle(BorderStyle style) {
            this.borderStyle = style;
            return this;
        }
        
        /**
         * Set maximum table width (lines will be at least this wide)
         */
        public Builder maxWidth(int width) {
            this.maxWidth = width;
            return this;
        }
        
        /**
         * Build the table
         */
        public Table build() {
            if (headers.isEmpty()) {
                throw new IllegalStateException("Table must have at least one header");
            }
            return new Table(this);
        }
    }
}
