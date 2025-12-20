package org.lucee.lucli.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Implementation of server monitor subcommand.
 * Monitors server performance via JMX using the MonitorCommand utility.
 */
@Command(
    name = "monitor",
    description = "Monitor server performance via JMX"
)
public class ServerMonitorCommandImpl implements Callable<Integer> {

    @ParentCommand
    private ServerCommand parent;

    @Option(names = {"-n", "--name"},
            description = "Name of server instance to monitor")
    private String name;

    @Option(names = {"-h", "--host"},
            description = "JMX host (default: localhost)")
    private String host;

    @Option(names = {"-p", "--port"},
            description = "JMX port (default: 8999)")
    private Integer port;

    @Option(names = {"-r", "--refresh"},
            description = "Refresh interval in seconds (default: 3)")
    private Integer refreshInterval;

    @Option(names = {"--help"},
            usageHelp = true,
            description = "Show help for monitor command")
    private boolean helpRequested;

    @Override
    public Integer call() throws Exception {
        // Build arguments array for MonitorCommand
        List<String> args = new ArrayList<>();

        if (name != null) {
            args.add("--name");
            args.add(name);
        }

        if (host != null) {
            args.add("--host");
            args.add(host);
        }

        if (port != null) {
            args.add("--port");
            args.add(String.valueOf(port));
        }

        if (refreshInterval != null) {
            args.add("--refresh");
            args.add(String.valueOf(refreshInterval));
        }

        // Call MonitorCommand.executeMonitor() directly
        String result = org.lucee.lucli.monitoring.MonitorCommand.executeMonitor(args.toArray(new String[0]));

        // If result is not null, it means an error occurred
        if (result != null) {
            System.err.println(result);
            return 1;
        }

        // Monitoring completed successfully (or was interrupted)
        return 0;
    }
}
