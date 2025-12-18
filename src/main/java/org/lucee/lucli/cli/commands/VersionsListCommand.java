package org.lucee.lucli.cli.commands;

import java.util.List;
import java.util.concurrent.Callable;

import org.lucee.lucli.commands.ServerConfigHelper;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Hidden command for shell completion to dynamically fetch Lucee versions.
 * This command is not shown in help output but is used by shell completion scripts.
 */
@Command(
    name = "versions-list",
    description = "List available Lucee versions (for shell completion use)",
    hidden = true  // Don't show in help output
)
public class VersionsListCommand implements Callable<Integer> {

    @Option(
        names = {"--no-cache"},
        description = "Bypass cache and fetch fresh versions"
    )
    private boolean noCache = false;

    @Override
    public Integer call() throws Exception {
        ServerConfigHelper helper = new ServerConfigHelper();
        List<String> versions = helper.getAvailableVersions(!noCache);
        
        // Output one version per line for easy parsing in shell scripts
        for (String version : versions) {
            System.out.println(version);
        }
        
        return 0;
    }
}
