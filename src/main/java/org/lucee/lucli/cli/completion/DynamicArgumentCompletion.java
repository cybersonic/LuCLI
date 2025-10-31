package org.lucee.lucli.cli.completion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic completion for module arguments that look like key=value
 */
public class DynamicArgumentCompletion implements Iterable<String> {

    @Override
    public java.util.Iterator<String> iterator() {
        List<String> candidates = new ArrayList<>();
        
        // Add common argument names for modules
        candidates.add("file=");
        candidates.add("path=");
        candidates.add("input=");
        candidates.add("output=");
        candidates.add("config=");
        candidates.add("format=");
        candidates.add("verbose=");
        candidates.add("debug=");
        
        return candidates.iterator();
    }

    /**
     * Completion for file= arguments with actual file paths
     */
    public static class FileArgumentCompletion implements Iterable<String> {
        private final String prefix;

        public FileArgumentCompletion(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public java.util.Iterator<String> iterator() {
            List<String> candidates = new ArrayList<>();
            
            try {
                if (prefix.startsWith("file=")) {
                    String filePath = prefix.substring(5);
                    Path basePath = filePath.isEmpty() ? Paths.get(".") : Paths.get(filePath);
                    
                    if (Files.isDirectory(basePath)) {
                        // List files in directory
                        Files.list(basePath)
                            .sorted()
                            .forEach(p -> candidates.add("file=" + p.getFileName()));
                    } else {
                        // Try to complete partial path
                        Path parent = basePath.getParent();
                        if (parent != null && Files.exists(parent)) {
                            String fileName = basePath.getFileName().toString();
                            Files.list(parent)
                                .filter(p -> p.getFileName().toString().startsWith(fileName))
                                .sorted()
                                .forEach(p -> candidates.add("file=" + p.getFileName()));
                        }
                    }
                }
            } catch (Exception e) {
                // Silently fail on completion errors
            }
            
            return candidates.iterator();
        }
    }
}
