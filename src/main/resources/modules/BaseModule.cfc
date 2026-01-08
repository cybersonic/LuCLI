component {

    // this component provides base functionality for all modules. Dont modify it as it will be overwritten during module updates.
    static {
       // Existing
        red    = chr(27) & "[31m";
        green  = chr(27) & "[32m";
        yellow = chr(27) & "[33m";
        blue   = chr(27) & "[34m";
        reset  = chr(27) & "[0m";

        // Additional standard colors
        black   = chr(27) & "[30m";
        magenta = chr(27) & "[35m";
        cyan    = chr(27) & "[36m";
        white   = chr(27) & "[37m";

        // Bright variants (high-intensity)
        brightBlack   = chr(27) & "[90m";  // gray
        brightRed     = chr(27) & "[91m";
        brightGreen   = chr(27) & "[92m";
        brightYellow  = chr(27) & "[93m";
        brightBlue    = chr(27) & "[94m";
        brightMagenta = chr(27) & "[95m";
        brightCyan    = chr(27) & "[96m";
        brightWhite   = chr(27) & "[97m";

        // Background colors (standard)
        bgBlack   = chr(27) & "[40m";
        bgRed     = chr(27) & "[41m";
        bgGreen   = chr(27) & "[42m";
        bgYellow  = chr(27) & "[43m";
        bgBlue    = chr(27) & "[44m";
        bgMagenta = chr(27) & "[45m";
        bgCyan    = chr(27) & "[46m";
        bgWhite   = chr(27) & "[47m";

        // Bright backgrounds
        bgBrightBlack   = chr(27) & "[100m";
        bgBrightRed     = chr(27) & "[101m";
        bgBrightGreen   = chr(27) & "[102m";
        bgBrightYellow  = chr(27) & "[103m";
        bgBrightBlue    = chr(27) & "[104m";
        bgBrightMagenta = chr(27) & "[105m";
        bgBrightCyan    = chr(27) & "[106m";
        bgBrightWhite   = chr(27) & "[107m";

        // Text styles (can be combined with colors, e.g., "[1;31m")
        bold      = chr(27) & "[1m";
        dim       = chr(27) & "[2m";
        italic    = chr(27) & "[3m";
        underline = chr(27) & "[4m";
        blink     = chr(27) & "[5m";  // often unsupported
        reverse   = chr(27) & "[7m";
        hidden    = chr(27) & "[8m";
        strike    = chr(27) & "[9m";
    }
    function init(
        boolean verboseEnabled=false,
        boolean timingEnabled=false,
        string cwd="",
        any timer
        ) {
            variables.verboseEnabled = arguments.verboseEnabled;
            variables.timingEnabled = arguments.timingEnabled;
            variables.cwd = arguments.cwd;
            variables.timer = arguments.timer ?: {
                "start": function(){},
                "stop": function(){}
            };
        return this;
    }


    // Helper Functions
    /**
     * output message to the cli. If the message is not a simple value, it will be serialized to JSON first.
     *
     * @message the object or string to output
     */
    private void function out(any message, string colour="", string style=""){
        if(!isSimpleValue(message)){
            message = serializeJson(var=message, compact=false);
        }
        // For CLI usage (including tight loops like watch()), write directly to
        // system output so LuCLI can surface logs immediately.
        // Check the colour and style exists
        colour = static[arguments.colour] ?: "";
        style = static[arguments.style] ?: "";
      
        if(len(colour) || len(style)){
            message = "#style##colour##message##reset#";
        }
        systemOutput(message, true, true);
    }
    // Could add colours here:

    /**
     * output the error to the cli, if the error is not a simple value it will be serialzied to JSON first
     *
     * @message the error message or object to output
     */
    private void function err(any message){
        if(!isSimpleValue(message)){
            message = serializeJson(var=message, compact=false);
        }
        // For CLI usage (including tight loops like watch()), write directly to
        // system output so LuCLI can surface logs immediately.
        systemOutput(message, true, false);
    }

    
    /**
     * Get environment variable from server.env or SERVER.system.environment
     * This should be used as the standard way to get env vars in modules
     *
     * @envKeyName The name of the environment variable
     * @defaultValue The default value to return if the env var is not set
     */
    function getEnv(String envKeyName, String defaultValue=""){
        
        if(structKeyExists(server.env, envKeyName)){
            return server.env[envKeyName];
        }
        elseif (structKeyExists(SERVER.system.environment, envKeyName)) {
            return SERVER.system.environment[envKeyName];
        }
        
        return defaultValue;
    }

    /**
     * Output verbose message if verbose is enabled
     *
     * @message The message to output
     */
    function verbose(any message){
        if(variables.verboseEnabled){
            out(message, "magenta", "italic");
        }
    }

    /**
     * Get absolute path for a given path, relative to cwd if not already absolute
     * This is useful for handling file paths passed into modules
     *
     * @cwd The current working directory
     * @path The path to convert to absolute
     */
    function getAbsolutePath(string cwd, string path){
        var fileObj = createObject("java", "java.io.File");
        
        var targetFile = fileObj.init(path);
        if(!targetFile.isAbsolute()) {
            targetFile = fileObj.init(cwd, path);
        }
        return targetFile.getCanonicalPath();
    }

    /**
     * Execute a LuCLI unified command (server, modules, monitor, …) and
     * return the formatted output string.
     *
     * @command    e.g. "server", "modules", "monitor"
     * @args       CFML array of string args, e.g. ["start","--name","foo"]
     * @projectDir optional project directory (defaults to module cwd)
     */
    public string function executeCommand(
        required string command,
        array args = [],
        string projectDir = ""
    ) {
        // Resolve project dir: explicit > module cwd > current template dir
        var resolvedCwd = len(trim(projectDir))
            ? projectDir
            : (len(trim(variables.cwd)) ? variables.cwd : getDirectoryFromPath(getCurrentTemplatePath()));

        var Paths  = createObject("java", "java.nio.file.Paths");
        var URI = createObject("java", "java.net.URI").init("file:///#resolvedCwd#");
        var cwdPath = Paths.get(URI);


        // Terminal mode = true so ServerCommandHandler returns strings
        var executor = createObject("java", "org.lucee.lucli.server.ServerCommandHandler")
            .init(true, cwdPath);

        var result = executor.executeCommand(command, args);

        // Always return a string (never null)
        return result ?: "";
    }
}