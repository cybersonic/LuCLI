component {

    // this component provides base functionality for all modules. Dont modify it as it will be overwritten during module updates.

    function init(
        boolean verboseEnabled=false,
        boolean timingEnabled=false,
        string cwd="",
        any timer
        ) {
            variables.verboseEnabled = arguments.verboseEnabled;
            variables.timingEnabled = arguments.timingEnabled;
            variables.cwd = arguments.cwd;
            variables.timer = arguments.timer ?: {};
        return this;
    }


    // Helper Functions
    /**
     * output message to the cli. If the message is not a simple value, it will be serialized to JSON first.
     *
     * @message the object or string to output
     */
    private void function out(any message){
        if(!isSimpleValue(message)){
            message = serializeJson(var=message, compact=false);
        }
        writeOutput(message & chr(10));
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
            out(message);
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
}