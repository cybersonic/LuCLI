package org.lucee.lucli.cli.commands;

import java.util.List;
import java.util.concurrent.Callable;

import org.lucee.lucli.cli.completion.CompletionProvider;
import org.lucee.lucli.cli.completion.DynamicCompleter;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Hidden completion endpoint command for shell integration.
 * 
 * Usage: lucli __complete --words="lucli server" --current=1
 * Output: start\nstop\nstatus\nlist\nmonitor\nlog\n...
 * 
 * This is called by shell completion scripts to get available completions.
 * It is hidden from help output and not meant for direct user interaction.
 */
@Command(
    name = "__complete",
    hidden = true,
    description = "Internal completion endpoint for shell integration (not for direct use)"
)
public class CompleteCommand implements Callable<Integer> {

    @Option(names = "--words", 
            description = "Space-separated command words being completed (e.g., 'lucli server start')")
    private String words;

    @Option(names = "--current", 
            description = "Index of the word being completed")
    private int current = -1;

    @Override
    public Integer call() throws Exception {
        try {
            // Validate required arguments
            if (words == null || words.isEmpty() || current < 0) {
                return 0; // Silent failure for invalid input
            }
            
            // Create a new CommandLine with the main command
            Class<?> lucliCommandClass = Class.forName("org.lucee.lucli.cli.LuCLICommand");
            Object mainCommand = lucliCommandClass.getDeclaredConstructor().newInstance();
            CommandLine commandLine = new CommandLine(mainCommand);
            
            // Create completion infrastructure
            CompletionProvider provider = new CompletionProvider(commandLine);
            DynamicCompleter completer = new DynamicCompleter(provider);

            // Debug output if enabled
            if (System.getenv("LUCLI_DEBUG_COMPLETION") != null) {
                System.err.println("DEBUG: words=" + words + ", current=" + current);
            }

            // Parse arguments and get completions
            List<String> candidates = completer.getCompletionsFromString(words, current);
            
            if (System.getenv("LUCLI_DEBUG_COMPLETION") != null) {
                System.err.println("DEBUG: found " + candidates.size() + " candidates: " + candidates);
            }
            
            // Format and output results
            String formatted = completer.formatCompletions(candidates);
            if (!formatted.isEmpty()) {
                System.out.println(formatted);
            }

            return 0;
        } catch (Exception e) {
            // Silently fail for completion - don't show error messages to user
            // since this is called during shell completion
            if (System.getenv("LUCLI_DEBUG_COMPLETION") != null) {
                System.err.println("Completion error: " + e.getMessage());
                e.printStackTrace();
            }
            return 1;
        }
    }
}
