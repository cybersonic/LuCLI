package org.lucee.lucli;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tab completion for LuCLI commands and file paths
 */
public class LuCLICompleter implements Completer {
    private final CommandProcessor commandProcessor;
    private final String[] commands = {
        "ls", "dir", "cd", "pwd", "mkdir", "rmdir", "rm", "cp", "mv", 
        "cat", "touch", "find", "wc", "head", "tail", "cfml", "help", "exit", "quit"
    };
    
    public LuCLICompleter(CommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
    }
    
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        
        if (buffer.trim().isEmpty() || line.words().size() == 1) {
            // Complete commands
            String partial = line.words().isEmpty() ? "" : line.words().get(0);
            for (String command : commands) {
                if (command.startsWith(partial.toLowerCase())) {
                    candidates.add(new Candidate(command, command, null, null, null, null, true));
                }
            }
        } else {
            // Complete file paths for commands that take file arguments
            String command = line.words().get(0).toLowerCase();
            if (isFileCommand(command)) {
                String partial = line.words().size() > 1 ? line.words().get(line.words().size() - 1) : "";
                completeFilePaths(partial, candidates);
            }
        }
    }
    
    private boolean isFileCommand(String command) {
        switch (command) {
            case "ls":
            case "dir":
            case "cd":
            case "cat":
            case "cp":
            case "mv":
            case "rm":
            case "mkdir":
            case "rmdir":
            case "touch":
            case "find":
            case "wc":
            case "head":
            case "tail":
                return true;
            default:
                return false;
        }
    }
    
    private void completeFilePaths(String partial, List<Candidate> candidates) {
        try {
            Path currentDir = commandProcessor.getFileSystemState().getCurrentWorkingDirectory();
            Path basePath;
            String prefix = "";
            
            // Handle different path scenarios
            if (partial.isEmpty()) {
                basePath = currentDir;
            } else if (partial.equals("~")) {
                basePath = commandProcessor.getFileSystemState().getHomeDirectory();
                prefix = "~/";
            } else if (partial.startsWith("~/")) {
                Path homePath = commandProcessor.getFileSystemState().getHomeDirectory();
                String relativePart = partial.substring(2);
                int lastSlash = relativePart.lastIndexOf('/');
                if (lastSlash >= 0) {
                    basePath = homePath.resolve(relativePart.substring(0, lastSlash));
                    prefix = "~/" + relativePart.substring(0, lastSlash + 1);
                } else {
                    basePath = homePath;
                    prefix = "~/";
                }
            } else if (partial.startsWith("/")) {
                // Absolute path
                int lastSlash = partial.lastIndexOf('/');
                if (lastSlash > 0) {
                    basePath = Path.of(partial.substring(0, lastSlash));
                    prefix = partial.substring(0, lastSlash + 1);
                } else {
                    basePath = Path.of("/");
                    prefix = "/";
                }
            } else {
                // Relative path
                int lastSlash = partial.lastIndexOf('/');
                if (lastSlash >= 0) {
                    basePath = currentDir.resolve(partial.substring(0, lastSlash));
                    prefix = partial.substring(0, lastSlash + 1);
                } else {
                    basePath = currentDir;
                }
            }
            
            if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
                return;
            }
            
            // Get the partial filename to match
            String filenamePartial = "";
            if (!partial.isEmpty()) {
                int lastSlash = partial.lastIndexOf('/');
                filenamePartial = lastSlash >= 0 ? partial.substring(lastSlash + 1) : partial;
                if (partial.startsWith("~/") && lastSlash < 0) {
                    filenamePartial = partial.substring(2);
                }
            }
            
            // List files and directories
            final String finalFilenamePartial = filenamePartial;
            final String finalPrefix = prefix;
            
            try (Stream<Path> files = Files.list(basePath)) {
                files.filter(path -> {
                    String filename = path.getFileName().toString();
                    return filename.startsWith(finalFilenamePartial) && 
                           (finalFilenamePartial.startsWith(".") || !filename.startsWith("."));
                })
                .forEach(path -> {
                    String filename = path.getFileName().toString();
                    String completion = finalPrefix + filename;
                    if (Files.isDirectory(path)) {
                        completion += "/";
                        candidates.add(new Candidate(completion, completion, null, null, null, null, false));
                    } else {
                        candidates.add(new Candidate(completion, completion, null, null, null, null, true));
                    }
                });
            }
            
        } catch (IOException e) {
            // Ignore completion errors
        }
    }
}
