package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

/**
 * Covers LuceeServerManager.downloadFile: redirects, HTTP status handling and
 * that failed/incomplete downloads never leave a file behind that looks cached.
 */
class LuceeServerManagerDownloadTest {

    private static final byte[] BODY = "PK-fake-archive-content".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok.zip", exchange -> {
            exchange.sendResponseHeaders(200, BODY.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(BODY);
            }
        });
        server.createContext("/redirect.zip", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl + "/ok.zip");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/missing.zip", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/error.zip", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/truncated.zip", exchange -> {
            // Announce more bytes than we send, then drop the connection
            exchange.sendResponseHeaders(200, BODY.length * 10L);
            OutputStream out = exchange.getResponseBody();
            out.write(BODY);
            out.flush();
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void downloadFile_writesBodyAndLeavesNoPartFile() throws Exception {
        Path target = tempDir.resolve("cache/ok.zip");
        LuceeServerManager.downloadFile(baseUrl + "/ok.zip", target);

        assertArrayEquals(BODY, Files.readAllBytes(target));
        assertFalse(Files.exists(partFile(target)));
    }

    @Test
    void downloadFile_followsRedirects() throws Exception {
        Path target = tempDir.resolve("redirect.zip");
        LuceeServerManager.downloadFile(baseUrl + "/redirect.zip", target);

        assertArrayEquals(BODY, Files.readAllBytes(target));
    }

    @Test
    void downloadFile_404_throwsFileNotFoundAndLeavesNothing() {
        Path target = tempDir.resolve("missing.zip");
        assertThrows(FileNotFoundException.class,
                () -> LuceeServerManager.downloadFile(baseUrl + "/missing.zip", target));

        assertNothingLeft(target);
    }

    @Test
    void downloadFile_serverError_throwsAndLeavesNothing() {
        Path target = tempDir.resolve("error.zip");
        IOException e = assertThrows(IOException.class,
                () -> LuceeServerManager.downloadFile(baseUrl + "/error.zip", target));

        assertTrue(e.getMessage().contains("500"), e.getMessage());
        assertNothingLeft(target);
    }

    @Test
    void downloadFile_truncatedBody_throwsAndLeavesNothing() {
        Path target = tempDir.resolve("truncated.zip");
        assertThrows(IOException.class,
                () -> LuceeServerManager.downloadFile(baseUrl + "/truncated.zip", target));

        assertNothingLeft(target);
    }

    private static Path partFile(Path target) {
        return target.resolveSibling(target.getFileName() + ".part");
    }

    private static void assertNothingLeft(Path target) {
        assertFalse(Files.exists(target), "target must not exist after a failed download");
        assertFalse(Files.exists(partFile(target)), ".part file must be cleaned up");
    }
}
