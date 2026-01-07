package org.lucee.lucli.config.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerConfig.ServerConfig;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JLine-based, curses-style TUI editor for lucee.json.
 *
 * First pass: focuses on the "General" tab fields only (name, host, version,
 * port, webroot, enableLucee, enableREST) and provides a simple full-screen
 * editing experience.
 */
public class ConfigEditorTui {

    private enum FieldType {
        STRING,
        INT,
        BOOL
    }

    private static class Field {
        final String id;
        final String label;
        final FieldType type;
        final Supplier<String> getter;
        final Consumer<String> setter;

        Field(String id, String label, FieldType type,
              Supplier<String> getter,
              Consumer<String> setter) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.getter = getter;
            this.setter = setter;
        }
    }

    private final List<Field> fields = new ArrayList<>();
    private int selectedIndex = 0;
    private boolean changed = false;

    /**
     * Run the TUI editor for the General tab.
     *
     * @return true if any changes were saved.
     */
    public boolean run(Path projectDir,
                       Path configFile,
                       ServerConfig config,
                       JsonNode schema,
                       String environment) throws IOException {

        // Prepare fields for the General tab
        initGeneralFields(config);

        Terminal terminal = TerminalBuilder.builder()
            .system(true)
            .build();

        try {
            terminal.enterRawMode();

            boolean running = true;
            while (running) {
                render(terminal, projectDir, configFile, environment);
                int ch = terminal.reader().read();

                switch (ch) {
                    case 'q':
                    case 'Q':
                        running = !confirmQuit(terminal);
                        break;
                    case 's':
                    case 'S':
                        if (confirmSave(terminal, configFile)) {
                            LuceeServerConfig.saveConfig(config, configFile);
                            changed = false;
                            showMessage(terminal, "✔ Saved to " + configFile.toAbsolutePath() + " (press any key)");
                            terminal.reader().read();
                        }
                        break;
                    case 13: // Enter
                    case 10: // Line feed
                        editCurrentField(terminal);
                        break;
                    case 27: // ESC sequence for arrows
                        handleEscapeSequence(terminal, ch);
                        break;
                    case 'k': // vi-style up
                        if (selectedIndex > 0) {
                            selectedIndex--;
                        }
                        break;
                    case 'j': // vi-style down
                        if (selectedIndex < fields.size() - 1) {
                            selectedIndex++;
                        }
                        break;
                    default:
                        // Ignore other keys
                        break;
                }
            }
        } finally {
            // Clear screen and restore cursor
            terminal.writer().print("\033[2J\033[H\033[?25h");
            terminal.flush();
            terminal.close();
        }

        return changed;
    }

    private void initGeneralFields(ServerConfig config) {
        fields.clear();
        fields.add(new Field("name", "Name", FieldType.STRING,
                () -> nullToEmpty(config.name),
                v -> config.name = v));
        fields.add(new Field("host", "Host", FieldType.STRING,
                () -> nullToEmpty(config.host),
                v -> config.host = v));
        fields.add(new Field("version", "Lucee Version", FieldType.STRING,
                () -> nullToEmpty(config.version),
                v -> config.version = v));
        fields.add(new Field("port", "HTTP Port", FieldType.INT,
                () -> String.valueOf(config.port),
                v -> {
                    try {
                        int port = Integer.parseInt(v);
                        if (port < 1 || port > 65535) {
                            // Ignore invalid, caller will show warning
                            return;
                        }
                        config.port = port;
                    } catch (NumberFormatException ignored) {
                        // Caller will show warning
                    }
                }));
        fields.add(new Field("webroot", "Webroot", FieldType.STRING,
                () -> nullToEmpty(config.webroot),
                v -> config.webroot = v));
        fields.add(new Field("enableLucee", "Enable Lucee", FieldType.BOOL,
                () -> String.valueOf(config.enableLucee),
                v -> {
                    Boolean b = parseBoolean(v);
                    if (b != null) {
                        config.enableLucee = b;
                    }
                }));
        fields.add(new Field("enableREST", "Enable REST", FieldType.BOOL,
                () -> String.valueOf(config.enableREST),
                v -> {
                    Boolean b = parseBoolean(v);
                    if (b != null) {
                        config.enableREST = b;
                    }
                }));
    }

    private void render(Terminal terminal,
                        Path projectDir,
                        Path configFile,
                        String environment) {
        // Clear screen
        terminal.writer().print("\033[2J\033[H");

        int width = terminal.getWidth();
        if (width <= 0) {
            width = 80;
        }

        // Title bar
        String title = " Lucee Config Editor ";
        String line = "┌" + padCenter(title, Math.max(0, width - 2), '─') + "┐";
        terminal.writer().println("\033[1;36m" + line + "\033[0m");

        // Project/config info
        String proj = "Project: " + projectDir.toAbsolutePath();
        String cfg = "Config : " + configFile.toAbsolutePath();
        terminal.writer().println(truncate(proj, width));
        terminal.writer().println(truncate(cfg, width));
        if (environment != null && !environment.trim().isEmpty()) {
            String env = "Env    : " + environment.trim();
            terminal.writer().println(truncate(env, width));
        } else {
            terminal.writer().println();
        }

        // Tab bar (General active, others placeholder)
        String tabs = "[General]  [JVM]  [HTTPS]  [Admin]  [URL Rewrite]";
        terminal.writer().println("\033[1;37m" + truncate(tabs, width) + "\033[0m");
        terminal.writer().println("\033[37m" + "─".repeat(Math.max(0, width)) + "\033[0m");

        // Fields
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            boolean selected = (i == selectedIndex);
            String label = String.format("%-14s", f.label + ":");
            String value = f.getter.get();
            String row = "  " + label + " " + value;

            if (selected) {
                terminal.writer().print("\033[30;47m"); // black on white
            }
            terminal.writer().print(truncate(row, width));
            if (selected) {
                terminal.writer().print("\033[0m");
            }
            terminal.writer().println();
        }

        // Blank line before help
        terminal.writer().println();

        // Help / status
        String help = "\033[90m↑/k: Up  ↓/j: Down  Enter: Edit  s: Save  q: Quit  (General tab only for now)\033[0m";
        terminal.writer().println(truncate(help, width));

        terminal.flush();
    }

    private void editCurrentField(Terminal terminal) throws IOException {
        if (selectedIndex < 0 || selectedIndex >= fields.size()) {
            return;
        }
        Field f = fields.get(selectedIndex);
        String current = f.getter.get();

        // Move cursor to line of current field and to value column is complex; instead,
        // show a simple one-line prompt at the bottom of the screen.
        String prompt = "New value for " + f.label + " [" + current + "]: ";
        String input = readLine(terminal, prompt);
        if (input == null) {
            return; // Cancel
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return; // No change
        }

        // Validate based on type
        switch (f.type) {
            case INT:
                try {
                    int port = Integer.parseInt(trimmed);
                    if (port < 1 || port > 65535) {
                        showMessage(terminal, "⚠ Port must be between 1 and 65535 (press any key)");
                        terminal.reader().read();
                        return;
                    }
                } catch (NumberFormatException e) {
                    showMessage(terminal, "⚠ Invalid number (press any key)");
                    terminal.reader().read();
                    return;
                }
                break;
            case BOOL:
                if (parseBoolean(trimmed) == null) {
                    showMessage(terminal, "⚠ Expected true/false/yes/no (press any key)");
                    terminal.reader().read();
                    return;
                }
                break;
            case STRING:
            default:
                // No extra validation
                break;
        }

        f.setter.accept(trimmed);
        changed = true;
    }

    private void handleEscapeSequence(Terminal terminal, int firstChar) throws IOException {
        // We already read ESC (27) as firstChar
        int next1 = terminal.reader().read();
        if (next1 != '[') {
            return; // Just ESC, ignore here (quit is mapped to 'q')
        }
        int next2 = terminal.reader().read();
        switch (next2) {
            case 'A': // Up arrow
                if (selectedIndex > 0) {
                    selectedIndex--;
                }
                break;
            case 'B': // Down arrow
                if (selectedIndex < fields.size() - 1) {
                    selectedIndex++;
                }
                break;
            default:
                break;
        }
    }

    private boolean confirmQuit(Terminal terminal) throws IOException {
        if (!changed) {
            return true; // Nothing to lose
        }
        String answer = readLine(terminal, "Unsaved changes, quit anyway? [y/N]: ");
        if (answer == null) {
            return false;
        }
        String v = answer.trim().toLowerCase();
        return v.equals("y") || v.equals("yes");
    }

    private boolean confirmSave(Terminal terminal, Path configFile) throws IOException {
        String answer = readLine(terminal, "Save changes to " + configFile.getFileName() + "? [y/N]: ");
        if (answer == null) {
            return false;
        }
        String v = answer.trim().toLowerCase();
        return v.equals("y") || v.equals("yes");
    }

    private String readLine(Terminal terminal, String prompt) throws IOException {
        int width = terminal.getWidth();
        if (width <= 0) {
            width = 80;
        }
        int height = terminal.getHeight();
        if (height <= 0) {
            height = 24;
        }

        // Move to bottom line, clear it and print prompt
        terminal.writer().print("\033[" + height + ";1H"); // move cursor to last row
        terminal.writer().print("\033[2K");                 // clear line
        terminal.writer().print(truncate(prompt, width));
        terminal.writer().flush();

        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = terminal.reader().read();
            if (ch == -1) {
                return null;
            }
            if (ch == 13 || ch == 10) { // Enter
                break;
            }
            if (ch == 27) { // ESC cancels
                return null;
            }
            if (ch == 127 || ch == 8) { // Backspace
                if (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                    terminal.writer().print("\b \\b");
                    terminal.writer().flush();
                }
                continue;
            }
            sb.append((char) ch);
            terminal.writer().print((char) ch);
            terminal.writer().flush();
        }

        return sb.toString();
    }

    private void showMessage(Terminal terminal, String message) {
        int width = terminal.getWidth();
        if (width <= 0) {
            width = 80;
        }
        int height = terminal.getHeight();
        if (height <= 0) {
            height = 24;
        }
        terminal.writer().print("\033[" + height + ";1H");
        terminal.writer().print("\033[2K");
        terminal.writer().print(truncate(message, width));
        terminal.writer().flush();
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String padCenter(String text, int width, char padChar) {
        if (width <= 0) {
            return "";
        }
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        int totalPadding = width - text.length();
        int left = totalPadding / 2;
        int right = totalPadding - left;
        String pad = String.valueOf(padChar);
        return pad.repeat(left) + text + pad.repeat(right);
    }

    private static String truncate(String s, int width) {
        if (s == null) {
            return "";
        }
        if (s.length() <= width) {
            return s;
        }
        return s.substring(0, width);
    }

    private static Boolean parseBoolean(String input) {
        String v = input.toLowerCase();
        if (v.equals("true") || v.equals("t") || v.equals("yes") || v.equals("y")) {
            return Boolean.TRUE;
        }
        if (v.equals("false") || v.equals("f") || v.equals("no") || v.equals("n")) {
            return Boolean.FALSE;
        }
        return null;
    }
}
