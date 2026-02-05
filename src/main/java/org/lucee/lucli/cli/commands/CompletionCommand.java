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
        CompletionCommand.ZshCompletionCommand.class,
        CompletionCommand.MarkdownDocCommand.class
    }
)
public class CompletionCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        // If no subcommand specified, show help
        System.out.println("Usage: lucli completion <bash|zsh|md>");
        System.out.println("\nGenerate shell completion scripts or markdown documentation.");
        System.out.println("\nCommands:");
        System.out.println("  bash  Generate bash completion script");
        System.out.println("  zsh   Generate zsh completion script");
        System.out.println("  md    Generate markdown documentation");
        System.out.println("\nInstallation:");
        System.out.println("  Bash: lucli completion bash | sudo tee /etc/bash_completion.d/lucli");
        System.out.println("  Zsh:  lucli completion zsh > ~/.zsh/lucli_completion && echo 'source ~/.zsh/lucli_completion' >> ~/.zshrc");
        System.out.println("  Docs: lucli completion md > docs/command-reference.md");
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
            CommandLine cmd = new CommandLine(new org.lucee.lucli.LuCLI());
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
            CommandLine cmd = new CommandLine(new org.lucee.lucli.LuCLI());
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

    /**
     * Markdown documentation generator
     */
    @Command(
        name = "md",
        description = "Generate markdown documentation from command structure"
    )
    public static class MarkdownDocCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            String markdown = generateMarkdownDoc();
            System.out.print(markdown);
            return 0;
        }

        private static String generateMarkdownDoc() {
            CommandLine cmd = new CommandLine(new org.lucee.lucli.LuCLI());
            StringBuilder md = new StringBuilder();
            
            // Add front matter and title
            md.append("---\n");
            md.append("title: Command Reference\n");
            md.append("layout: docs\n");
            md.append("---\n\n");
            md.append("# Command Reference\n\n");
            md.append("Auto-generated reference for all LuCLI commands and their options.\n\n");
            
            // Add global options
            md.append("## Global Options\n\n");
            md.append("These options work with any command:\n\n");
            md.append("| Option | Short | Description |\n");
            md.append("|--------|-------|-------------|\n");
            
            CommandLine.Model.CommandSpec spec = cmd.getCommandSpec();
            for (CommandLine.Model.OptionSpec option : spec.options()) {
                if (option.hidden()) continue;
                String names = String.join(", ", option.names());
                String shortOpt = "";
                String longOpt = "";
                
                for (String name : option.names()) {
                    if (name.startsWith("--")) {
                        longOpt = "`" + name + "`";
                    } else if (name.startsWith("-") && name.length() == 2) {
                        shortOpt = "`" + name + "`";
                    }
                }
                
                String description = option.description().length > 0 ? option.description()[0] : "";
                md.append("| ").append(longOpt).append(" | ").append(shortOpt).append(" | ")
                  .append(description).append(" |\n");
            }
            
            md.append("\n");
            
            // Add main commands section
            md.append("## Commands\n\n");
            
            // Document each subcommand
            for (CommandLine subcommand : cmd.getSubcommands().values()) {
                CommandLine.Model.CommandSpec subSpec = subcommand.getCommandSpec();
                if (subSpec.userObject() == null || subSpec.name().equals("help")) continue;
                
                documentCommand(md, subcommand, 3);
            }
            
            // Add examples section
            md.append("\n---\n\n");
            md.append("## Quick Reference\n\n");
            md.append("### Most Common Commands\n\n");
            md.append("```bash\n");
            md.append("# Start server\n");
            md.append("lucli server start\n\n");
            md.append("# Stop server\n");
            md.append("lucli server stop\n\n");
            md.append("# Execute script\n");
            md.append("lucli script.cfs\n\n");
            md.append("# Interactive mode\n");
            md.append("lucli\n\n");
            md.append("# Get help\n");
            md.append("lucli --help\n");
            md.append("lucli help server\n");
            md.append("```\n");
            
            return md.toString();
        }
        
        private static String getCommandPath(CommandLine cmd) {
            StringBuilder path = new StringBuilder();
            CommandLine current = cmd;

            // Walk up the command hierarchy, skipping the root (which has no parent)
            while (current != null && current.getParent() != null) {
                CommandLine.Model.CommandSpec currentSpec = current.getCommandSpec();
                if (path.length() == 0) {
                    path.insert(0, currentSpec.name());
                } else {
                    path.insert(0, currentSpec.name() + " ");
                }
                current = current.getParent();
            }

            return path.toString();
        }

        private static void documentCommand(StringBuilder md, CommandLine cmd, int headerLevel) {
            CommandLine.Model.CommandSpec spec = cmd.getCommandSpec();
            String headerPrefix = "#".repeat(headerLevel);
            String commandPath = getCommandPath(cmd);
            
            // Command name and description (use full command path like "server start")
            md.append(headerPrefix).append(" `lucli ").append(commandPath).append("`\n\n");
            
            if (spec.usageMessage().description().length > 0) {
                md.append(spec.usageMessage().description()[0]).append("\n\n");
            }
            
            // Usage
            md.append("**Usage:**\n");
            md.append("```bash\n");
            md.append("lucli ").append(commandPath);
            if (!spec.options().isEmpty()) {
                md.append(" [OPTIONS]");
            }
            if (!cmd.getSubcommands().isEmpty()) {
                md.append(" [COMMAND]");
            }
            md.append("\n```\n\n");
            
            // Options table
            if (!spec.options().isEmpty()) {
                md.append("**Options:**\n\n");
                md.append("| Option | Description |\\n");
                md.append("|--------|-------------|\\n");
                
                for (CommandLine.Model.OptionSpec option : spec.options()) {
                    if (option.hidden()) continue;
                    
                    String names = String.join(", ", option.names());
                    String description = option.description().length > 0 ? option.description()[0] : "";
                    
                    md.append("| `").append(names).append("` | ")
                      .append(description).append(" |\\n");
                }
                md.append("\\n");
            }
            
            // Subcommands
            if (!cmd.getSubcommands().isEmpty()) {
                md.append("**Subcommands:**\\n\\n");
                
                for (CommandLine subcommand : cmd.getSubcommands().values()) {
                    CommandLine.Model.CommandSpec subSpec = subcommand.getCommandSpec();
                    if (subSpec.userObject() == null) continue;
                    
                    String subDescription = subSpec.usageMessage().description().length > 0 
                        ? subSpec.usageMessage().description()[0] : "";
                    String subCommandPath = getCommandPath(subcommand);
                    
                    md.append("- **`lucli ").append(subCommandPath).append("`** - ")
                      .append(subDescription).append("\\n");
                }
                md.append("\\n");
                
                // Document each subcommand recursively
                for (CommandLine subcommand : cmd.getSubcommands().values()) {
                    CommandLine.Model.CommandSpec subSpec = subcommand.getCommandSpec();
                    if (subSpec.userObject() == null) continue;
                    
                    documentCommand(md, subcommand, headerLevel + 1);
                }
            }
            
            md.append("---\\n\\n");
        }
    }
}
