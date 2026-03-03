package org.lucee.lucli.cli.commands;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.modules.ModuleCommand;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lucee.runtime.type.Array;
import lucee.runtime.type.Struct;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Start a per-module MCP server over stdio.
 *
 * Transport:
 * - one JSON-RPC message per line (newline-delimited JSON)
 * - server MUST NOT write non-protocol data to stdout
 * - stderr may be used for logs
 */
@Command(
    name = "mcp",
    description = "Run an MCP server for a module (stdio JSON-RPC)"
)
public class McpCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        arity = "0..1",
        paramLabel = "MODULE_NAME",
        description = "Name of the module to expose via MCP"
    )
    private String moduleName;

    @Option(
        names = {"-m", "--module"},
        description = "Name of the module to expose via MCP (alternative to positional MODULE_NAME)"
    )
    private String moduleOption;

    @Option(
        names = {"--once"},
        paramLabel = "<method>",
        description = "Developer helper: execute a single MCP method (e.g. tools/list) and exit"
    )
    private String onceMethod;

    private final ObjectMapper mapper = new ObjectMapper();

    private boolean initialized = false;
    private String negotiatedProtocolVersion = null;

    @Override
    public Integer call() throws Exception {
        String mod = resolveModuleName();
        if (mod == null || mod.isBlank()) {
            System.err.println("mcp: missing module name. Usage: lucli mcp <module>");
            return 2;
        }

        if (!ModuleCommand.moduleExists(mod)) {
            System.err.println("mcp: module not found: '" + mod + "'");
            System.err.println("Try: lucli modules list");
            return 1;
        }

        // Ensure Lucee engine initialized (module metadata + execution depend on it).
        LuceeScriptEngine.getInstance();

        if (onceMethod != null && !onceMethod.isBlank()) {
            // Minimal debug mode: we simulate a request without starting the server loop.
            // For convenience, assume initialized so `--once tools/list` works.
            this.initialized = true;
            this.negotiatedProtocolVersion = "2025-03-26";

            Map<String, Object> fakeReq = new LinkedHashMap<>();
            fakeReq.put("jsonrpc", "2.0");
            fakeReq.put("id", "once");
            fakeReq.put("method", onceMethod.trim());
            Map<String, Object> resp = handleRequestNode(mapper.valueToTree(fakeReq), mod);
            if (resp != null) {
                System.out.println(mapper.writeValueAsString(resp));
            }
            return 0;
        }

        runStdioServer(mod);
        return 0;
    }

    private String resolveModuleName() {
        if (moduleOption != null && !moduleOption.isBlank()) {
            return moduleOption.trim();
        }
        if (moduleName != null && !moduleName.isBlank()) {
            return moduleName.trim();
        }
        return null;
    }

    private void runStdioServer(String mod) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            JsonNode node;
            try {
                node = mapper.readTree(trimmed);
            } catch (JsonProcessingException e) {
                Map<String, Object> resp = jsonRpcErrorResponse(null, -32700, "Parse error: " + e.getOriginalMessage());
                writeResponse(writer, resp);
                continue;
            }

            // JSON-RPC batch support
            if (node.isArray()) {
                List<Map<String, Object>> responses = new ArrayList<>();
                for (JsonNode item : node) {
                    Map<String, Object> resp = handleRequestNode(item, mod);
                    if (resp != null) {
                        responses.add(resp);
                    }
                }
                if (!responses.isEmpty()) {
                    writeResponse(writer, responses);
                }
            } else {
                Map<String, Object> resp = handleRequestNode(node, mod);
                if (resp != null) {
                    writeResponse(writer, resp);
                }
            }
        }
    }

    private void writeResponse(BufferedWriter writer, Object responseObj) {
        try {
            writer.write(mapper.writeValueAsString(responseObj));
            writer.write("\n");
            writer.flush();
        } catch (Exception e) {
            // If we can't write to stdout, we're effectively dead; just stop.
            if (LuCLI.debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle a single JSON-RPC request node. Returns null for notifications.
     */
    private Map<String, Object> handleRequestNode(JsonNode node, String mod) {
        if (node == null || !node.isObject()) {
            return jsonRpcErrorResponse(null, -32600, "Invalid Request: expected JSON object");
        }

        JsonNode methodNode = node.get("method");
        String method = methodNode != null ? methodNode.asText() : null;

        JsonNode idNode = node.get("id");
        Object id = jsonNodeToJavaId(idNode);

        // Notifications have no id; no response.
        boolean isNotification = (idNode == null || id == null);

        if (method == null || method.isBlank()) {
            return isNotification ? null : jsonRpcErrorResponse(id, -32600, "Invalid Request: missing method");
        }

        try {
            switch (method) {
                case "initialize":
                    Map<String, Object> result = handleInitialize(node.get("params"));
                    initialized = true;
                    return isNotification ? null : jsonRpcResultResponse(id, result);

                case "notifications/initialized":
                    // No response (notification)
                    initialized = true;
                    return null;

                case "ping":
                    return isNotification ? null : jsonRpcResultResponse(id, new LinkedHashMap<>());

                case "tools/list":
                    if (!initialized) {
                        return isNotification ? null : jsonRpcErrorResponse(id, -32002, "Server not initialized");
                    }
                    Map<String, Object> toolsList = new LinkedHashMap<>();
                    toolsList.put("tools", listToolsForModule(mod));
                    return isNotification ? null : jsonRpcResultResponse(id, toolsList);

                case "tools/call":
                    if (!initialized) {
                        return isNotification ? null : jsonRpcErrorResponse(id, -32002, "Server not initialized");
                    }
                    Map<String, Object> callResult = handleToolsCall(mod, node.get("params"));
                    return isNotification ? null : jsonRpcResultResponse(id, callResult);

                default:
                    return isNotification ? null : jsonRpcErrorResponse(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            if (LuCLI.debug) {
                e.printStackTrace();
            }
            return isNotification ? null : jsonRpcErrorResponse(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    private Map<String, Object> handleInitialize(JsonNode params) {
        String requestedVersion = null;
        if (params != null && params.isObject()) {
            JsonNode pv = params.get("protocolVersion");
            if (pv != null && pv.isTextual()) {
                requestedVersion = pv.asText();
            }
        }
        negotiatedProtocolVersion = requestedVersion != null ? requestedVersion : "2025-03-26";

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "lucli");
        serverInfo.put("version", LuCLI.getVersion());

        Map<String, Object> toolsCaps = new LinkedHashMap<>();
        toolsCaps.put("listChanged", false);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", toolsCaps);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", negotiatedProtocolVersion);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);

        return result;
    }

    private List<Map<String, Object>> listToolsForModule(String mod) throws Exception {
        Array meta = LuceeScriptEngine.getInstance().getComponentMetadata("modules." + mod + ".Module");

        List<Map<String, Object>> tools = new ArrayList<>();
        for (int i = 1; i <= meta.size(); i++) {
            Object itemObj = meta.get(i, null);
            if (!(itemObj instanceof Struct)) {
                continue;
            }
            Struct fn = (Struct) itemObj;

            String name = getString(fn, "name");
            if (name == null || name.isBlank()) {
                continue;
            }

            String hint = getString(fn, "hint");
            String description = firstLine(hint);

            Map<String, Object> inputSchema = buildInputSchema(fn);

            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", name);
            if (description != null && !description.isBlank()) {
                tool.put("description", description);
            }
            tool.put("inputSchema", inputSchema);

            tools.add(tool);
        }

        return tools;
    }

    private Map<String, Object> buildInputSchema(Struct fn) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        Object paramsObj = fn.get("parameters", null);
        if (paramsObj instanceof Array) {
            Array params = (Array) paramsObj;
            for (int j = 1; j <= params.size(); j++) {
                Object pObj = params.get(j, null);
                if (!(pObj instanceof Struct)) {
                    continue;
                }
                Struct p = (Struct) pObj;

                String pName = getString(p, "name");
                if (pName == null || pName.isBlank()) {
                    continue;
                }

                String cfType = getString(p, "type");
                String jsonType = mapCfmlTypeToJsonType(cfType);

                Map<String, Object> pSchema = new LinkedHashMap<>();
                pSchema.put("type", jsonType);

                String pHint = getString(p, "hint");
                if (pHint != null && !pHint.isBlank()) {
                    pSchema.put("description", firstLine(pHint));
                }

                Object def = p.get("default", null);
                if (def != null) {
                    // Only include JSON-serializable defaults; if not, just omit.
                    if (def instanceof String || def instanceof Number || def instanceof Boolean) {
                        pSchema.put("default", def);
                    }
                }

                boolean isRequired = getBoolean(p, "required");
                if (isRequired) {
                    required.add(pName);
                }

                properties.put(pName, pSchema);
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        // We only allow named args defined by the function signature.
        schema.put("additionalProperties", false);

        return schema;
    }

    private Map<String, Object> handleToolsCall(String mod, JsonNode params) throws Exception {
        if (params == null || !params.isObject()) {
            return toolCallError("Invalid params: expected object");
        }

        JsonNode nameNode = params.get("name");
        if (nameNode == null || !nameNode.isTextual()) {
            return toolCallError("Invalid params: missing tool name");
        }

        String toolName = nameNode.asText();
        if (toolName == null || toolName.isBlank()) {
            return toolCallError("Invalid tool name");
        }

        // Validate that toolName exists in the current tools list.
        boolean toolExists = false;
        for (Map<String, Object> t : listToolsForModule(mod)) {
            Object n = t.get("name");
            if (toolName.equals(n)) {
                toolExists = true;
                break;
            }
        }
        if (!toolExists) {
            return toolCallError("Unknown tool: " + toolName);
        }

        JsonNode argsNode = params.get("arguments");
        Map<String, Object> args = new LinkedHashMap<>();
        if (argsNode != null && argsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = argsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                args.put(entry.getKey(), jsonNodeToJavaValue(entry.getValue()));
            }
        }

        String output;
        boolean isError = false;
        try {
            output = executeModuleTool(mod, toolName, args);
        } catch (Exception e) {
            isError = true;
            output = (e.getMessage() != null ? e.getMessage() : e.toString());
        }

        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("type", "text");
        contentItem.put("text", output != null ? output : "");

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(contentItem);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        if (isError) {
            result.put("isError", true);
        }

        return result;
    }

    private String executeModuleTool(String mod, String toolName, Map<String, Object> args) throws Exception {
        List<String> argv = new ArrayList<>();
        argv.add(toolName);
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null || key.isBlank()) {
                continue;
            }

            String stringValue;
            if (value == null) {
                stringValue = "";
            } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                stringValue = value.toString();
            } else {
                // For objects/arrays, send JSON as a string.
                stringValue = mapper.writeValueAsString(value);
            }

            argv.add(key + "=" + stringValue);
        }

        // Capture ALL output produced by the module so we don't contaminate the
        // MCP protocol stream (stdout).
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        StringOutput stringOutput = StringOutput.getInstance();
        PrintStream originalStringOut = stringOutput.getOutputStream();
        PrintStream originalStringErr = stringOutput.getErrorStream();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PrintStream capture = new PrintStream(baos, true, StandardCharsets.UTF_8);
            System.setOut(capture);
            System.setErr(capture);
            stringOutput.setOutputStream(capture);
            stringOutput.setErrorStream(capture);

            LuceeScriptEngine.getInstance().executeModule(mod, argv.toArray(new String[0]));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            stringOutput.setOutputStream(originalStringOut);
            stringOutput.setErrorStream(originalStringErr);
        }

        return baos.toString(StandardCharsets.UTF_8);
    }

    private Map<String, Object> toolCallError(String message) {
        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("type", "text");
        contentItem.put("text", message);

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(contentItem);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("isError", true);
        return result;
    }

    private Map<String, Object> jsonRpcResultResponse(Object id, Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        return resp;
    }

    private Map<String, Object> jsonRpcErrorResponse(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", err);
        return resp;
    }

    private Object jsonNodeToJavaId(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isTextual()) {
            return idNode.asText();
        }
        if (idNode.isNumber()) {
            return idNode.numberValue();
        }
        // JSON-RPC ids can technically be string/number/null. Fall back to string.
        return idNode.toString();
    }

    private Object jsonNodeToJavaValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isObject() || node.isArray()) {
            return mapper.convertValue(node, Object.class);
        }
        return node.asText();
    }

    private String getString(Struct s, String key) {
        if (s == null || key == null) return null;
        Object v = s.get(key, null);
        return v != null ? v.toString() : null;
    }

    private boolean getBoolean(Struct s, String key) {
        if (s == null || key == null) return false;
        Object v = s.get(key, null);
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    private String firstLine(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return "";
        int idx = t.indexOf('\n');
        if (idx >= 0) {
            return t.substring(0, idx).trim();
        }
        idx = t.indexOf('\r');
        if (idx >= 0) {
            return t.substring(0, idx).trim();
        }
        return t;
    }

    private String mapCfmlTypeToJsonType(String cfType) {
        if (cfType == null) return "string";
        String t = cfType.trim().toLowerCase();
        if (t.isEmpty() || "any".equals(t)) return "string";

        switch (t) {
            case "string":
                return "string";
            case "boolean":
            case "bool":
                return "boolean";
            case "numeric":
            case "number":
            case "int":
            case "integer":
            case "long":
            case "double":
            case "float":
                return "number";
            case "array":
                return "array";
            case "struct":
            case "object":
                return "object";
            default:
                // Most LuCLI args are ultimately strings; be permissive.
                return "string";
        }
    }
}
