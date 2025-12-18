package org.lucee.lucli.cli.commands;

import java.util.concurrent.Callable;

import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Public completion subcommand for generating shell completion scripts.
 * 
 * Supports bash and zsh shells on macOS and Linux.
 * Windows PowerShell completion is not currently supported.
 * 
 * Usage:
 *   lucli completion bash   # Generate bash completion script
 *   lucli completion zsh    # Generate zsh completion script
 */
@Command(
    name = "completion",
    description = "Generate shell completion scripts for bash or zsh",
    hidden = true,
    subcommands = {
        CompletionCommand.BashCompletionCommand.class,
        CompletionCommand.ZshCompletionCommand.class
    }
)
public class CompletionCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        // If no subcommand specified, show help
        System.out.println("Usage: lucli completion <bash|zsh>");
        System.out.println("\nGenerate shell completion scripts for bash or zsh.");
        System.out.println("Installation:");
        System.out.println("  Bash: lucli completion bash | sudo tee /etc/bash_completion.d/lucli");
        System.out.println("  Zsh:  lucli completion zsh > ~/.zsh/lucli_completion && echo 'source ~/.zsh/lucli_completion' >> ~/.zshrc");
        System.out.println("\nNote: The zsh script is bash-style completion with bashcompinit glue. It must be sourced from your .zshrc.");
        System.out.println("      Windows PowerShell completion is not yet supported.");
        return 0;
    }

    /**
     * Bash completion script generator
     */
    @Command(
        name = "bash",
        description = "Generate bash completion script"
    )
    public static class BashCompletionCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            String script = generateBashCompletion();
            System.out.print(script);
            return 0;
        }

        private static String generateBashCompletion() {
            // Use picocli's built-in AutoComplete generator so completion scripts
            // follow the standard, well-tested format understood by bash and zsh.
            CommandLine cmd = new CommandLine(new org.lucee.lucli.cli.LuCLICommand());
            String baseScript = AutoComplete.bash("lucli", cmd);
            
            // Post-process to make version completion dynamic
            return makeDynamicVersionCompletion(baseScript);
        }
    }

    /**
     * Zsh completion script generator
     */
    @Command(
        name = "zsh",
        description = "Generate zsh completion script"
    )
    public static class ZshCompletionCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            String script = generateZshCompletion();
            System.out.print(script);
            return 0;
        }

        private static String generateZshCompletion() {
            // Picocli's generated bash completion script is also compatible with zsh
            // (it contains the bashcompinit glue), so we can reuse the same script.
            CommandLine cmd = new CommandLine(new org.lucee.lucli.cli.LuCLICommand());
            String baseScript = AutoComplete.bash("lucli", cmd);
            
            // Post-process to make version completion dynamic
            return makeDynamicVersionCompletion(baseScript);
        }
    }
    
    /**
     * Post-processes the generated completion script to replace hardcoded version arrays
     * with dynamic calls to 'lucli versions-list'.
     */
    private static String makeDynamicVersionCompletion(String script) {
        // Create the dynamic replacement
        // Simply use the versions as-is, no quotes needed for completion
        String dynamicArray = "local version_option_args=($(lucli versions-list 2>/dev/null))";
        
        // Replace all occurrences of version_option_args array declarations
        // This handles both the start and tart (alias) commands
        String result = script;
        int startIdx = 0;
        while ((startIdx = result.indexOf("local version_option_args=(", startIdx)) != -1) {
            // Find the end including the closing parenthesis
            int endIdx = result.indexOf(") # --version values", startIdx);
            if (endIdx == -1) {
                break; // Malformed array, stop processing
            }
            
            // Extract the parts - include the closing ) in what we replace
            String before = result.substring(0, startIdx);
            String after = result.substring(endIdx + 1); // Skip the ) that we're replacing
            
            result = before + dynamicArray + after;
            startIdx = before.length() + dynamicArray.length();
        }
        
        return result;
    }
}
