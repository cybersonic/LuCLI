package org.lucee.lucli.cli.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * Provides command completion candidates by introspecting Picocli's CommandSpec model.
 * Extracts available commands, options, and subcommands from the command hierarchy.
 */
public class CompletionProvider {
    private final CommandLine commandLine;

    public CompletionProvider(CommandLine commandLine) {
        this.commandLine = commandLine;
    }

    /**
     * Get all available top-level commands and options
     */
    public List<String> getRootCompletions() {
        Set<String> candidates = new TreeSet<>();
        CommandSpec spec = commandLine.getCommandSpec();

        // Add subcommands
        if (spec.subcommands() != null) {
            candidates.addAll(spec.subcommands().keySet());
        }

        // Add options
        candidates.addAll(getOptionsForCommand(spec));

        return new ArrayList<>(candidates);
    }

    /**
     * Get completions for a specific command path
     * @param commandPath list of command names, e.g. ["server", "start"]
     * @return list of possible completions (subcommands or options)
     */
    public List<String> getCompletions(List<String> commandPath) {
        if (commandPath.isEmpty()) {
            return getRootCompletions();
        }

        CommandSpec currentSpec = resolveCommand(commandPath);
        if (currentSpec == null) {
            return Collections.emptyList();
        }

        Set<String> candidates = new TreeSet<>();

        // Add subcommands
        if (currentSpec.subcommands() != null) {
            candidates.addAll(currentSpec.subcommands().keySet());
        }

        // Add options
        candidates.addAll(getOptionsForCommand(currentSpec));

        return new ArrayList<>(candidates);
    }

    /**
     * Get completions for a partial word
     * @param commandPath command path to resolve
     * @param prefix the partial word to complete
     * @return filtered list of completions starting with prefix
     */
    public List<String> getCompletionsWithPrefix(List<String> commandPath, String prefix) {
        List<String> candidates = getCompletions(commandPath);
        List<String> filtered = new ArrayList<>();

        for (String candidate : candidates) {
            if (candidate.startsWith(prefix)) {
                filtered.add(candidate);
            }
        }

        return filtered;
    }

    /**
     * Resolve a command spec by following the command path
     * @param commandPath list of command names
     * @return the CommandSpec at the end of the path, or null if not found
     */
    private CommandSpec resolveCommand(List<String> commandPath) {
        CommandSpec currentSpec = commandLine.getCommandSpec();

        for (String commandName : commandPath) {
            if (currentSpec.subcommands() == null || !currentSpec.subcommands().containsKey(commandName)) {
                return null;
            }
            currentSpec = currentSpec.subcommands().get(commandName).getCommandSpec();
        }

        return currentSpec;
    }

    /**
     * Get all options for a command as completion candidates
     */
    private List<String> getOptionsForCommand(CommandSpec spec) {
        Set<String> options = new TreeSet<>();

        if (spec.options() != null) {
            for (OptionSpec option : spec.options()) {
                // Add all names (both short and long forms)
                if (option.names() != null) {
                    for (String name : option.names()) {
                        options.add(name);
                    }
                }
            }
        }

        return new ArrayList<>(options);
    }

    /**
     * Get a description for a command
     */
    public String getDescription(String commandName) {
        CommandSpec spec = commandLine.getCommandSpec();
        if (spec.subcommands() != null && spec.subcommands().containsKey(commandName)) {
            CommandLine subCmdLine = spec.subcommands().get(commandName);
            CommandSpec subSpec = subCmdLine.getCommandSpec();
            // usageMessage() returns the full usage message, not description
            // For now, return null since we don't need descriptions for completion
        }
        return null;
    }

    /**
     * Get all subcommand names for a given command path
     */
    public List<String> getSubcommands(List<String> commandPath) {
        CommandSpec spec = resolveCommand(commandPath);
        if (spec == null || spec.subcommands() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(spec.subcommands().keySet());
    }

    /**
     * Check if a command path is valid
     */
    public boolean isValidCommandPath(List<String> commandPath) {
        return resolveCommand(commandPath) != null;
    }
}
