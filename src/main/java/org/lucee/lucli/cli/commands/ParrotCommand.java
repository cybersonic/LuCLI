package org.lucee.lucli.cli.commands;

import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Simple parrot command as proof of concept for refactored architecture.
 * 
 * This demonstrates the correct pattern:
 * - PicocLI @Command with options/parameters
 * - Implementation directly in call() method
 * - No conversion to String[] and re-parsing
 * - Works in both CLI and terminal modes
 * 
 * Named 'parrot' to avoid confusion with system 'echo' command.
 */
@Command(
    name = "parrot",
    description = "Repeat back the provided text (proof of concept command)",
    mixinStandardHelpOptions = true
)
public class ParrotCommand implements Callable<Integer> {

    @Parameters(
        arity = "1..*",
        description = "Text to repeat back"
    )
    private List<String> words;

    @Override
    public Integer call() throws Exception {
        // ACTUAL IMPLEMENTATION HERE - No delegation to another processor!
        // Just do the work directly
        
        if (words == null || words.isEmpty()) {
            System.err.println("Error: No text provided");
            return 1;
        }
        
        // Parrot says:
        System.out.println("🦜 " + String.join(" ", words));
        return 0;
    }
}
