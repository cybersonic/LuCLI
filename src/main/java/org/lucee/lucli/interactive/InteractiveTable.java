package org.lucee.lucli.interactive;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Interactive table component with keyboard navigation and row selection
 */
public class InteractiveTable<T> {
    
    private final Terminal terminal;
    private final List<T> data;
    private final List<Column<T>> columns;
    private final String title;
    private final Function<T, String> detailProvider;
    
    private int selectedRow = 0;
    private int scrollOffset = 0;
    private boolean running = true;
    
    public static class Column<T> {
        private final String header;
        private final Function<T, String> valueExtractor;
        private final int width;
        private final boolean rightAlign;
        
        public Column(String header, Function<T, String> valueExtractor, int width) {
            this(header, valueExtractor, width, false);
        }
        
        public Column(String header, Function<T, String> valueExtractor, int width, boolean rightAlign) {
            this.header = header;
            this.valueExtractor = valueExtractor;
            this.width = width;
            this.rightAlign = rightAlign;
        }
        
        public String getHeader() { return header; }
        public String getValue(T item) { return valueExtractor.apply(item); }
        public int getWidth() { return width; }
        public boolean isRightAlign() { return rightAlign; }
    }
    
    public InteractiveTable(String title, List<T> data, List<Column<T>> columns, Function<T, String> detailProvider) throws IOException {
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .build();
        this.title = title;
        this.data = new ArrayList<>(data);
        this.columns = new ArrayList<>(columns);
        this.detailProvider = detailProvider;
        
        // Debug terminal info
        System.out.println("Debug: Terminal type: " + terminal.getType());
        System.out.println("Debug: Terminal size: " + terminal.getWidth() + "x" + terminal.getHeight());
        System.out.println("Debug: Data size: " + data.size());
        
        // Enable raw mode for key handling
        terminal.enterRawMode();
    }
    
    public void run() {
        try {
            while (running) {
                render();
                handleInput();
            }
        } finally {
            cleanup();
        }
    }
    
    private void render() {
        // Clear screen and move cursor to top
        terminal.writer().print("\033[2J\033[H");
        terminal.flush();
        
        // Get terminal dimensions
        int terminalHeight = terminal.getHeight();
        int terminalWidth = terminal.getWidth();
        int maxVisibleRows = terminalHeight - 6; // Leave space for title, headers, and help
        
        // Render title
        renderTitle();
        
        // Render table header
        renderHeader();
        
        // Calculate visible data range
        adjustScrollOffset(maxVisibleRows);
        int endRow = Math.min(scrollOffset + maxVisibleRows, data.size());
        
        // Render data rows
        for (int i = scrollOffset; i < endRow; i++) {
            T item = data.get(i);
            boolean isSelected = (i == selectedRow);
            renderDataRow(item, i, isSelected);
        }
        
        // Fill remaining space
        for (int i = endRow - scrollOffset; i < maxVisibleRows; i++) {
            terminal.writer().println();
        }
        
        // Render help line
        renderHelp();
        
        terminal.flush();
    }
    
    private void renderTitle() {
        String titleLine = "┌─ " + title + " ─";
        int padding = Math.max(0, 80 - titleLine.length());
        titleLine += "─".repeat(padding) + "┐";
        
        terminal.writer().println("\033[1;36m" + titleLine + "\033[0m"); // Cyan bold
        terminal.writer().println();
    }
    
    private void renderHeader() {
        StringBuilder header = new StringBuilder();
        StringBuilder separator = new StringBuilder();
        
        for (int i = 0; i < columns.size(); i++) {
            Column<T> column = columns.get(i);
            String headerText = column.getHeader();
            
            if (i > 0) {
                header.append("│");
                separator.append("┼");
            }
            
            // Format header text
            String formatted = String.format(
                column.isRightAlign() ? "%" + column.getWidth() + "s" : "%-" + column.getWidth() + "s",
                headerText.length() > column.getWidth() ? headerText.substring(0, column.getWidth()) : headerText
            );
            header.append(" ").append(formatted).append(" ");
            separator.append("─".repeat(column.getWidth() + 2));
        }
        
        terminal.writer().println("\033[1;37m" + header.toString() + "\033[0m"); // White bold
        terminal.writer().println("\033[37m" + separator.toString() + "\033[0m"); // White
    }
    
    private void renderDataRow(T item, int rowIndex, boolean isSelected) {
        StringBuilder row = new StringBuilder();
        
        // Apply selection highlighting
        if (isSelected) {
            row.append("\033[30;47m"); // Black text on white background
        }
        
        for (int i = 0; i < columns.size(); i++) {
            Column<T> column = columns.get(i);
            String value = column.getValue(item);
            
            if (i > 0) {
                row.append("│");
            }
            
            // Format value
            String formatted = String.format(
                column.isRightAlign() ? "%" + column.getWidth() + "s" : "%-" + column.getWidth() + "s",
                value.length() > column.getWidth() ? value.substring(0, column.getWidth()) : value
            );
            row.append(" ").append(formatted).append(" ");
        }
        
        if (isSelected) {
            row.append("\033[0m"); // Reset color
        }
        
        terminal.writer().println(row.toString());
    }
    
    private void renderHelp() {
        terminal.writer().println();
        String help = "\033[90m↑↓: Navigate  │  ⏎: View Details  │  q: Quit  │  " + 
                     (selectedRow + 1) + "/" + data.size() + "\033[0m";
        terminal.writer().println(help);
    }
    
    private void handleInput() {
        try {
            int ch = terminal.reader().read();
            
            switch (ch) {
                case 'q':
                case 'Q':
                    running = false;
                    break;
                    
                case 27: // ESC - check for escape sequences
                    int next1 = terminal.reader().read();
                    if (next1 == 91) { // '[' - this is an escape sequence
                        int next2 = terminal.reader().read();
                        switch (next2) {
                            case 65: // Up arrow
                                if (selectedRow > 0) {
                                    selectedRow--;
                                }
                                break;
                            case 66: // Down arrow  
                                if (selectedRow < data.size() - 1) {
                                    selectedRow++;
                                }
                                break;
                        }
                    } else {
                        // Just ESC key - quit
                        running = false;
                    }
                    break;
                    
                case 'k': // vi-style up
                    if (selectedRow > 0) {
                        selectedRow--;
                    }
                    break;
                    
                case 'j': // vi-style down
                    if (selectedRow < data.size() - 1) {
                        selectedRow++;
                    }
                    break;
                    
                case 13: // Enter
                case 10: // Line feed
                    if (!data.isEmpty() && selectedRow < data.size()) {
                        showDetail(data.get(selectedRow));
                    }
                    break;
                    
                case 'r':
                case 'R':
                    // Refresh - just re-render
                    break;
            }
        } catch (IOException e) {
            // Handle input error
            running = false;
        }
    }
    
    private void adjustScrollOffset(int maxVisibleRows) {
        if (selectedRow < scrollOffset) {
            scrollOffset = selectedRow;
        } else if (selectedRow >= scrollOffset + maxVisibleRows) {
            scrollOffset = selectedRow - maxVisibleRows + 1;
        }
    }
    
    private void showDetail(T item) {
        if (detailProvider == null) {
            return;
        }
        
        // Clear screen
        terminal.writer().print("\033[2J\033[H");
        terminal.flush();
        
        // Show detail view
        String detailContent = detailProvider.apply(item);
        terminal.writer().println("\033[1;36m┌─ Details ─────────────────────────────────────────────────────────────────┐\033[0m");
        terminal.writer().println();
        
        // Split content into lines and display
        String[] lines = detailContent.split("\n");
        for (String line : lines) {
            terminal.writer().println(line);
        }
        
        terminal.writer().println();
        terminal.writer().println("\033[90mPress any key to return...\033[0m");
        terminal.flush();
        
        // Wait for key press
        try {
            terminal.reader().read();
        } catch (IOException e) {
            // Handle error
        }
    }
    
    private void cleanup() {
        try {
            // Clear screen and show cursor
            terminal.writer().print("\033[2J\033[H\033[?25h");
            terminal.flush();
            
            terminal.close();
        } catch (IOException e) {
            // Handle cleanup error
        }
    }
    
    /**
     * Builder class for creating interactive tables
     */
    public static class Builder<T> {
        private String title = "Interactive Table";
        private List<T> data = new ArrayList<>();
        private List<Column<T>> columns = new ArrayList<>();
        private Function<T, String> detailProvider;
        
        public Builder<T> title(String title) {
            this.title = title;
            return this;
        }
        
        public Builder<T> data(List<T> data) {
            this.data = new ArrayList<>(data);
            return this;
        }
        
        public Builder<T> addColumn(String header, Function<T, String> valueExtractor, int width) {
            columns.add(new Column<>(header, valueExtractor, width));
            return this;
        }
        
        public Builder<T> addColumn(String header, Function<T, String> valueExtractor, int width, boolean rightAlign) {
            columns.add(new Column<>(header, valueExtractor, width, rightAlign));
            return this;
        }
        
        public Builder<T> detailProvider(Function<T, String> detailProvider) {
            this.detailProvider = detailProvider;
            return this;
        }
        
        public InteractiveTable<T> build() throws IOException {
            return new InteractiveTable<>(title, data, columns, detailProvider);
        }
    }
}
