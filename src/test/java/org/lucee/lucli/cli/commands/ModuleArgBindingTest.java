package org.lucee.lucli.cli.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for LuCLI's module dispatch auto-binding CLI positional
 * args (arg1, arg2, ...) to the target function's declared typed parameters.
 *
 * Installs a tiny fixture module that declares typed-param signatures and
 * reports via out() which values arrived where. Spawns the dev-lucli.sh
 * subprocess to exercise CLI invocation (not MCP) — the positional dispatch
 * path is what this feature fixes.
 */
class ModuleArgBindingTest {

    private static final String FIXTURE_MODULE_NAME = "typedbind";

    private static Path fixtureInstalledPath;
    private static String lucliBin;

    // The LuCLI home for this class: the fixture is installed here and the
    // dev-lucli.sh child gets it as LUCLI_HOME, so the suite never touches the
    // developer's own home and passes in a clean one.
    @TempDir
    static Path lucliHome;

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeFalse(
                System.getProperty("os.name").toLowerCase().contains("win"),
                "ModuleArgBindingTest uses bash subprocess — skipped on Windows");

        Path fixtureSrc = Path.of("src/test/resources/typedbind-fixture-module")
                .toAbsolutePath();
        assertTrue(Files.isDirectory(fixtureSrc),
                "fixture source not found at " + fixtureSrc);

        Path modulesDir = lucliHome.resolve("modules");
        Files.createDirectories(modulesDir);
        fixtureInstalledPath = modulesDir.resolve(FIXTURE_MODULE_NAME);

        if (Files.exists(fixtureInstalledPath)) {
            deleteRecursively(fixtureInstalledPath);
        }
        copyDirectory(fixtureSrc, fixtureInstalledPath);

        File devBin = new File("dev-lucli.sh");
        assertTrue(devBin.exists(), "dev-lucli.sh must be run from LuCLI repo root");
        lucliBin = devBin.getAbsolutePath();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (fixtureInstalledPath != null && Files.exists(fixtureInstalledPath)) {
            deleteRecursively(fixtureInstalledPath);
        }
    }

    @Test
    void twoPositionalArgsBindToTypedParams() throws Exception {
        String output = runCli("typedbind", "report", "hello", "world");
        assertTrue(output.contains("first=hello second=world"),
                "expected 'first=hello second=world' in output. Got: " + output);
    }

    @Test
    void singlePositionalLeavesOptionalAtDefault() throws Exception {
        String output = runCli("typedbind", "report", "onlyone");
        assertTrue(output.contains("first=onlyone second=default-second"),
                "expected 'first=onlyone second=default-second' in output. Got: " + output);
    }

    @Test
    void namedArgBeatsPositional() throws Exception {
        String output = runCli("typedbind", "report", "positional", "--first=named", "--second=alsoNamed");
        assertTrue(output.contains("first=named second=alsoNamed"),
                "expected named args to override positional. Got: " + output);
    }

    @Test
    void moduleTimeoutValuesReachTheModuleInsteadOfTheRootParser() throws Exception {
        for (String value : List.of("5", "x", "0")) {
            String output = runCli("typedbind", "deadline", "--timeout=" + value);
            assertTrue(output.contains("module timeout=" + value), output);
        }
        String split = runCli("typedbind", "deadline", "--timeout", "7");
        assertTrue(split.contains("module timeout=7"), split);
        String prefixed = runCli("--verbose", "--timeout=40", "typedbind", "deadline", "--timeout=9");
        assertTrue(prefixed.contains("module timeout=9"), prefixed);
        String explicit = runCli("modules", "run", "typedbind", "deadline", "--timeout=11");
        assertTrue(explicit.contains("module timeout=11"), explicit);
    }

    @Test
    void timeoutBeforeTheModuleRemainsARootOption() throws Exception {
        String output = runCli("--timeout", "5", "typedbind", "deadline");
        assertTrue(output.contains("module timeout=900"), output);
        String invalid = runCli("--timeout=x", "typedbind", "deadline");
        assertTrue(invalid.contains("Invalid value for option '--timeout'"), invalid);
    }

    @Test
    void clusteredRootFlagsAndAttachedValuesPreserveModuleTimeout() throws Exception {
        for (String[] prefix : List.of(new String[]{"-vd"}, new String[]{"-veprod"},
                new String[]{"-ve", "prod"}, new String[]{"-eprod"})) {
            List<String> argv = new ArrayList<>(List.of(prefix));
            argv.addAll(List.of("typedbind", "deadline", "--timeout=5"));
            String output = runCli(argv.toArray(new String[0]));
            assertTrue(output.contains("module timeout=5"), output);
        }
    }

    @Test
    void falseHelpFlagsRunTheRequestedCommand() throws Exception {
        for (String flag : List.of("--help=false", "-h=false", "--help=0", "--no-help")) {
            String output = runCli("typedbind", "report", "hello", flag);
            assertTrue(output.contains("first=hello second=default-second"), flag + ": " + output);
        }
    }

    @Test
    void enabledHelpFlagsStillShowModuleHelp() throws Exception {
        for (String flag : List.of("--help", "-h", "--help=true", "-h=true")) {
            String output = runCli("typedbind", "report", "hello", flag);
            assertTrue(output.contains("fixture help"), flag + ": " + output);
        }
    }

    @Test
    void childRunsInTheTemporaryLucliHome() throws Exception {
        String output = runCli("typedbind", "report", "hello");
        assertTrue(Files.isDirectory(lucliHome.resolve("lucee-server")),
                "the child must create its Lucee context under LUCLI_HOME, not the developer's home");
        assertTrue(output.contains("first=hello"), output);
    }

    private String runCli(String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("/bin/bash");
        cmd.add(lucliBin);
        for (String a : args) cmd.add(a);

        ProcessBuilder pb = lucliProcess(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String out = r.lines().collect(Collectors.joining("\n"));
            proc.waitFor(120, TimeUnit.SECONDS);
            return out;
        }
    }

    private static void copyDirectory(Path src, Path dest) throws IOException {
        Files.createDirectories(dest);
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> {
                try {
                    Path target = dest.resolve(src.relativize(source));
                    if (Files.isDirectory(source)) Files.createDirectories(target);
                    else Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) { throw new RuntimeException(e); }
            });
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                  .forEach(p -> { try { Files.delete(p); } catch (IOException e) { throw new RuntimeException(e); } });
        }
    }

    // Every child gets this class's LuCLI home.
    private static ProcessBuilder lucliProcess(List<String> command) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("LUCLI_HOME", lucliHome.toString());
        return pb;
    }
}
