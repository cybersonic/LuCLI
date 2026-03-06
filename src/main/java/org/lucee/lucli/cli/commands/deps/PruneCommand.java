package org.lucee.lucli.cli.commands.deps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.paths.LucliPaths;

import picocli.CommandLine.Command;

/**
 * Prune cached git dependency clones under ~/.lucli/deps/git-cache.
 */
@Command(
    name = "prune",
    description = "Prune cached git dependency clones"
)
public class PruneCommand implements Runnable {

    @Override
    public void run() {
        try {
            Path gitCacheDir = LucliPaths.resolve().depsGitCacheDir();

            if (!Files.exists(gitCacheDir)) {
                StringOutput.Quick.info("No git dependency cache found at " + gitCacheDir);
                return;
            }

            StringOutput.Quick.info("Pruning git dependency cache at " + gitCacheDir + "...");

            try (Stream<Path> stream = Files.walk(gitCacheDir)) {
                stream.sorted(Comparator.reverseOrder())
                      .map(Path::toFile)
                      .forEach(File::delete);
            }

            StringOutput.Quick.success("✓ Git dependency cache pruned");
        } catch (IOException e) {
            System.err.println("Error pruning git dependency cache: " + e.getMessage());
        }
    }
}
