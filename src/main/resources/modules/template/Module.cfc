        
component extends="modules.BaseModule" {
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
            // Map CLI verbosity flag into module-scoped verbose flag
            variables.verbose = arguments.verboseEnabled;
            variables.timingEnabled = arguments.timingEnabled;
            variables.cwd = arguments.cwd;
            variables.timer = arguments.timer ?: {};
        // Module initialization code goes here
        return this;
    }
    
    function main() {
        // Main module logic goes here
        out("Hello from {{MODULE_NAME}} module!");
        out("Arguments passed:");
        out(__arguments ?: []);
        return "Module executed successfully";
    }
    
}
        
