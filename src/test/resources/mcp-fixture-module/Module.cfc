component extends="modules.BaseModule" {

    /**
     * hint: Emit a known string via out() — used to test MCP output capture.
     */
    public string function echo() {
        out("hello from echo");
        return "";
    }

    /**
     * hint: Emit via err() — used to test error-stream capture.
     */
    public string function boom() {
        err("hello from boom");
        return "";
    }

    /**
     * hint: Public tool that should appear in MCP tools/list.
     * @subject Thing to greet
     */
    public string function greet(string subject = "world") {
        out("hello, " & arguments.subject);
        return "";
    }

    /**
     * hint: Public tool that should be hidden from MCP tools/list via mcpHiddenTools().
     */
    public string function secret() {
        out("should not be reachable via MCP");
        return "";
    }

    /**
     * hint: Declares which tools to hide from MCP discovery.
     */
    public array function mcpHiddenTools() {
        return ["secret"];
    }

    /**
     * hint: Declares per-tool MCP input schemas (mcpToolSpecs convention).
     * The greet entry's description deliberately differs from the @subject
     * doc hint so tests can prove the DECLARED schema beat the
     * signature-derived one.
     */
    public struct function mcpToolSpecs() {
        return {
            "greet" = {
                "type" = "object",
                "properties" = {
                    "subject" = {
                        "type" = "string",
                        "description" = "Greeting target (declared via mcpToolSpecs)",
                        "default" = "world"
                    }
                },
                "required" = [],
                "additionalProperties" = false
            }
        };
    }

}
