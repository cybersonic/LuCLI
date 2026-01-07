package org.lucee.lucli.cli.completion;

import java.util.ArrayList;
import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import picocli.CommandLine;
import picocli.shell.jline3.PicocliJLineCompleter;

/**
 * Wrapper around PicocliJLineCompleter that tweaks grouping and labelling
 * of candidates for LuCLI's interactive terminal.
 *
 * - At the first word position, non-dash entries are grouped as "commands".
 * - Entries starting with '-' or '--' are grouped as "options".
 * - For all other positions, behaviour is delegated unchanged.
 */
public class PicocliStyledCompleter implements Completer {

    private final Completer delegate;

    public PicocliStyledCompleter(CommandLine commandLine) {
        this.delegate = new PicocliJLineCompleter(commandLine.getCommandSpec());
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        List<Candidate> delegateCandidates = new ArrayList<>();
        delegate.complete(reader, line, delegateCandidates);

        boolean firstWordContext = line == null
                || line.line() == null
                || line.line().trim().isEmpty()
                || line.words().size() == 1;

        for (Candidate c : delegateCandidates) {
            String value = c.value();
            String group = c.group();
            String description = c.descr();

            if (firstWordContext) {
                if (value != null && (value.startsWith("--") || value.startsWith("-"))) {
                    // Short and long options
                    group = "options";
                    if (description == null || description.isEmpty()) {
                        description = "Option";
                    }
                } else if (value != null && !value.isEmpty()) {
                    // Top-level commands (deps, module, server, etc.)
                    group = "commands";
                    if (description == null || description.isEmpty()) {
                        description = "Command";
                    }
                }
            }

            candidates.add(new Candidate(
                    c.value(),
                    c.displ(),
                    group,
                    description,
                    c.suffix(),
                    c.key(),
                    c.complete()));
        }
    }
}
