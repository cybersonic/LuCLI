component {
    function init() {
        writeOutput("Component initialized" & chr(10));
        return this;
    }
    
    function main(array args = []) {
        writeOutput("Hello from main method!" & chr(10));
        writeOutput("Received " & arrayLen(args) & " arguments" & chr(10));
        
        for (var i = 1; i <= arrayLen(args); i++) {
            writeOutput("  Arg " & i & ": " & args[i] & chr(10));
        }
        
        return true;
    }
}
