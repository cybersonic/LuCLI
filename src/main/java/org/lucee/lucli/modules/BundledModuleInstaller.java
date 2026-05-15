package org.lucee.lucli.modules;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Installs modules bundled in the JAR under {@code /modules-install} into the
 * active profile modules directory (for example {@code ~/.markspresso/modules})
 * on first startup.
 */
public final class BundledModuleInstaller {

    private static final String BUNDLED_MODULES_RESOURCE = "/modules-install";
    private static volatile boolean installedThisProcess = false;

    private BundledModuleInstaller() {
    }

    public static synchronized void ensureBundledModulesInstalled() throws IOException {
        if (installedThisProcess) {
            return;
        }
        installedThisProcess = true;

        URL modulesUrl = BundledModuleInstaller.class.getResource(BUNDLED_MODULES_RESOURCE);
        if (modulesUrl == null) {
            return;
        }

        Path modulesDir = ModuleCommand.getModulesDirectory();
        try {
            installFromResourceUrl(modulesUrl, modulesDir);
        } catch (URISyntaxException e) {
            throw new IOException("Failed to resolve bundled modules resource URI", e);
        }
    }

    static void resetForTests() {
        installedThisProcess = false;
    }

    static void installBundledModulesFromPath(Path bundledModulesRoot, Path modulesDir) throws IOException {
        if (bundledModulesRoot == null || !Files.exists(bundledModulesRoot) || !Files.isDirectory(bundledModulesRoot)) {
            return;
        }

        Files.createDirectories(modulesDir);

        try (Stream<Path> stream = Files.list(bundledModulesRoot)) {
            stream
                .filter(Files::isDirectory)
                .forEach(sourceModuleDir -> {
                    String moduleName = sourceModuleDir.getFileName().toString();
                    Path targetModuleDir = modulesDir.resolve(moduleName);
                    Path targetModuleFile = targetModuleDir.resolve("Module.cfc");
                    if (Files.exists(targetModuleFile)) {
                        return;
                    }
                    try {
                        copyDirectory(sourceModuleDir, targetModuleDir);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to install bundled module '" + moduleName + "'", e);
                    }
                });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    private static void installFromResourceUrl(URL modulesUrl, Path modulesDir) throws IOException, URISyntaxException {
        URI uri = modulesUrl.toURI();
        String scheme = uri.getScheme();

        if ("file".equalsIgnoreCase(scheme)) {
            installBundledModulesFromPath(Paths.get(uri), modulesDir);
            return;
        }

        if ("jar".equalsIgnoreCase(scheme)) {
            FileSystem fs = null;
            boolean created = false;
            try {
                try {
                    fs = FileSystems.newFileSystem(uri, Map.of());
                    created = true;
                } catch (FileSystemAlreadyExistsException ignored) {
                    fs = FileSystems.getFileSystem(uri);
                }
                Path root = fs.getPath(BUNDLED_MODULES_RESOURCE);
                installBundledModulesFromPath(root, modulesDir);
            } finally {
                if (created && fs != null && fs.isOpen()) {
                    fs.close();
                }
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path destination = target;
                    for (Path part : relative) {
                        destination = destination.resolve(part.toString());
                    }
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}
