component {
    // Missing semicolon and unused variable
    var unusedVariable = "test"
    var name = "Hello World"
    
    function test() {
        // Missing var scope
        localVar = "not scoped properly";
        return localVar;
    }
    
    function getName() {
        return name;
    }
}
