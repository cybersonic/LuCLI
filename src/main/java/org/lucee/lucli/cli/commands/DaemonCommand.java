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
import java.util.Arrays;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.modules.ModuleCommand;

import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * LuCLI daemon.
 *
 * Modes:
 * - Default JSON daemon: one JSON line per connection
 *   {"argv":["modules","list"]} → executes Picocli pipeline and returns
 *   {"exitCode":0,"output":"..."}
 *
 * - LSP daemon: Language Server Protocol over TCP using a CFML module
 *   lucli daemon --lsp --module LuceeLSP
 */
@Command(
    name = "daemon",
    description = "Run LuCLI in daemon mode (JSON or LSP)"
)
public class DaemonCommand implements Callable<Integer> {

    @Option(names = "--port", description = "Port to listen on", defaultValue = "10000")
    private int port;

    @Option(names = "--lsp", description = "Run daemon in Language Server Protocol (LSP) mode")
    private boolean lspMode;

    @Option(names = "--module", description = "CFML module to use as LSP endpoint (e.g. LuceeLSP)")
    private String lspModuleName = "LuceeLSP";

    @Override
    public Integer call() throws Exception {
        if (lspMode) {
            return runLspDaemon();
        }
        return runJsonDaemon();
    }

    /** JSON daemon mode (existing behaviour) */
    private Integer runJsonDaemon() throws Exception {
        LuCLI.info("Starting LuCLI JSON daemon on 127.0.0.1:" + port + " ...");

        try (ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            LuCLI.info("LuCLI JSON daemon listening on 127.0.0.1:" + port);

            // Simple, single-threaded loop: handle one connection at a time.
            while (true) {
                try (Socket client = server.accept()) {
                    if (LuCLI.debug) {
                        LuCLI.debug("Daemon", "Accepted connection from " + client.getRemoteSocketAddress());
                    }
                    handleJsonClient(client);
                } catch (IOException e) {
                    String msg = e.getMessage();
                    boolean isStreamClosed = msg != null && msg.contains("Stream closed");
                    if (!isStreamClosed) {
                        LuCLI.error("Daemon client error: " + msg);
                    }
                    if (LuCLI.debug) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void handleJsonClient(Socket client) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));

        String line = reader.readLine();
        if (line == null || line.trim().isEmpty()) {
            // Nothing to do; close connection.
            if (LuCLI.debug) {
                LuCLI.debug("Daemon", "Received empty request from " + client.getRemoteSocketAddress());
            }
            return;
        }

        if (LuCLI.debug) {
            LuCLI.debug("Daemon", "Raw request from " + client.getRemoteSocketAddress() + ": " + line);
        }

        ObjectMapper mapper = new ObjectMapper();
        DaemonRequest request;
        try {
            request = mapper.readValue(line, DaemonRequest.class);
        } catch (Exception e) {
            if (LuCLI.debug) {
                LuCLI.debug("Daemon", "Failed to parse JSON request: " + e.getMessage());
            }
            writeJsonErrorResponse(writer, 1, "Invalid JSON request: " + e.getMessage());
            return;
        }

        if (request.argv == null || request.argv.length == 0) {
            writeJsonErrorResponse(writer, 1, "Request must contain non-empty 'argv' array");
            return;
        }

        if (LuCLI.debug) {
            LuCLI.debug("Daemon", "Executing request id=" + request.id + " argv=" + Arrays.toString(request.argv));
        }

        // Capture stdout/stderr for this request only (including StringOutput).
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        StringOutput stringOutput = StringOutput.getInstance();
        PrintStream originalStringOut = stringOutput.getOutputStream();
        PrintStream originalStringErr = stringOutput.getErrorStream();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int exitCode;
        try {
            PrintStream capture = new PrintStream(baos, true, StandardCharsets.UTF_8);
            System.setOut(capture);
            System.setErr(capture);
            stringOutput.setOutputStream(capture);
            stringOutput.setErrorStream(capture);

            // Reuse the main Picocli command pipeline without System.exit().
            picocli.CommandLine cmd = new picocli.CommandLine(new org.lucee.lucli.LuCLI());
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
            stringOutput.setOutputStream(originalStringOut);
            stringOutput.setErrorStream(originalStringErr);
        }

        String output = baos.toString(StandardCharsets.UTF_8);

        if (LuCLI.debug) {
            LuCLI.debug("Daemon", "Command exitCode=" + exitCode + ", output:\n" + output);
        }

        DaemonResponse response = new DaemonResponse();
        response.id = request.id;
        response.exitCode = exitCode;
        response.output = output;

        if (LuCLI.debug) {
            try {
                LuCLI.debug("Daemon", "Sending response: " + mapper.writeValueAsString(response));
            } catch (Exception ignored) {
                // Ignore debug serialization errors
            }
        }

        mapper.writeValue(writer, response);
        writer.write("\n");
        writer.flush();
    }

    private void writeJsonErrorResponse(BufferedWriter writer, int exitCode, String message) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        DaemonResponse response = new DaemonResponse();
        response.exitCode = exitCode;
        response.output = message;
        mapper.writeValue(writer, response);
        writer.write("\n");
        writer.flush();
    }

    /** LSP daemon mode: minimal echo implementation via a CFML module. */
    private Integer runLspDaemon() throws Exception {
        LuCLI.info("Starting LuCLI LSP daemon on 127.0.0.1:" + port + " using module '" + lspModuleName + "' ...");

        // Ensure Lucee engine + directories are initialized so modules can run.
        LuceeScriptEngine.getInstance();

        if (!ModuleCommand.moduleExists(lspModuleName)) {
            LuCLI.error("LSP module '" + lspModuleName + "' not found under ~/.lucli/modules.");
            LuCLI.error("Install or create it with 'lucli modules init " + lspModuleName + "'.");
            return 1;
        }

        try (ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            LuCLI.info("LuCLI LSP daemon listening on 127.0.0.1:" + port);

            while (true) {
                Socket client = server.accept();
                if (LuCLI.debug) {
                    LuCLI.debug("LSP", "Accepted connection from " + client.getRemoteSocketAddress());
                }
                // Handle client in-place (single-threaded for now)
                try {
                    handleLspClient(client);
                } catch (IOException ioe) {
                    if (LuCLI.debug) {
                        ioe.printStackTrace();
                    }
                } finally {
                    try {
                        client.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    /**
     * Very small LSP framing implementation:
     * - Reads Content-Length header
     * - Reads that many bytes
     * - Passes JSON to CFML module main(message, context)
     * - Echo implementation: module can just return the message or a struct.
     */
    private void handleLspClient(Socket client) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));

        while (true) {
            // Read headers until blank line
            String line;
            int contentLength = -1;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String header = line.trim();
                if (header.toLowerCase().startsWith("content-length:")) {
                    String value = header.substring("content-length:".length()).trim();
                    try {
                        contentLength = Integer.parseInt(value);
                    } catch (NumberFormatException nfe) {
                        LuCLI.error("Invalid Content-Length header: " + header);
                        return;
                    }
                }
            }

            if (contentLength <= 0) {
                // No more messages or invalid header; end client session
                return;
            }

            char[] buf = new char[contentLength];
            int read = 0;
            while (read < contentLength) {
                int r = reader.read(buf, read, contentLength - read);
                if (r == -1) {
                    return; // client closed
                }
                read += r;
            }

            String json = new String(buf, 0, read);
            if (LuCLI.debug) {
                LuCLI.debug("LSP", "Received LSP message: " + json);
            }

            // Pass raw JSON text into the CFML module as a named argument
            // "message" so modules can declare main(string message) and
            // remain completely in control of how it is parsed/handled.
            String[] moduleArgs = new String[] { "message=" + json };

            String responseJson = null;
            try {
                LuceeScriptEngine engineWrapper = LuceeScriptEngine.getInstance();
                // Execute the module; its return value is stored in the
                // standard "results" variable by executeModule.cfs.
                engineWrapper.executeModule(lspModuleName, moduleArgs);
                Object cfResult = engineWrapper.getEngine().get("results");
                if (cfResult != null) {
                    responseJson = cfResult.toString();
                }
            } catch (Exception e) {
                LuCLI.error("Error executing LSP module '" + lspModuleName + "': " + e.getMessage());
                if (LuCLI.debug) {
                    e.printStackTrace();
                }
            }

            // If the CFML side returned a JSON string, send it back using LSP
            // framing. Notifications (no id) will generally return null.
            if (responseJson != null && !responseJson.isEmpty()) {
                String header = "Content-Length: " + responseJson.length() + "\r\n\r\n";
                writer.write(header);
                writer.write(responseJson);
                writer.flush();
            }
        }
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
