package org.lucee.lucli.cli.commands;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.lucee.lucli.cli.LuCLICommand;
import org.lucee.lucli.commands.UnifiedCommandExecutor;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Server management command using Picocli
 */
@Command(
    name = "server",
    description = "Manage Lucee server instances",
    subcommands = {
        ServerCommand.StartCommand.class,
        ServerCommand.StopCommand.class,
        ServerCommand.StatusCommand.class,
        ServerCommand.ListCommand.class,
        ServerCommand.LogCommand.class,
        ServerCommand.MonitorCommand.class
    }
)
public class ServerCommand implements Callable<Integer> {

    @ParentCommand 
    private LuCLICommand parent;

    @Override
    public Integer call() throws Exception {
        // If server command is called without subcommand, show help
        new picocli.CommandLine(this).usage(System.out);
        return 0;
    }

    /**
     * Server start subcommand
     */
    @Command(
        name = "start", 
        description = "Start a Lucee server instance"
    )
    static class StartCommand implements Callable<Integer> {

        @ParentCommand 
        private ServerCommand parent;

        @Option(names = {"-v", "--version"}, 
                description = "Lucee version to use (e.g., 6.2.2.91)")
        private String version;

        @Option(names = {"-n", "--name"}, 
                description = "Custom name for the server instance")
        private String name;

        @Option(names = {"-p", "--port"}, 
                description = "Port number for the server (e.g., 8080)")
        private Integer port;

        @Option(names = {"-f", "--force"}, 
                description = "Force replace existing server with same name")
        private boolean force = false;

        @Parameters(paramLabel = "[PROJECT_DIR]", 
                    description = "Project directory (defaults to current directory)",
                    arity = "0..1")
        private String projectDir;

        @Override
        public Integer call() throws Exception {
            // Get current working directory
            Path currentDir = projectDir != null ? 
                Paths.get(projectDir) : 
                Paths.get(System.getProperty("user.dir"));

            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, currentDir);

            // Build arguments array for the unified executor
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("start");
            
            if (version != null) {
                args.add("--version");
                args.add(version);
            }
            if (name != null) {
                args.add("--name"); 
                args.add(name);
            }
            if (port != null) {
                args.add("--port");
                args.add(port.toString());
            }
            if (force) {
                args.add("--force");
            }
            if (projectDir != null) {
                args.add(projectDir);
            }

            // Execute the server start command
            String result = executor.executeCommand("server", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    /**
     * Server stop subcommand
     */
    @Command(
        name = "stop", 
        description = "Stop a Lucee server instance"
    )
    static class StopCommand implements Callable<Integer> {

        @ParentCommand 
        private ServerCommand parent;

        @Option(names = {"-n", "--name"}, 
                description = "Name of the server instance to stop")
        private String name;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Build arguments array
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("stop");
            
            if (name != null) {
                args.add("--name");
                args.add(name);
            }

            // Execute the server stop command
            String result = executor.executeCommand("server", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    /**
     * Server status subcommand
     */
    @Command(
        name = "status", 
        description = "Show status of server instances"
    )
    static class StatusCommand implements Callable<Integer> {

        @ParentCommand 
        private ServerCommand parent;

        @Option(names = {"-n", "--name"}, 
                description = "Name of specific server to check")
        private String name;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Build arguments array
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("status");
            
            if (name != null) {
                args.add("--name");
                args.add(name);
            }

            // Execute the server status command
            String result = executor.executeCommand("server", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    /**
     * Server list subcommand
     */
    @Command(
        name = "list", 
        description = "List all server instances"
    )
    static class ListCommand implements Callable<Integer> {

        @ParentCommand 
        private ServerCommand parent;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Execute the server list command
            String result = executor.executeCommand("server", new String[]{"list"});
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    /**
     * Server log subcommand
     */
    @Command(
        name = "log", 
        description = "View server logs"
    )
    static class LogCommand implements Callable<Integer> {

        @ParentCommand 
        private ServerCommand parent;

        @Option(names = {"-n", "--name"}, 
                description = "Name of server instance")
        private String name;

        @Option(names = {"-f", "--follow"}, 
                description = "Follow log output (tail -f)")
        private boolean follow = false;

        @Option(names = {"-t", "--type"}, 
                description = "Log type (server, access, error)")
        private String type;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Build arguments array
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("log");
            
            if (name != null) {
                args.add("--name");
                args.add(name);
            }
            if (follow) {
                args.add("--follow");
            }
            if (type != null) {
                args.add("--type");
                args.add(type);
            }

            // Execute the server log command
            String result = executor.executeCommand("server", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    /**
     * Server monitor subcommand
     */
    @Command(
        name = "monitor", 
        description = "Monitor server performance via JMX"
    )
    static class MonitorCommand implements Callable<Integer> {

        @ParentCommand 
        private ServerCommand parent;

        @Option(names = {"-n", "--name"}, 
                description = "Name of server instance to monitor")
        private String name;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode  
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Build arguments array
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("monitor");
            
            if (name != null) {
                args.add("--name");
                args.add(name);
            }

            // Execute the server monitor command
            String result = executor.executeCommand("server", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }
}