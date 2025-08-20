package org.lucee.lucli;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

/**
 * Processes file system commands for the terminal
 */
public class CommandProcessor {
    private final FileSystemState fileSystemState;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd HH:mm");
    private final Settings settings;
    private final PromptConfig promptConfig;
    
    public CommandProcessor() {
        this.fileSystemState = new FileSystemState();
        this.settings = new Settings();
        this.promptConfig = new PromptConfig(settings);
    }
    
    public FileSystemState getFileSystemState() {
        return fileSystemState;
    }
    
    public PromptConfig getPromptConfig() {
        return promptConfig;
    }
    
    /**
     * Get the settings instance
     */
    public Settings getSettings() {
        return settings;
    }
    
    /**
     * Execute a command and return the result
     */
    public String executeCommand(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return "";
        }
        
        String[] parts = parseCommand(commandLine.trim());
        if (parts.length == 0) {
            return "";
        }
        
        String command = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        
        try {
            switch (command) {
                case "ls":
                case "dir":
                    return listFiles(args);
                case "cd":
                    return changeDirectory(args);
                case "pwd":
                    return printWorkingDirectory();
                case "mkdir":
                    return makeDirectory(args);
                case "rmdir":
                    return removeDirectory(args);
                case "rm":
                    return removeFile(args);
                case "cp":
                    return copyFile(args);
                case "mv":
                    return moveFile(args);
                case "cat":
                    return displayFile(args);
                case "touch":
                    return touchFile(args);
                case "find":
                    return findFiles(args);
                case "wc":
                    return wordCount(args);
                case "head":
                    return headFile(args);
                case "tail":
                    return tailFile(args);
                case "prompt":
                    return promptCommand(args);
                case "run":
                    return runScriptCommand(args);
                default:
                    return "❌ Unknown command: " + command + "\n💡 Type 'help' for available commands.";
            }
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
    
    /**
     * Parse command line into array of arguments, handling quotes
     */
    public String[] parseCommand(String commandLine) {
        List<String> parts = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentPart = new StringBuilder();
        
        for (char c : commandLine.toCharArray()) {
            if (c == '"' || c == '\'') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString());
                    currentPart = new StringBuilder();
                }
            } else {
                currentPart.append(c);
            }
        }
        
        if (currentPart.length() > 0) {
            parts.add(currentPart.toString());
        }
        
        return parts.toArray(new String[0]);
    }
    
    private String listFiles(String[] args) throws IOException {
        boolean longFormat = false;
        boolean showHidden = false;
        boolean humanReadable = false;
        List<String> paths = new ArrayList<>();
        
        // Parse arguments
        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (arg.contains("l")) longFormat = true;
                if (arg.contains("a")) showHidden = true;
                if (arg.contains("h")) humanReadable = true;
            } else {
                paths.add(arg);
            }
        }
        
        if (paths.isEmpty()) {
            paths.add(".");
        }
        
        StringBuilder result = new StringBuilder();
        
        for (String pathStr : paths) {
            Path path = fileSystemState.resolvePath(pathStr);
            
            if (!Files.exists(path)) {
                result.append("ls: ").append(pathStr).append(": No such file or directory\n");
                continue;
            }
            
            if (Files.isDirectory(path)) {
                result.append(listDirectory(path, longFormat, showHidden, humanReadable));
            } else {
                result.append(listSingleFile(path, longFormat, humanReadable));
            }
        }
        
        return result.toString().trim();
    }
    
    private String listDirectory(Path directory, boolean longFormat, boolean showHidden, boolean humanReadable) throws IOException {
        StringBuilder result = new StringBuilder();
        
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> sortedFiles = files
                    .filter(p -> showHidden || !p.getFileName().toString().startsWith("."))
                    .sorted((a, b) -> {
                        boolean aIsDir = Files.isDirectory(a);
                        boolean bIsDir = Files.isDirectory(b);
                        if (aIsDir && !bIsDir) return -1;
                        if (!aIsDir && bIsDir) return 1;
                        return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                    })
                    .toList();
            
            if (longFormat) {
                for (Path file : sortedFiles) {
                    result.append(formatLongListing(file, humanReadable)).append("\n");
                }
            } else {
                int maxColumns = 80; // Could be made configurable
                int columnWidth = 0;
                
                // Calculate column width
                for (Path file : sortedFiles) {
                    columnWidth = Math.max(columnWidth, file.getFileName().toString().length());
                }
                columnWidth += 2; // Add some padding
                
                int columnsPerLine = Math.max(1, maxColumns / columnWidth);
                
                for (int i = 0; i < sortedFiles.size(); i++) {
                    Path file = sortedFiles.get(i);
                    String name = file.getFileName().toString();
                    String emoji = getFileEmoji(file);
                    
                    if (Files.isDirectory(file)) {
                        name += "/";
                    }
                    
                    String displayName = emoji + name;
                    result.append(String.format("%-" + (columnWidth + 2) + "s", displayName)); // +2 for emoji
                    
                    if ((i + 1) % columnsPerLine == 0 || i == sortedFiles.size() - 1) {
                        result.append("\n");
                    }
                }
            }
        }
        
        return result.toString();
    }
    
    private String listSingleFile(Path file, boolean longFormat, boolean humanReadable) throws IOException {
        if (longFormat) {
            return formatLongListing(file, humanReadable);
        } else {
            String emoji = getFileEmoji(file);
            return emoji + file.getFileName().toString();
        }
    }
    
    private String formatLongListing(Path file, boolean humanReadable) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        StringBuilder result = new StringBuilder();
        
        // File type and permissions
        if (Files.isDirectory(file)) {
            result.append("d");
        } else if (Files.isSymbolicLink(file)) {
            result.append("l");
        } else {
            result.append("-");
        }
        
        // Try to get POSIX permissions, fall back to basic readable/writable/executable
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            result.append(PosixFilePermissions.toString(permissions));
        } catch (UnsupportedOperationException e) {
            // Fall back to basic permissions
            result.append(Files.isReadable(file) ? "r" : "-");
            result.append(Files.isWritable(file) ? "w" : "-");
            result.append(Files.isExecutable(file) ? "x" : "-");
            result.append("------"); // Fill the remaining positions
        }
        
        // Links (just show 1 for simplicity)
        result.append(String.format("%4d ", 1));
        
        // Owner and group (simplified)
        try {
            String owner = Files.getOwner(file).getName();
            result.append(String.format("%-8s %-8s ", owner, owner));
        } catch (Exception e) {
            result.append("user     user     ");
        }
        
        // Size
        long size = attrs.size();
        if (humanReadable && !Files.isDirectory(file)) {
            result.append(String.format("%8s ", formatSize(size)));
        } else {
            result.append(String.format("%8d ", size));
        }
        
        // Date
        Date modTime = new Date(attrs.lastModifiedTime().toMillis());
        result.append(dateFormat.format(modTime)).append(" ");
        
        // Name with emoji
        String name = file.getFileName().toString();
        String emoji = getFileEmoji(file);
        if (Files.isDirectory(file)) {
            name += "/";
        }
        result.append(emoji).append(name);
        
        return result.toString();
    }
    
    private String formatSize(long size) {
        String[] units = {"B", "K", "M", "G", "T"};
        int unitIndex = 0;
        double sizeDouble = size;
        
        while (sizeDouble >= 1024 && unitIndex < units.length - 1) {
            sizeDouble /= 1024.0;
            unitIndex++;
        }
        
        if (unitIndex == 0) {
            return String.format("%.0f%s", sizeDouble, units[unitIndex]);
        } else {
            return String.format("%.1f%s", sizeDouble, units[unitIndex]);
        }
    }
    
    private String changeDirectory(String[] args) {
        String targetPath = args.length > 0 ? args[0] : null;
        
        if (fileSystemState.changeDirectory(targetPath)) {
            return ""; // Success, no output
        } else {
            if (targetPath == null) {
                return "cd: HOME not set";
            } else {
                return "cd: " + targetPath + ": No such file or directory";
            }
        }
    }
    
    private String printWorkingDirectory() {
        return fileSystemState.getCurrentWorkingDirectory().toString();
    }
    
    private String makeDirectory(String[] args) {
        if (args.length == 0) {
            return "mkdir: missing operand";
        }
        
        boolean makeParents = false;
        List<String> directories = new ArrayList<>();
        
        for (String arg : args) {
            if (arg.equals("-p")) {
                makeParents = true;
            } else {
                directories.add(arg);
            }
        }
        
        StringBuilder result = new StringBuilder();
        
        for (String dir : directories) {
            Path path = fileSystemState.resolvePath(dir);
            try {
                if (makeParents) {
                    Files.createDirectories(path);
                } else {
                    Files.createDirectory(path);
                }
            } catch (IOException e) {
                result.append("mkdir: cannot create directory '").append(dir).append("': ")
                       .append(e.getMessage()).append("\n");
            }
        }
        
        return result.toString().trim();
    }
    
    private String removeDirectory(String[] args) {
        if (args.length == 0) {
            return "rmdir: missing operand";
        }
        
        StringBuilder result = new StringBuilder();
        
        for (String dir : args) {
            Path path = fileSystemState.resolvePath(dir);
            
            if (!Files.exists(path)) {
                result.append("rmdir: failed to remove '").append(dir).append("': No such file or directory\n");
                continue;
            }
            
            if (!Files.isDirectory(path)) {
                result.append("rmdir: failed to remove '").append(dir).append("': Not a directory\n");
                continue;
            }
            
            try {
                Files.delete(path);
            } catch (DirectoryNotEmptyException e) {
                result.append("rmdir: failed to remove '").append(dir).append("': Directory not empty\n");
            } catch (IOException e) {
                result.append("rmdir: failed to remove '").append(dir).append("': ").append(e.getMessage()).append("\n");
            }
        }
        
        return result.toString().trim();
    }
    
    private String removeFile(String[] args) {
        if (args.length == 0) {
            return "rm: missing operand";
        }
        
        boolean recursive = false;
        boolean force = false;
        List<String> files = new ArrayList<>();
        
        for (String arg : args) {
            if (arg.equals("-r") || arg.equals("-R")) {
                recursive = true;
            } else if (arg.equals("-f")) {
                force = true;
            } else if (arg.startsWith("-")) {
                if (arg.contains("r") || arg.contains("R")) recursive = true;
                if (arg.contains("f")) force = true;
            } else {
                files.add(arg);
            }
        }
        
        StringBuilder result = new StringBuilder();
        
        for (String file : files) {
            Path path = fileSystemState.resolvePath(file);
            
            if (!Files.exists(path) && !force) {
                result.append("rm: cannot remove '").append(file).append("': No such file or directory\n");
                continue;
            }
            
            try {
                if (Files.isDirectory(path) && recursive) {
                    deleteRecursively(path);
                } else {
                    Files.delete(path);
                }
            } catch (IOException e) {
                if (!force) {
                    result.append("rm: cannot remove '").append(file).append("': ").append(e.getMessage()).append("\n");
                }
            }
        }
        
        return result.toString().trim();
    }
    
    private void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    private String copyFile(String[] args) {
        if (args.length < 2) {
            return "cp: missing destination file operand";
        }
        
        String source = args[0];
        String destination = args[1];
        
        Path sourcePath = fileSystemState.resolvePath(source);
        Path destPath = fileSystemState.resolvePath(destination);
        
        if (!Files.exists(sourcePath)) {
            return "cp: cannot stat '" + source + "': No such file or directory";
        }
        
        try {
            if (Files.isDirectory(destPath)) {
                destPath = destPath.resolve(sourcePath.getFileName());
            }
            
            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
            return "";
        } catch (IOException e) {
            return "cp: cannot copy '" + source + "' to '" + destination + "': " + e.getMessage();
        }
    }
    
    private String moveFile(String[] args) {
        if (args.length < 2) {
            return "mv: missing destination file operand";
        }
        
        String source = args[0];
        String destination = args[1];
        
        Path sourcePath = fileSystemState.resolvePath(source);
        Path destPath = fileSystemState.resolvePath(destination);
        
        if (!Files.exists(sourcePath)) {
            return "mv: cannot stat '" + source + "': No such file or directory";
        }
        
        try {
            if (Files.isDirectory(destPath)) {
                destPath = destPath.resolve(sourcePath.getFileName());
            }
            
            Files.move(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
            return "";
        } catch (IOException e) {
            return "mv: cannot move '" + source + "' to '" + destination + "': " + e.getMessage();
        }
    }
    
    private String displayFile(String[] args) {
        if (args.length == 0) {
            return "cat: missing file operand";
        }
        
        StringBuilder result = new StringBuilder();
        
        for (String file : args) {
            Path path = fileSystemState.resolvePath(file);
            
            if (!Files.exists(path)) {
                result.append("cat: ").append(file).append(": No such file or directory\n");
                continue;
            }
            
            if (Files.isDirectory(path)) {
                result.append("cat: ").append(file).append(": Is a directory\n");
                continue;
            }
            
            try {
                String content = Files.readString(path);
                result.append(content);
                if (!content.endsWith("\n")) {
                    result.append("\n");
                }
            } catch (IOException e) {
                result.append("cat: ").append(file).append(": ").append(e.getMessage()).append("\n");
            }
        }
        
        return result.toString().trim();
    }
    
    private String touchFile(String[] args) {
        if (args.length == 0) {
            return "touch: missing file operand";
        }
        
        StringBuilder result = new StringBuilder();
        
        for (String file : args) {
            Path path = fileSystemState.resolvePath(file);
            
            try {
                if (Files.exists(path)) {
                    Files.setLastModifiedTime(path, FileTime.from(java.time.Instant.now()));
                } else {
                    Files.createFile(path);
                }
            } catch (IOException e) {
                result.append("touch: cannot touch '").append(file).append("': ").append(e.getMessage()).append("\n");
            }
        }
        
        return result.toString().trim();
    }
    
    private String findFiles(String[] args) {
        Path searchPath = fileSystemState.getCurrentWorkingDirectory();
        String pattern = "*";
        
        if (args.length > 0) {
            searchPath = fileSystemState.resolvePath(args[0]);
        }
        if (args.length > 1) {
            pattern = args[1];
        }
        
        StringBuilder result = new StringBuilder();
        final String finalPattern = pattern;
        
        try {
            Files.walkFileTree(searchPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    if (fileName.matches(finalPattern.replace("*", ".*").replace("?", "."))) {
                        result.append(file.toString()).append("\n");
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "find: " + e.getMessage();
        }
        
        return result.toString().trim();
    }
    
    private String wordCount(String[] args) {
        if (args.length == 0) {
            return "wc: missing file operand";
        }
        
        StringBuilder result = new StringBuilder();
        
        for (String file : args) {
            Path path = fileSystemState.resolvePath(file);
            
            if (!Files.exists(path)) {
                result.append("wc: ").append(file).append(": No such file or directory\n");
                continue;
            }
            
            if (Files.isDirectory(path)) {
                result.append("wc: ").append(file).append(": Is a directory\n");
                continue;
            }
            
            try {
                List<String> lines = Files.readAllLines(path);
                int lineCount = lines.size();
                int wordCount = 0;
                int charCount = 0;
                
                for (String line : lines) {
                    wordCount += line.split("\\s+").length;
                    charCount += line.length() + 1; // +1 for newline
                }
                
                result.append(String.format("%8d %8d %8d %s\n", lineCount, wordCount, charCount, file));
            } catch (IOException e) {
                result.append("wc: ").append(file).append(": ").append(e.getMessage()).append("\n");
            }
        }
        
        return result.toString().trim();
    }
    
    private String headFile(String[] args) {
        int lines = 10;
        List<String> files = new ArrayList<>();
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-n") && i + 1 < args.length) {
                try {
                    lines = Integer.parseInt(args[i + 1]);
                    i++; // skip next argument
                } catch (NumberFormatException e) {
                    return "head: invalid number of lines: " + args[i + 1];
                }
            } else {
                files.add(args[i]);
            }
        }
        
        if (files.isEmpty()) {
            return "head: missing file operand";
        }
        
        StringBuilder result = new StringBuilder();
        
        for (String file : files) {
            Path path = fileSystemState.resolvePath(file);
            
            if (!Files.exists(path)) {
                result.append("head: ").append(file).append(": No such file or directory\n");
                continue;
            }
            
            if (Files.isDirectory(path)) {
                result.append("head: ").append(file).append(": Is a directory\n");
                continue;
            }
            
            try {
                List<String> fileLines = Files.readAllLines(path);
                int linesToShow = Math.min(lines, fileLines.size());
                
                for (int i = 0; i < linesToShow; i++) {
                    result.append(fileLines.get(i)).append("\n");
                }
            } catch (IOException e) {
                result.append("head: ").append(file).append(": ").append(e.getMessage()).append("\n");
            }
        }
        
        return result.toString().trim();
    }
    
    private String tailFile(String[] args) {
        int lines = 10;
        List<String> files = new ArrayList<>();
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-n") && i + 1 < args.length) {
                try {
                    lines = Integer.parseInt(args[i + 1]);
                    i++; // skip next argument
                } catch (NumberFormatException e) {
                    return "tail: invalid number of lines: " + args[i + 1];
                }
            } else {
                files.add(args[i]);
            }
        }
        
        if (files.isEmpty()) {
            return "tail: missing file operand";
        }
        
        StringBuilder result = new StringBuilder();
        
        for (String file : files) {
            Path path = fileSystemState.resolvePath(file);
            
            if (!Files.exists(path)) {
                result.append("tail: ").append(file).append(": No such file or directory\n");
                continue;
            }
            
            if (Files.isDirectory(path)) {
                result.append("tail: ").append(file).append(": Is a directory\n");
                continue;
            }
            
            try {
                List<String> fileLines = Files.readAllLines(path);
                int startLine = Math.max(0, fileLines.size() - lines);
                
                for (int i = startLine; i < fileLines.size(); i++) {
                    result.append(fileLines.get(i)).append("\n");
                }
            } catch (IOException e) {
                result.append("tail: ").append(file).append(": ").append(e.getMessage()).append("\n");
            }
        }
        
        return result.toString().trim();
    }
    
    /**
     * Handle prompt command to switch between prompt templates
     */
    private String promptCommand(String[] args) {
        if (args.length == 0) {
            // List available prompts and show current prompt
            StringBuilder result = new StringBuilder();
            result.append("🎨 Available prompt templates:\n\n");
            
            String currentPrompt = settings.getCurrentPrompt();
            List<String> templates = promptConfig.getAvailableTemplateNames();
            
            for (String templateName : templates) {
                PromptConfig.PromptTemplate template = promptConfig.getTemplate(templateName);
                if (template != null) {
                    String marker = templateName.equals(currentPrompt) ? "➤ " : "  ";
                    String emoji = template.useEmoji ? "✨ " : "";
                    result.append(String.format("%s%s%s - %s\n", marker, emoji, templateName, template.description));
                }
            }
            
            result.append("\n💡 Current prompt: ").append(currentPrompt);
            result.append("\n🔧 Usage: prompt <template-name> to switch prompts");
            result.append("\n📁 Custom prompts are stored in ~/.lucli/prompts/");
            
            return result.toString();
        } else {
            String templateName = args[0];
            
            if (promptConfig.setCurrentTemplate(templateName)) {
                PromptConfig.PromptTemplate template = promptConfig.getTemplate(templateName);
                String emoji = settings.showEmojis() && template.useEmoji ? "✨ " : "";
                return "✅ Prompt changed to: " + emoji + templateName + " - " + template.description;
            } else {
                return "❌ Unknown prompt template: " + templateName + "\n💡 Use 'prompt' to see available templates.";
            }
        }
    }
    
    /**
     * Execute a CFML script file using LuceeScriptEngine
     */
    private String runScriptCommand(String[] args) {
        if (args.length == 0) {
            return "❌ run: missing file operand\n💡 Usage: run <file.cfm|file.cfc|file.cfs> [arguments...]";
        }
        
        String scriptFile = args[0];
        String[] scriptArgs = Arrays.copyOfRange(args, 1, args.length);
        
        Path scriptPath = fileSystemState.resolvePath(scriptFile);
        
        if (!Files.exists(scriptPath)) {
            return "❌ run: cannot find file '" + scriptFile + "'";
        }
        
        if (Files.isDirectory(scriptPath)) {
            return "❌ run: '" + scriptFile + "' is a directory";
        }
        
        // Check if file has a supported CFML extension
        String fileName = scriptPath.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".cfm") && !fileName.endsWith(".cfc") && !fileName.endsWith(".cfs")) {
            return "❌ run: '" + scriptFile + "' is not a CFML file (.cfm, .cfc, or .cfs)";
        }
        
        try {
            // Get or create the LuceeScriptEngine instance
            LuceeScriptEngine luceeEngine = LuceeScriptEngine.getInstance(false, false); // non-verbose for cleaner output
            
            // Execute the script file with arguments
            luceeEngine.executeScript(scriptPath.toString(), scriptArgs);
            
            // Success - no output needed as script execution handles its own output
            return "";
            
        } catch (Exception e) {
            return "❌ Error executing CFML script '" + scriptFile + "': " + e.getMessage();
        }
    }
    
    /**
     * Get the appropriate emoji for a file based on its type and extension
     */
    private String getFileEmoji(Path file) {
        if (!settings.showEmojis()) {
            return "";
        }
        
        try {
            if (Files.isDirectory(file)) {
                return "📁";
            }
            
            if (Files.isSymbolicLink(file)) {
                return "🔗";
            }
            
            String fileName = file.getFileName().toString().toLowerCase();
            
            // Hidden files
            if (fileName.startsWith(".")) {
                return "👻";
            }
            
            // Get file extension
            String extension = "";
            int lastDot = fileName.lastIndexOf(".");
            if (lastDot > 0) {
                extension = fileName.substring(lastDot + 1);
            }
            
            // Programming files
            switch (extension) {
                case "java": return "☕";
                case "js": case "javascript": return "🟨";
                case "py": case "python": return "🐍";
                case "html": case "htm": return "🌐";
                case "css": return "🎨";
                case "json": return "📋";
                case "xml": return "📄";
                case "yml": case "yaml": return "⚙️";
                case "md": case "markdown": return "📝";
                case "txt": return "📄";
                case "log": return "📊";
                case "sql": return "🗃️";
                case "sh": case "bash": return "⚡";
                case "bat": case "cmd": return "⚡";
                case "ps1": return "💙";
                case "cfm": case "cfml": case "cfc": case "cfs": return "⚡";
                
                // Image files
                case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "svg": case "webp":
                    return "🖼️";
                    
                // Video files
                case "mp4": case "avi": case "mkv": case "mov": case "wmv": case "flv": case "webm":
                    return "🎬";
                    
                // Audio files
                case "mp3": case "wav": case "flac": case "aac": case "ogg": case "m4a":
                    return "🎵";
                    
                // Archive files
                case "zip": case "rar": case "tar": case "gz": case "7z": case "bz2":
                    return "📦";
                    
                // Document files
                case "pdf": return "📕";
                case "doc": case "docx": return "📄";
                case "xls": case "xlsx": return "📊";
                case "ppt": case "pptx": return "📊";
                
                // Configuration files
                case "conf": case "config": case "ini": case "properties":
                    return "⚙️";
                    
                // Database files
                case "db": case "sqlite": case "mdb":
                    return "🗃️";
                    
                default:
                    // Check if file is executable
                    if (Files.isExecutable(file)) {
                        return "⚡";
                    }
                    return "📄";
            }
        } catch (Exception e) {
            return "📄"; // Default file emoji on error
        }
    }
    
    /**
     * Get available commands for help
     */
    public String getAvailableCommands() {
        boolean showEmojis = settings.showEmojis();
        String prefix = showEmojis ? "📁 " : "";
        
        return prefix + "Available file system commands:\n" +
               "  ls [-la]              📋 List directory contents\n" +
               "  cd [directory]        📂 Change directory\n" +
               "  pwd                   📍 Print working directory\n" +
               "  mkdir [-p] dir...     ➕ Create directories\n" +
               "  rmdir dir...          ➖ Remove empty directories\n" +
               "  rm [-rf] file...      🗑️  Remove files and directories\n" +
               "  cp src dest           📄 Copy files\n" +
               "  mv src dest           🚚 Move/rename files\n" +
               "  cat file...           👀 Display file contents\n" +
               "  touch file...         ✨ Create/update file timestamps\n" +
               "  find [dir] [pattern]  🔍 Find files\n" +
               "  wc file...            📊 Count lines, words, characters\n" +
               "  head [-n num] file... ⬆️  Show first lines of file\n" +
               "  tail [-n num] file... ⬇️  Show last lines of file\n" +
               "  run file.cf[ms] [...] ⚡ Execute CFML script files\n" +
               "  prompt [template]     🎨 Change prompt style";
    }
}
