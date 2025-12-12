package org.lucee.lucli.cli.completion;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves shell completion context and generates candidate completions.
 * Parses the current command line state (words and cursor position) to determine
 * which completions are appropriate.
 */
public class DynamicCompleter {
    private final CompletionProvider provider;

    public DynamicCompleter(CompletionProvider provider) {
        this.provider = provider;
    }

    /**
     * Get completion candidates for the current state
     * @param words array of all words typed so far
     * @param current index of the word being completed
     * @return list of completion candidates
     */
    public List<String> getCompletions(String[] words, int current) {
        if (words == null || words.length == 0) {
            return provider.getRootCompletions();
        }

        // Validate current index
        if (current < 0 || current >= words.length) {
            return new ArrayList<>();
        }

        // Extract the command path (non-option words before the current position)
        List<String> commandPath = new ArrayList<>();
        String currentWord = words[current];

        // Build command path from words before current position, excluding options
        for (int i = 0; i < current; i++) {
            String word = words[i];
            // Skip the program name (first word) and option flags
            if (i > 0 && !word.startsWith("-")) {
                commandPath.add(word);
            }
        }

        // If we're completing an option (starts with -), return options for current command
        if (currentWord.startsWith("-")) {
            return provider.getCompletions(commandPath);
        }

        // Otherwise, get all completions and filter by prefix
        return provider.getCompletionsWithPrefix(commandPath, currentWord);
    }

    /**
     * Parse shell completion context from strings
     * Converts bash/zsh completion format to internal representation
     * @param wordsString space-separated words string (format: "lucli server start")
     * @param currentIndex current word index being completed
     * @return list of completion candidates
     */
    public List<String> getCompletionsFromString(String wordsString, int currentIndex) {
        if (wordsString == null || wordsString.isEmpty()) {
            return provider.getRootCompletions();
        }

        String[] words = wordsString.split("\\s+");
        return getCompletions(words, currentIndex);
    }

    /**
     * Parse command-line style arguments (from __complete endpoint)
     * @param args command-line arguments in format:
     *            --words="lucli server start" --current=2
     * @return list of completion candidates
     */
    public List<String> parseArgumentsAndGetCompletions(String[] args) {
        String wordsString = null;
        int currentIndex = -1;

        // Parse arguments
        for (String arg : args) {
            if (arg.startsWith("--words=")) {
                wordsString = arg.substring("--words=".length());
                // Remove surrounding quotes if present
                if (wordsString.startsWith("\"") && wordsString.endsWith("\"")) {
                    wordsString = wordsString.substring(1, wordsString.length() - 1);
                }
            } else if (arg.startsWith("--current=")) {
                try {
                    currentIndex = Integer.parseInt(arg.substring("--current=".length()));
                } catch (NumberFormatException e) {
                    currentIndex = -1;
                }
            }
        }

        if (wordsString == null || currentIndex < 0) {
            return new ArrayList<>();
        }

        return getCompletionsFromString(wordsString, currentIndex);
    }

    /**
     * Format completion candidates for shell output
     * @param candidates list of completion candidates
     * @return formatted string with one candidate per line
     */
    public String formatCompletions(List<String> candidates) {
        if (candidates.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String candidate : candidates) {
            sb.append(candidate).append("\n");
        }

        // Remove trailing newline
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }
}
