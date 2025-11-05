        
component {
    /**
     * {{MODULE_NAME}} Module
     * 
     * This is the main entry point for your module.
     * Implement your module logic in the main() function.
     */
    
    function init(
        verboseEnabled=false,
        timingEnabled=false,
        cwd="",
        timer=nullValue()
        ) {
            variables.verbose = arguments.verbose;
            variables.timingEnabled = arguments.timingEnabled;
            variables.cwd = arguments.cwd;
            variables.timer = arguments.timer ?: {};
        // Module initialization code goes here
        return this;
    }
    
    function main(string myArgument="") {
        // Main module logic goes here
        out("Hello from {{MODULE_NAME}} module!");
        out("Arguments passed:");
        out(arguments);

        for(var argName in arguments){
            out("  " & argName & ": " & arguments[argName]);
        }
        return "Module executed successfully";
    }
    
    // Helper Functions
    function out(any message){
        if(!isSimpleValue(message)){
            message = serializeJson(var=message, compact=false);
        }
        writeOutput(message & chr(10));
    }

    function getEnv(String envKeyName, String defaultValue=""){
        var envValue = SERVER.system.environment[envKeyName];
        if(isNull(envValue)){
            return defaultValue;
        }
        return envValue;
    }

    function verbose(any message){
        if(variables.verbose){
            out(message);
        }
    }
}
        