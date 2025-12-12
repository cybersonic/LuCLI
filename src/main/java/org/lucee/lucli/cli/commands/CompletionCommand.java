package org.lucee.lucli.cli.commands;

import java.util.concurrent.Callable;

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
        System.out.println("  Zsh:  lucli completion zsh | sudo tee /usr/share/zsh/site-functions/_lucli");
        System.out.println("\nNote: Shell completion is currently supported for bash and zsh on macOS and Linux.");
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
            return "#!/usr/bin/env bash\n" +
                   "# LuCLI bash completion script\n" +
                   "# Install: lucli completion bash | sudo tee /etc/bash_completion.d/lucli\n" +
                   "\n" +
                   "_lucli_completion() {\n" +
                   "    local cur prev words cword\n" +
                   "    COMPREPLY=()\n" +
                   "    \n" +
                   "    # Get current word being completed\n" +
                   "    cur=\"${COMP_WORDS[COMP_CWORD]}\"\n" +
                   "    prev=\"${COMP_WORDS[COMP_CWORD-1]}\"\n" +
                   "    words=\"${COMP_WORDS[*]}\"\n" +
                   "    cword=$COMP_CWORD\n" +
                   "    \n" +
                   "    # Get completions from lucli\n" +
                   "    local completions=$(lucli __complete --words=\"$words\" --current=$cword 2>/dev/null)\n" +
                   "    \n" +
                   "    # Convert to bash COMPREPLY array\n" +
                   "    COMPREPLY=( $(compgen -W \"$completions\" -- \"$cur\") )\n" +
                   "}\n" +
                   "\n" +
                   "# Register the completion function\n" +
                   "complete -o bashdefault -o default -o nospace -F _lucli_completion lucli\n";
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
            return "#compdef lucli\n" +
                   "# LuCLI zsh completion script\n" +
                   "# Install: lucli completion zsh | sudo tee /usr/share/zsh/site-functions/_lucli\n" +
                   "\n" +
                   "_lucli() {\n" +
                   "    local state line words cword\n" +
                   "    local -a opts\n" +
                   "    \n" +
                   "    # Build words array from COMP_LINE\n" +
                   "    words=(${(@s: :)COMP_LINE})\n" +
                   "    cword=$((${#words[@]} - 1))\n" +
                   "    \n" +
                   "    # Get completions from lucli\n" +
                   "    local completions=$(lucli __complete --words=\"${(j: :)words[*]}\" --current=$cword 2>/dev/null)\n" +
                   "    \n" +
                   "    # Parse and add completions\n" +
                   "    local -a candidates\n" +
                   "    for line in \"${(@f)completions}\"; do\n" +
                   "        candidates+=(\"$line\")\n" +
                   "    done\n" +
                   "    \n" +
                   "    if [[ $#candidates -gt 0 ]]; then\n" +
                   "        _describe 'lucli' candidates\n" +
                   "    fi\n" +
                   "}\n" +
                   "\n" +
                   "_lucli \"$@\"\n";
        }
    }
}
