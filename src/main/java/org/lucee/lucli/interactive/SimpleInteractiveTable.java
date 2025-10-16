package org.lucee.lucli.interactive;

import java.util.List;
import java.util.function.Function;

/**
 * Simple interactive table that works reliably without complex terminal handling
 */
public class SimpleInteractiveTable<T> {
    
    private final String title;
    private final List<T> data;
    private final List<Column<T>> columns;
    private final Function<T, String> detailProvider;
    private int currentIndex = 0;
    
    public static class Column<T> {
        final String header;
        final Function<T, String> valueExtractor;
        final int width;
        
        public Column(String header, Function<T, String> valueExtractor, int width) {
            this.header = header;
            this.valueExtractor = valueExtractor;
            this.width = width;
        }
    }
    
    public SimpleInteractiveTable(String title, List<T> data, List<Column<T>> columns, Function<T, String> detailProvider) {
        this.title = title;
        this.data = data;
        this.columns = columns;
        this.detailProvider = detailProvider;
    }
    
    public void run() {
        boolean running = true;
        
        // Enable raw mode for single keypress input
        enableRawMode();
        
        try {
            while (running) {
                clearScreen();
                displayTable();
                System.out.println("\nPress: [j/↓]down [k/↑]up [Enter]details [q]quit");
                
                int key = readSingleKey();
                
                switch (key) {
                    case 'j':
                    case 'J':
                        if (currentIndex < data.size() - 1) {
                            currentIndex++;
                        }
                        break;
                    case 'k':
                    case 'K':
                        if (currentIndex > 0) {
                            currentIndex--;
                        }
                        break;
                    case 13: // Enter
                    case 10: // Line feed
                        if (!data.isEmpty() && currentIndex < data.size()) {
                            showDetails(data.get(currentIndex));
                        }
                        break;
                    case 'q':
                    case 'Q':
                    case 27: // ESC
                        running = false;
                        break;
                }
            }
        } finally {
            disableRawMode();
            clearScreen();
            System.out.println("👋 Goodbye!");
        }
    }
    
    private void displayTable() {
        // Title
        System.out.println("┌─ " + title + " " + "─".repeat(Math.max(0, 60 - title.length())) + "┐");
        System.out.println();
        
        // Headers
        StringBuilder headerLine = new StringBuilder();
        StringBuilder separatorLine = new StringBuilder();
        
        for (int i = 0; i < columns.size(); i++) {
            Column<T> col = columns.get(i);
            if (i > 0) {
                headerLine.append(" │ ");
                separatorLine.append("─┼─");
            }
            String header = truncate(col.header, col.width);
            headerLine.append(String.format("%-" + col.width + "s", header));
            separatorLine.append("─".repeat(col.width));
        }
        
        System.out.println(headerLine.toString());
        System.out.println(separatorLine.toString());
        
        // Data rows
        for (int i = 0; i < data.size(); i++) {
            T item = data.get(i);
            StringBuilder row = new StringBuilder();
            
            // Highlight selected row
            String prefix = (i == currentIndex) ? "► " : "  ";
            row.append(prefix);
            
            for (int j = 0; j < columns.size(); j++) {
                Column<T> col = columns.get(j);
                if (j > 0) {
                    row.append(" │ ");
                }
                String value = truncate(col.valueExtractor.apply(item), col.width);
                row.append(String.format("%-" + col.width + "s", value));
            }
            
            System.out.println(row.toString());
        }
        
        System.out.println();
        System.out.println("Showing " + (currentIndex + 1) + "/" + data.size() + " items");
    }
    
    private void showDetails(T item) {
        clearScreen();
        System.out.println("┌─ Details " + "─".repeat(65) + "┐");
        System.out.println();
        
        if (detailProvider != null) {
            String details = detailProvider.apply(item);
            // Fix newline rendering by replacing \n with actual newlines
            String formattedDetails = details.replace("\\n", "\n");
            System.out.println(formattedDetails);
        } else {
            System.out.println("No details available for this item.");
        }
        
        System.out.println();
        System.out.println("Press any key to return...");
        readSingleKey();
    }
    
    private String truncate(String str, int width) {
        if (str == null) return "";
        return str.length() <= width ? str : str.substring(0, width - 3) + "...";
    }
    
    private void clearScreen() {
        System.out.print("\033[2J\033[H");
        System.out.flush();
    }
    
    private void enableRawMode() {
        try {
            // Disable line buffering and echo for immediate key response
            String[] cmd = {"/bin/sh", "-c", "stty raw -echo < /dev/tty"};
            Runtime.getRuntime().exec(cmd).waitFor();
        } catch (Exception e) {
            // Fallback - will still work but require Enter
        }
    }
    
    private void disableRawMode() {
        try {
            // Restore normal terminal settings
            String[] cmd = {"/bin/sh", "-c", "stty cooked echo < /dev/tty"};
            Runtime.getRuntime().exec(cmd).waitFor();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    private int readSingleKey() {
        try {
            int ch = System.in.read();
            
            // Handle escape sequences for arrow keys
            if (ch == 27) { // ESC
                int next1 = System.in.read();
                if (next1 == 91) { // '[' - this is an escape sequence
                    int next2 = System.in.read();
                    switch (next2) {
                        case 65: // Up arrow
                            return 'k'; // Treat as k (up)
                        case 66: // Down arrow
                            return 'j'; // Treat as j (down)
                        default:
                            return 27; // Return ESC for other sequences
                    }
                } else {
                    return 27; // Just ESC key
                }
            }
            
            return ch;
        } catch (Exception e) {
            return 'q'; // Exit on error
        }
    }
    
    public static class Builder<T> {
        private String title = "Interactive Table";
        private List<T> data;
        private List<Column<T>> columns = new java.util.ArrayList<>();
        private Function<T, String> detailProvider;
        
        public Builder<T> title(String title) {
            this.title = title;
            return this;
        }
        
        public Builder<T> data(List<T> data) {
            this.data = data;
            return this;
        }
        
        public Builder<T> addColumn(String header, Function<T, String> valueExtractor, int width) {
            columns.add(new Column<>(header, valueExtractor, width));
            return this;
        }
        
        public Builder<T> detailProvider(Function<T, String> detailProvider) {
            this.detailProvider = detailProvider;
            return this;
        }
        
        public SimpleInteractiveTable<T> build() {
            return new SimpleInteractiveTable<>(title, data, columns, detailProvider);
        }
    }
}
