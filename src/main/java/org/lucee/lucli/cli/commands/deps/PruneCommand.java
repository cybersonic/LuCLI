package org.lucee.lucli.cli.commands.deps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import org.lucee.lucli.StringOutput;

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
            String lucliHome = System.getProperty("lucli.home");
            if (lucliHome == null) {
                lucliHome = System.getenv("LUCLI_HOME");
            }
            if (lucliHome == null) {
                lucliHome = System.getProperty("user.home") + "/.lucli";
            }

            Path gitCacheDir = Paths.get(lucliHome, "deps", "git-cache");

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
