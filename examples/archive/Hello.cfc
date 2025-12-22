component {

    public function init() {
        return this;
    }

    public function main(array args = []) {
        writeOutput("Hello from LuCLI CFC!" & chr(10));
        writeOutput("Lucee version: " & server.lucee.version & chr(10));
        
        if (arrayLen(args) > 0) {
            writeOutput("Arguments received: " & arrayToList(args, ", ") & chr(10));
        }
        
        // Simple computation to see script execution time
        var result = 0;
        for (var i = 1; i <= 1000; i++) {
            result += i;
        }
        
        writeOutput("Sum of 1-1000: " & result & chr(10));
        writeOutput("Current time: " & now() & chr(10));
        
        return "Hello CFC execution completed";
    }
}
