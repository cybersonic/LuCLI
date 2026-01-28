package org.lucee.lucli.cli.commands;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;

import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Minimal LuCLI daemon MVP.
 *
 * Listens on a local TCP port and accepts a single JSON line per connection:
 *   {"argv":["modules","list"]}
 *
 * For each request it executes the given argv through the normal Picocli
 * command pipeline and returns a JSON response with the combined stdout/stderr
 * and exit code:
 *   {"exitCode":0,"output":"..."}
 *
 * This implementation is intentionally simple:
 * - Localhost only (127.0.0.1)
 * - One request per connection (no multiplexing)
 * - Output is buffered per-request (not streaming yet)
 */
@Command(
    name = "daemon",
    description = "Run LuCLI in daemon mode, listening for JSON commands over TCP"
)
public class DaemonCommand implements Callable<Integer> {

    @Option(names = "--port", description = "Port to listen on", defaultValue = "10000")
    private int port;

    @Override
    public Integer call() throws Exception {
        LuCLI.printInfo("Starting LuCLI daemon on 127.0.0.1:" + port + " ...");

        try (ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            LuCLI.printInfo("LuCLI daemon listening on 127.0.0.1:" + port);

            // Simple, single-threaded loop: handle one connection at a time.
            while (true) {
                try (Socket client = server.accept()) {
                    handleClient(client);
                } catch (IOException e) {
                    LuCLI.printError("Daemon client error: " + e.getMessage());
                    if (LuCLI.debug) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void handleClient(Socket client) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));

        String line = reader.readLine();
        if (line == null || line.trim().isEmpty()) {
            // Nothing to do; close connection.
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        DaemonRequest request;
        try {
            request = mapper.readValue(line, DaemonRequest.class);
        } catch (Exception e) {
            writeErrorResponse(writer, 1, "Invalid JSON request: " + e.getMessage());
            return;
        }

        if (request.argv == null || request.argv.length == 0) {
            writeErrorResponse(writer, 1, "Request must contain non-empty 'argv' array");
            return;
        }

        // Capture stdout/stderr for this request only.
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int exitCode;
        try {
            PrintStream capture = new PrintStream(baos, true, StandardCharsets.UTF_8);
            System.setOut(capture);
            System.setErr(capture);

            // Reuse the main Picocli command pipeline without System.exit().
            picocli.CommandLine cmd = new picocli.CommandLine(new org.lucee.lucli.cli.LuCLICommand());
            exitCode = cmd.execute(request.argv);
        } catch (Exception e) {
            exitCode = 1;
            baos.writeBytes(("Daemon execution error: " + e.getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
            if (LuCLI.debug) {
                e.printStackTrace();
            }
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String output = baos.toString(StandardCharsets.UTF_8);
        DaemonResponse response = new DaemonResponse();
        response.id = request.id;
        response.exitCode = exitCode;
        response.output = output;

        mapper.writeValue(writer, response);
        writer.write("\n");
        writer.flush();
    }

    private void writeErrorResponse(BufferedWriter writer, int exitCode, String message) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        DaemonResponse response = new DaemonResponse();
        response.exitCode = exitCode;
        response.output = message;
        mapper.writeValue(writer, response);
        writer.write("\n");
        writer.flush();
    }

    // Simple DTOs for JSON mapping
    public static class DaemonRequest {
        public String id;
        public String[] argv;
    }

    public static class DaemonResponse {
        public String id;
        public int exitCode;
        public String output;
    }
}
