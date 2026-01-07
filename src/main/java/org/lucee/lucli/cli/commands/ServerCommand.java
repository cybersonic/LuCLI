package org.lucee.lucli.cli.commands;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.lucee.lucli.cli.LuCLICommand;
import org.lucee.lucli.cli.completion.LuceeVersionCandidates;
import org.lucee.lucli.commands.UnifiedCommandExecutor;
import org.lucee.lucli.server.LuceeServerManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Server management command using Picocli
 */
    @Command(
        name = "server",
        description = "Manage Lucee server instances",
        mixinStandardHelpOptions = true,
        subcommands = {
        ServerCommand.StartCommand.class,
        ServerCommand.RunCommand.class,
        ServerCommand.StopCommand.class,
        ServerCommand.RestartCommand.class,
        ServerCommand.StatusCommand.class,
        ServerCommand.ListCommand.class,
        ServerCommand.PruneCommand.class,
        ServerCommand.GetCommand.class,
        ServerCommand.SetCommand.class,
        ServerCommand.LogCommand.class,
        ServerCommand.OpenCommand.class,
        ServerMonitorCommandImpl.class
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
        aliases = {"tart"},
        description = "Start a Lucee server instance"
    )
    static class StartCommand implements Callable<Integer> {

        @ParentCommand 
        private ServerCommand parent;

        @Spec
        private CommandSpec spec;

        @Option(
                names = {"-v", "--version"},
                description = "Lucee version to use (e.g., 6.2.2.91)",
                completionCandidates = LuceeVersionCandidates.class
        )
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

        @Option(names = {"-c", "--config"}, 
                description = "Configuration file to use (defaults to lucee.json)")
        private String configFile;

        @Option(names = {"--env", "--environment"}, 
                description = "Environment to use (e.g., prod, dev, staging)")
        private String environment;

        @Option(names = {"--dry-run"}, 
                description = "Show configuration without starting the server")
        private boolean dryRun = false;

        @Option(names = {"--include-lucee"}, 
                description = "Include Lucee CFConfig in dry-run output")
        private boolean includeLucee = false;

        @Option(names = {"--include-tomcat-web"}, 
                description = "Include Tomcat web.xml in dry-run output")
        private boolean includeTomcatWeb = false;

        @Option(names = {"--include-tomcat-server"}, 
                description = "Include Tomcat server.xml in dry-run output")
        private boolean includeTomcatServer = false;

        @Option(names = {"--include-https-keystore-plan"}, 
                description = "Include HTTPS keystore plan in dry-run output")
        private boolean includeHttpsKeystorePlan = false;

        @Option(names = {"--include-https-redirect-rules"}, 
                description = "Include HTTPS redirect rules in dry-run output")
        private boolean includeHttpsRedirectRules = false;

        @Option(names = {"--include-all"}, 
                description = "Include all available dry-run previews")
        private boolean includeAll = false;

        @Option(names = {"--no-agents"}, 
                description = "Disable all Java agents")
        private boolean noAgents = false;

        @Option(names = {"--agents"}, 
                description = "Comma-separated list of agent IDs to include")
        private String agents;

        @Option(names = {"--enable-agent"}, 
                description = "Enable a specific agent by ID (repeatable)")
        private java.util.List<String> enableAgents;

        @Option(names = {"--disable-agent"}, 
                description = "Disable a specific agent by ID (repeatable)")
        private java.util.List<String> disableAgents;

        @Option(names = {"--open-browser"}, 
                description = "Open browser after server starts (default: true)",
                arity = "0..1",
                fallbackValue = "true")
        private String openBrowser;
        
        @Option(names = {"--disable-open-browser"}, 
                description = "Disable automatic browser opening")
        private boolean disableOpenBrowser = false;
        
        @Option(names = {"--disable-lucee"}, 
                description = "Disable Lucee CFML engine for this start (static server)")
        private boolean disableLucee = false;
        
        @Option(names = {"--enable-lucee"}, 
                description = "Explicitly enable Lucee CFML engine for this start")
        private boolean enableLucee = false;

        @Option(names = {"--webroot"},
                description = "Override webroot (relative to project directory, like lucee.json)")
        private String webroot;
        
        @Parameters(paramLabel = "[PROJECT_DIR]", 
                    description = "Project directory (defaults to current directory)",
                    arity = "0..1")
        private String projectDir;

        @picocli.CommandLine.Unmatched
        private java.util.List<String> configOverrides = new java.util.ArrayList<>();


        @Override
        public Integer call() throws Exception {
            boolean invokedAsTart = false;
            try {
                if (spec != null && spec.commandLine() != null && spec.commandLine().getParseResult() != null) {
                    for (String arg : spec.commandLine().getParseResult().originalArgs()) {
                        if ("tart".equals(arg)) {
                            invokedAsTart = true;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore and fall back to normal behavior
            }

            if (invokedAsTart) {
                System.out.println("Tip: 'lucli server tart' also starts a server. Sweet.");
                System.out.println("       .-\"\"-.");
                System.out.println("     .'  .-.  '.");
                System.out.println("    /   (   )   \\");
                System.out.println("   |  .-`-'-`-.  |");
                System.out.println("   |  |  pie  |  |");
                System.out.println("    \\ '.___.' /");
                System.out.println("     '.___ __.'");
            }

            if (disableLucee && enableLucee) {
                System.err.println("Cannot combine --enable-lucee and --disable-lucee in a single command.");
                return 1;
            }

            // Get current working directory
            Path currentDir = projectDir != null ?
                Paths.get(projectDir) :
                Paths.get(System.getProperty("user.dir"));

            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, currentDir);

            // Build arguments array for the unified executor
            // NOTE: do not pass projectDir as an argument; the executor already has the correct working directory.
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
            if (configFile != null) {
                args.add("--config");
                args.add(configFile);
            }
            if (environment != null) {
                args.add("--env");
                args.add(environment);
            }
            if (dryRun) {
                args.add("--dry-run");
            }
            if (includeLucee) {
                args.add("--include-lucee");
            }
            if (webroot != null) {
                args.add("--webroot");
                args.add(webroot);
            }
            if (includeTomcatWeb) {
                args.add("--include-tomcat-web");
            }
            if (includeTomcatServer) {
                args.add("--include-tomcat-server");
            }
            if (includeHttpsKeystorePlan) {
                args.add("--include-https-keystore-plan");
            }
            if (includeHttpsRedirectRules) {
                args.add("--include-https-redirect-rules");
            }
            if (includeAll) {
                args.add("--include-all");
            }
            if (noAgents) {
                args.add("--no-agents");
            }
            if (agents != null) {
                args.add("--agents");
                args.add(agents);
            }
            if (enableAgents != null) {
                for (String agent : enableAgents) {
                    args.add("--enable-agent");
                    args.add(agent);
                }
            }
            if (disableAgents != null) {
                for (String agent : disableAgents) {
                    args.add("--disable-agent");
                    args.add(agent);
                }
            }
            
            // Handle browser opening
            if (disableOpenBrowser) {
                args.add("openBrowser=false");
            } else if (openBrowser != null) {
                args.add("openBrowser=" + openBrowser);
            }

            // Handle Lucee engine toggles
            if (disableLucee) {
                args.add("--disable-lucee");
            } else if (enableLucee) {
                args.add("--enable-lucee");
            }
            
            // Add any config overrides (key=value pairs)
            if (configOverrides != null && !configOverrides.isEmpty()) {
                args.addAll(configOverrides);
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
     * Server run subcommand - starts server in foreground with log streaming
     */
    @Command(
        name = "run", 
        description = "Run a Lucee server in foreground (Ctrl+C to stop)"
    )
    static class RunCommand implements Callable<Integer> {

        @ParentCommand 
        private ServerCommand parent;

        @Spec
        private CommandSpec spec;

        @Option(
                names = {"-v", "--version"},
                description = "Lucee version to use (e.g., 6.2.2.91)",
                completionCandidates = LuceeVersionCandidates.class
        )
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

        @Option(names = {"-c", "--config"}, 
                description = "Configuration file to use (defaults to lucee.json)")
        private String configFile;
        
        @Option(names = {"--env", "--environment"}, 
                description = "Environment to use (e.g., prod, dev, staging)")
        private String environment;

        @Option(names = {"--webroot"},
                description = "Override webroot (relative to project directory, like lucee.json)")
        private String webroot;

        @Option(names = {"--no-agents"},
                description = "Disable all Java agents")
        private boolean noAgents = false;

        @Option(names = {"--agents"}, 
                description = "Comma-separated list of agent IDs to include")
        private String agents;

        @Option(names = {"--enable-agent"}, 
                description = "Enable a specific agent by ID (repeatable)")
        private java.util.List<String> enableAgents;

        @Option(names = {"--disable-agent"}, 
                description = "Disable a specific agent by ID (repeatable)")
        private java.util.List<String> disableAgents;

        @Parameters(paramLabel = "[PROJECT_DIR]", 
                    description = "Project directory (defaults to current directory)",
                    arity = "0..1")
        private String projectDir;

        @picocli.CommandLine.Unmatched
        private java.util.List<String> configOverrides = new java.util.ArrayList<>();


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
            args.add("run");

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
            if (configFile != null) {
                args.add("--config");
                args.add(configFile);
            }
            if (environment != null) {
                args.add("--env");
                args.add(environment);
            }
            if (webroot != null) {
                args.add("--webroot");
                args.add(webroot);
            }
            if (noAgents) {
                args.add("--no-agents");
            }
            if (agents != null) {
                args.add("--agents");
                args.add(agents);
            }
            if (enableAgents != null) {
                for (String agent : enableAgents) {
                    args.add("--enable-agent");
                    args.add(agent);
                }
            }
            if (disableAgents != null) {
                for (String agent : disableAgents) {
                    args.add("--disable-agent");
                    args.add(agent);
                }
            }

            // Add any config overrides (key=value pairs)
            if (configOverrides != null && !configOverrides.isEmpty()) {
                args.addAll(configOverrides);
            }

            // Execute the server run command
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

        @Option(names = {"--all"}, 
                description = "Stop all running servers")
        private boolean all = false;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Build arguments array
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("stop");
            
            if (all) {
                args.add("--all");
            } else if (name != null) {
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
     * Server restart subcommand
     */
    @Command(
        name = "restart", 
        description = "Show status of server instances"
    )
    static class RestartCommand implements Callable<Integer> {

        @ParentCommand 
        private ServerCommand parent;

        @Option(names = {"-n", "--name"}, 
                description = "Name of the server instance to restart")
        private String name;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Build arguments array
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("restart");
            
            if (name != null) {
                args.add("--name");
                args.add(name);
            }

            // Execute the server restart command
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

        @Option(names = {"-r", "--running"}, 
        description = "List only running server instances")
        private boolean running;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Execute the server list command
            String[] args = running ? new String[]{"list", "--running"} : new String[]{"list"};
            String result = executor.executeCommand("server", args);
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
     * Server prune subcommand
     */
    @Command(
        name = "prune",
        description = "Remove stopped server instances"
    )
    static class PruneCommand implements Callable<Integer> {

        @ParentCommand
        private ServerCommand parent;

        @Option(names = {"-a", "--all"},
                description = "Remove all stopped servers")
        private boolean all;

        @Option(names = {"-n", "--name"},
                description = "Remove a specific stopped server by name")
        private String name;

        @Option(names = {"-f", "--force"},
                description = "Skip confirmation prompt")
        private boolean force;

        @Override
        public Integer call() throws Exception {
            // Use UnifiedCommandExecutor in CLI mode (non-terminal)
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("prune");

            if (all) {
                args.add("--all");
            }
            if (name != null) {
                args.add("--name");
                args.add(name);
            }
            if (force) {
                args.add("--force");
            }

            String result = executor.executeCommand("server", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    /**
     * Server get subcommand for reading configuration
     */
    @Command(
        name = "get",
        description = "Get configuration values from lucee.json"
    )
    static class GetCommand implements Callable<Integer> {

        @ParentCommand
        private ServerCommand parent;

        @Parameters(paramLabel = "KEY",
                    description = "Configuration key to retrieve (e.g., port, admin.enabled)",
                    arity = "1")
        private String key;

        @Option(names = {"-d", "--directory"},
                description = "Project directory (defaults to current directory)")
        private String projectDir;

        @Override
        public Integer call() throws Exception {
            Path currentDir = projectDir != null ?
                Paths.get(projectDir) :
                Paths.get(System.getProperty("user.dir"));

            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, currentDir);

            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("config");
            args.add("get");
            args.add(key);

            String result = executor.executeCommand("server", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    /**
     * Server set subcommand for configuring settings
     */
    @Command(
        name = "set",
        description = "Set configuration values in lucee.json"
    )
    static class SetCommand implements Callable<Integer> {

        @ParentCommand
        private ServerCommand parent;

        @Option(names = {"--dry-run"},
                description = "Show what would be set without actually saving")
        private boolean dryRun = false;

        @Parameters(paramLabel = "KEY=VALUE",
                    description = "Configuration key=value pair (e.g., port=8080, admin.enabled=false)",
                    arity = "1..*")
        private String[] configPairs;

        @Option(names = {"-d", "--directory"},
                description = "Project directory (defaults to current directory)")
        private String projectDir;

        @Override
        public Integer call() throws Exception {
            Path currentDir = projectDir != null ?
                Paths.get(projectDir) :
                Paths.get(System.getProperty("user.dir"));

            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, currentDir);

            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("config");
            args.add("set");

            if (configPairs != null) {
                for (String pair : configPairs) {
                    args.add(pair);
                }
            }

            if (dryRun) {
                args.add("--dry-run");
            }

            String result = executor.executeCommand("server", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }
    /**
     * Server open subcommand
     */
    @Command(
        name = "open", 
        description = "Open a running server instance in a browser"
    )
    static class OpenCommand implements Callable<Integer> {

        @ParentCommand
        private ServerCommand parent;

        @Option(names = {"-n", "--name"},
                description = "Name of the server instance to open (defaults to current directory)")
        private String name;

        @Override
        public Integer call() throws Exception {
            // Default to current working directory when no name is provided
            Path currentDir = Paths.get(System.getProperty("user.dir"));

            LuceeServerManager serverManager = new LuceeServerManager();
            boolean opened = serverManager.openBrowser(currentDir, name);

            if (!opened) {
                if (name != null && !name.trim().isEmpty()) {
                    System.err.println("No running Lucee server found with name '" + name + "'.");
                } else {
                    System.err.println("No running Lucee server found for this directory. Start one with 'lucli server start'.");
                }
                return 1;
            }

            return 0;
        }
    }

}
