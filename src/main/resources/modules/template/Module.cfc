        
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
        timer=nullValue(),
        moduleConfig={},
        envVars={},
        secrets={},
        runtimeContext={}
        ) {
        super.init(
            verboseEnabled=arguments.verboseEnabled,
            timingEnabled=arguments.timingEnabled,
            cwd=arguments.cwd,
            timer=arguments.timer,
            moduleConfig=arguments.moduleConfig,
            envVars=arguments.envVars,
            secrets=arguments.secrets,
            runtimeContext=arguments.runtimeContext
        );
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
        
