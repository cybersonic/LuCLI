package org.lucee.lucli.cli.commands.fs;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.lucee.lucli.CommandProcessor;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Picocli-backed wrappers for LuCLI's internal file system style commands.
 *
 * These commands delegate to {@link CommandProcessor} so that behaviour stays
 * consistent between interactive terminal mode, .lucli scripts, and any
 * future CLI exposure, while still allowing Picocli to provide help and
 * documentation for them.
 */
@Command(name = "fs", description = "Internal file system commands", subcommands = {
        FileSystemCommands.Ls.class,
        FileSystemCommands.Cd.class,
        FileSystemCommands.Pwd.class,
        FileSystemCommands.Mkdir.class,
        FileSystemCommands.Rmdir.class,
        FileSystemCommands.Rm.class,
        FileSystemCommands.Cp.class,
        FileSystemCommands.Mv.class,
        FileSystemCommands.Cat.class,
        FileSystemCommands.Head.class,
        FileSystemCommands.Tail.class,
        FileSystemCommands.Wc.class
})
public class FileSystemCommands {

    // Shared CommandProcessor instance used by all file system commands.
    // This is configured once during terminal initialization.
    private static CommandProcessor sharedProcessor;

    public FileSystemCommands(CommandProcessor processor) {
        setCommandProcessor(processor);
    }

    /**
     * Configure the CommandProcessor instance used by all file system commands.
     */
    public static void setCommandProcessor(CommandProcessor processor) {
        sharedProcessor = processor;
    }

    /**
     * Helper to run a CommandProcessor command and print the result.
     */
    static int runInternal(String name, List<String> args) {
        if (sharedProcessor == null) {
            throw new IllegalStateException("FileSystemCommands CommandProcessor has not been initialized");
        }

        StringBuilder line = new StringBuilder(name);
        if (args != null) {
            for (String a : args) {
                if (a == null || a.isBlank()) continue;
                line.append(' ').append(a);
            }
        }
        String result = sharedProcessor.executeCommand(line.toString());
        if (result != null && !result.isBlank()) {
            System.out.println(result);
        }
        return 0;
    }

    @Command(name = "ls", aliases = {"dir"}, description = "List directory contents (internal LuCLI command)")
    public static class Ls implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed through to the internal ls command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("ls", args);
        }
    }

    @Command(name = "cd", description = "Change directory (internal LuCLI command)")
    public static class Cd implements Callable<Integer> {

        @Parameters(arity = "0..1", paramLabel = "DIR", description = "Target directory (optional)")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("cd", args);
        }
    }

    @Command(name = "pwd", description = "Print working directory (internal LuCLI command)")
    public static class Pwd implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("pwd", Collections.emptyList());
        }
    }

    @Command(name = "mkdir", description = "Create directories (internal LuCLI command)")
    public static class Mkdir implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed through to the internal mkdir command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("mkdir", args);
        }
    }

    @Command(name = "rmdir", description = "Remove empty directories (internal LuCLI command)")
    public static class Rmdir implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "DIRS", description = "Directories passed through to the internal rmdir command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("rmdir", args);
        }
    }

    @Command(name = "rm", description = "Remove files or directories (internal LuCLI command)")
    public static class Rm implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed through to the internal rm command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("rm", args);
        }
    }

    @Command(name = "cp", description = "Copy files (internal LuCLI command)")
    public static class Cp implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed through to the internal cp command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("cp", args);
        }
    }

    @Command(name = "mv", description = "Move or rename files (internal LuCLI command)")
    public static class Mv implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed through to the internal mv command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("mv", args);
        }
    }

    @Command(name = "cat", description = "Display file contents (internal LuCLI command)")
    public static class Cat implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "FILES", description = "Files passed through to the internal cat command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("cat", args);
        }
    }

    @Command(name = "head", description = "Show first lines of a file (internal LuCLI command)")
    public static class Head implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed through to the internal head command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("head", args);
        }
    }

    @Command(name = "tail", description = "Show last lines of a file (internal LuCLI command)")
    public static class Tail implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed through to the internal tail command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("tail", args);
        }
    }

    @Command(name = "wc", description = "Count lines, words, characters (internal LuCLI command)")
    public static class Wc implements Callable<Integer> {

        @Parameters(arity = "0..*", paramLabel = "FILES", description = "Files passed through to the internal wc command")
        private List<String> args;

        @Override
        public Integer call() throws Exception {
            return FileSystemCommands.runInternal("wc", args);
        }
    }
}
