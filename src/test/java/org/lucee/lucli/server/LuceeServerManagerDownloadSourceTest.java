package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Covers LuceeServerManager.resolveDownloadUrl: primary source (GitHub
 * Releases / Maven Central) first, cdn.lucee.org fallback when the primary
 * doesn't have the file.
 */
class LuceeServerManagerDownloadSourceTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok.zip", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
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
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void usesPrimaryWhenAvailable() {
        assertEquals(baseUrl + "/ok.zip",
                LuceeServerManager.resolveDownloadUrl(baseUrl + "/ok.zip", baseUrl + "/fallback.zip"));
    }

    @Test
    void usesPrimaryWhenItRedirectsToAnAvailableFile() {
        assertEquals(baseUrl + "/redirect.zip",
                LuceeServerManager.resolveDownloadUrl(baseUrl + "/redirect.zip", baseUrl + "/fallback.zip"));
    }

    @Test
    void fallsBackWhenPrimaryIsMissing() {
        assertEquals(baseUrl + "/ok.zip",
                LuceeServerManager.resolveDownloadUrl(baseUrl + "/missing.zip", baseUrl + "/ok.zip"));
    }

    @Test
    void returnsLastCandidateWhenNothingIsAvailable() {
        assertEquals(baseUrl + "/missing-too.zip",
                LuceeServerManager.resolveDownloadUrl(baseUrl + "/missing.zip", baseUrl + "/missing-too.zip"));
    }

    @Test
    void fallsBackWhenPrimaryIsUnreachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        assertEquals(baseUrl + "/ok.zip",
                LuceeServerManager.resolveDownloadUrl("http://127.0.0.1:" + closedPort + "/x.zip", baseUrl + "/ok.zip"));
    }
}
