package org.lucee.lucli.cli.commands;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.lucee.lucli.StringOutput;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.secrets.LocalSecretStore;
import org.lucee.lucli.secrets.SecretStore;
import org.lucee.lucli.secrets.SecretStoreException;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Secrets management CLI entrypoint.
 */
@Command(
    name = "secrets",
    description = "Manage secrets for LuCLI projects",
    mixinStandardHelpOptions = true,
    subcommands = {
        SecretsCommand.InitCommand.class,
        SecretsCommand.SetCommand.class,
        SecretsCommand.ListCommand.class,
        SecretsCommand.RmCommand.class,
        SecretsCommand.GetCommand.class,
        SecretsCommand.ProviderCommand.class
    }
)
public class SecretsCommand implements Callable<Integer> {

    @ParentCommand
    private LuCLI parent;

    @Override
    public Integer call() throws Exception {
        // Show help when no subcommand is provided
        new picocli.CommandLine(this).usage(System.out);
        return 0;
    }

    static Path getDefaultStorePath() {
        String home = System.getProperty("lucli.home");
        if (home == null) home = System.getenv("LUCLI_HOME");
        if (home == null) home = System.getProperty("user.home") + "/.lucli";
        return Paths.get(home).resolve("secrets").resolve("local.json");
    }

    static char[] readPassphrase(char[] provided) throws Exception {
        if (provided != null && provided.length > 0) {
            return provided;
        }
        java.io.Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("No console available for secure passphrase input");
        }
        char[] first = console.readPassword("Enter secrets passphrase: ");
        return first;
    }

    static LocalSecretStore createStore(char[] passphrase) throws SecretStoreException {
        return new LocalSecretStore(getDefaultStorePath(), passphrase);
    }

    @Command(name = "init", description = "Initialize the local encrypted secret store")
    static class InitCommand implements Callable<Integer> {

        @ParentCommand
        private SecretsCommand parent;

        @Option(names = "--reset", description = "Re-initialize the store (DANGEROUS: deletes existing secrets)")
        private boolean reset;

        @Override
        public Integer call() throws Exception {
            Path storePath = getDefaultStorePath();
            if (java.nio.file.Files.exists(storePath) && !reset) {
                System.err.println("Secret store already exists at " + storePath + ". Use --reset to recreate it (will delete existing secrets).");
                return 1;
            }
            java.io.Console console = System.console();
            if (console == null) {
                System.err.println("No console available to read passphrase securely.");
                return 1;
            }
            char[] p1 = console.readPassword("Create secrets passphrase: ");
            char[] p2 = console.readPassword("Confirm secrets passphrase: ");
            if (!java.util.Arrays.equals(p1, p2)) {
                System.err.println("Passphrases do not match.");
                return 1;
            }
            try {
                // Creating a new store will initialize and persist using the passphrase
                new LocalSecretStore(storePath, p1);
                System.out.println("Initialized local secret store at " + storePath);
                return 0;
            } catch (SecretStoreException e) {
                System.err.println("Failed to initialize secret store: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "set", description = "Set or update a secret value")
    static class SetCommand implements Callable<Integer> {

        @ParentCommand
        private SecretsCommand parent;

        @Parameters(paramLabel = "NAME", description = "Logical name of the secret")
        private String name;

        @Option(names = "--description", description = "Description for this secret")
        private String description;

        @Override
        public Integer call() throws Exception {
            java.io.Console console = System.console();
            if (console == null) {
                System.err.println("No console available to read secret value securely.");
                return 1;
            }
            char[] passphrase = readPassphrase(null);
            char[] value = console.readPassword("Enter secret value for '%s': ", name);
            try {
                SecretStore store = createStore(passphrase);
                store.put(name, value, description);
                System.out.println("Stored secret '" + name + "'.");
                return 0;
            } catch (SecretStoreException e) {
                System.err.println("Failed to store secret: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "list", description = "List stored secrets (names and metadata only)")
    static class ListCommand implements Callable<Integer> {

        @ParentCommand
        private SecretsCommand parent;

        @Override
        public Integer call() throws Exception {
            char[] passphrase = readPassphrase(null);
            try {
                SecretStore store = createStore(passphrase);
                List<SecretStore.SecretMetadata> all = store.list();
                if (all.isEmpty()) {
                    System.out.println("No secrets stored yet.");
                    return 0;
                }
                for (SecretStore.SecretMetadata meta : all) {
                    System.out.println("- " + meta.name() +
                        (meta.description() != null && !meta.description().isEmpty() ? " : " + meta.description() : ""));
                }
                return 0;
            } catch (SecretStoreException e) {
                System.err.println("Failed to list secrets: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "rm", description = "Remove a stored secret")
    static class RmCommand implements Callable<Integer> {

        @ParentCommand
        private SecretsCommand parent;

        @Parameters(paramLabel = "NAME", description = "Logical name of the secret to remove")
        private String name;

        @Option(names = "-f", description = "Do not prompt for confirmation")
        private boolean force;

        @Override
        public Integer call() throws Exception {
            if (!force) {
                java.io.Console console = System.console();
                if (console == null) {
                    System.err.println("No console to confirm deletion. Use -f to force.");
                    return 1;
                }
                String answer = console.readLine("Delete secret '%s'? (y/N): ", name);
                if (answer == null || !answer.trim().toLowerCase().startsWith("y")) {
                    System.out.println("Aborted.");
                    return 0;
                }
            }
            char[] passphrase = readPassphrase(null);
            try {
                SecretStore store = createStore(passphrase);
                store.delete(name);
                System.out.println("Removed secret '" + name + "'.");
                return 0;
            } catch (SecretStoreException e) {
                System.err.println("Failed to remove secret: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Retrieve a secret value")
    static class GetCommand implements Callable<Integer> {

        @ParentCommand
        private SecretsCommand parent;

        @Parameters(paramLabel = "NAME", description = "Logical name of the secret")
        private String name;

        @Option(names = "--show", description = "Print the secret value to stdout (use with care)")
        private boolean show;

        @Override
        public Integer call() throws Exception {
            if (!show) {
                System.err.println("By default, 'lucli secrets get' does not print raw values. Use --show if you really need to see it.");
                return 1;
            }
            char[] passphrase = readPassphrase(null);
            try {
                SecretStore store = createStore(passphrase);
                Optional<char[]> value = store.get(name);
                if (value.isEmpty()) {
                    System.err.println("Secret '" + name + "' not found.");
                    return 1;
                }
                // Intentionally print directly without additional formatting to reduce risk of logs copying
                System.out.println(new String(value.get()));
                return 0;
            } catch (SecretStoreException e) {
                System.err.println("Failed to retrieve secret: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "provider", description = "Manage secret providers")
    static class ProviderCommand implements Callable<Integer> {

        @ParentCommand
        private SecretsCommand parent;

        @Override
        public Integer call() throws Exception {
            new picocli.CommandLine(this).usage(System.out);
            return 0;
        }

        @Command(name = "list", description = "List available secret providers")
        public int listProviders() {
            System.out.println("Available secret providers:");
            System.out.println("- local (encrypted file under ~/.lucli/secrets/local.json)");
            System.out.println();
            System.out.println("More providers coming soon.");
            return 0;
        }
    }
}