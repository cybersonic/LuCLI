package org.lucee.lucli.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.lucee.lucli.LuceeScriptEngine;

/**
 * Test suite for LuceeScriptEngine to verify various CFML execution contexts
 * Tests different file types, argument passing, and execution modes
 */
public class LuceeScriptEngineTest {
    
    private static final String TEST_DIR = "test/cfml";
    
    public static void main(String[] args) {
        System.out.println("LuceeScriptEngine Test Suite");
        System.out.println("============================");
        System.out.println();
        
        try {
            // Initialize test environment
            setupTestEnvironment();
            
            // Run tests
            testBasicCFSExecution();
            testCFSWithArguments();
            testCFMTemplateExecution();
            testCFCComponentExecution();
            testOutputHandling();
            testErrorHandling();
            testVariableScoping();
            testComplexCFMLFeatures();
            
            System.out.println();
            System.out.println("✅ All tests completed!");
            
        } catch (Exception e) {
            System.err.println("❌ Test suite failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            cleanupTestEnvironment();
        }
    }
    
    private static void setupTestEnvironment() throws IOException {
        System.out.println("🔧 Setting up test environment...");
        Path testDirPath = Paths.get(TEST_DIR);
        Files.createDirectories(testDirPath);
        System.out.println("Created test directory: " + testDirPath.toAbsolutePath());
    }
    
    private static void testBasicCFSExecution() throws Exception {
        System.out.println("🧪 Testing basic CFS script execution...");
        
        String testScript = "// Basic CFS test\n" +
                           "writeOutput(\"Hello from CFS script!\" & chr(10));\n" +
                           "writeOutput(\"Current time: \" & now() & chr(10));";
        
        Path testFile = createTestFile("basic_test.cfs", testScript);
        executeAndReport(testFile, new String[]{});\n    }\n    \n    private static void testCFSWithArguments() throws Exception {\n        System.out.println(\"🧪 Testing CFS script with arguments...\");\n        \n        String testScript = \"// CFS with arguments test\\n\" +\n                           \"args = isDefined('__arguments') ? __arguments : [];\\n\" +\n                           \"writeOutput('Arguments received: ' & arrayLen(args) & chr(10));\\n\" +\n                           \"for (var i = 1; i <= arrayLen(args); i++) {\\n\" +\n                           \"    writeOutput('  Arg ' & i & ': ' & args[i] & chr(10));\\n\" +\n                           \"}\";\\n        \\n        Path testFile = createTestFile(\"args_test.cfs\", testScript);\n        executeAndReport(testFile, new String[]{\"arg1\", \"arg2\", \"arg3\"});\n    }\n    \n    private static void testCFMTemplateExecution() throws Exception {\n        System.out.println(\"🧪 Testing CFM template execution...\");\n        \n        String testTemplate = \"<cfoutput>\\n\" +\n                              \"<h1>CFM Template Test</h1>\\n\" +\n                              \"<p>Current date: ##now()##</p>\\n\" +\n                              \"<p>Template executed successfully!</p>\\n\" +\n                              \"</cfoutput>\";\n        \n        Path testFile = createTestFile(\"template_test.cfm\", testTemplate);\n        executeAndReport(testFile, new String[]{});\n    }\n    \n    private static void testCFCComponentExecution() throws Exception {\n        System.out.println(\"🧪 Testing CFC component execution...\");\n        \n        String testComponent = \"component {\\n\" +\n                               \"    function init() {\\n\" +\n                               \"        return this;\\n\" +\n                               \"    }\\n\" +\n                               \"    \\n\" +\n                               \"    function main(args) {\\n\" +\n                               \"        writeOutput('Hello from CFC component!' & chr(10));\\n\" +\n                               \"        writeOutput('Arguments: ' & arrayLen(args) & chr(10));\\n\" +\n                               \"        return 'CFC executed successfully';\\n\" +\n                               \"    }\\n\" +\n                               \"    \\n\" +\n                               \"    function helper() {\\n\" +\n                               \"        return 'Helper function called';\\n\" +\n                               \"    }\\n\" +\n                               \"}\";\n        \n        Path testFile = createTestFile(\"component_test.cfc\", testComponent);\n        executeAndReport(testFile, new String[]{\"test\", \"args\"});\n    }\n    \n    private static void testOutputHandling() throws Exception {\n        System.out.println(\"🧪 Testing output handling...\");\n        \n        String testScript = \"// Output handling test\\n\" +\n                           \"writeOutput('Plain text output' & chr(10));\\n\" +\n                           \"writeOutput('Number: ' & 42 & chr(10));\\n\" +\n                           \"writeOutput('Boolean: ' & true & chr(10));\\n\" +\n                           \"writeOutput('Array: ' & serializeJSON([1,2,3]) & chr(10));\\n\" +\n                           \"writeOutput('Struct: ' & serializeJSON({'key':'value'}) & chr(10));\";\n        \n        Path testFile = createTestFile(\"output_test.cfs\", testScript);\n        executeAndReport(testFile, new String[]{});\n    }\n    \n    private static void testErrorHandling() throws Exception {\n        System.out.println(\"🧪 Testing error handling...\");\n        \n        String testScript = \"// Error handling test\\n\" +\n                           \"try {\\n\" +\n                           \"    writeOutput('Before error' & chr(10));\\n\" +\n                           \"    // This should cause an error\\n\" +\n                           \"    undefinedVariable = someUndefinedFunction();\\n\" +\n                           \"    writeOutput('This should not print' & chr(10));\\n\" +\n                           \"} catch (any e) {\\n\" +\n                           \"    writeOutput('Caught error: ' & e.message & chr(10));\\n\" +\n                           \"    writeOutput('Error handled successfully' & chr(10));\\n\" +\n                           \"}\";\n        \n        Path testFile = createTestFile(\"error_test.cfs\", testScript);\n        executeAndReport(testFile, new String[]{});\n    }\n    \n    private static void testVariableScoping() throws Exception {\n        System.out.println(\"🧪 Testing variable scoping...\");\n        \n        String testScript = \"// Variable scoping test\\n\" +\n                           \"// Global scope\\n\" +\n                           \"globalVar = 'I am global';\\n\" +\n                           \"\\n\" +\n                           \"function testFunction() {\\n\" +\n                           \"    var localVar = 'I am local';\\n\" +\n                           \"    writeOutput('Inside function - global: ' & globalVar & chr(10));\\n\" +\n                           \"    writeOutput('Inside function - local: ' & localVar & chr(10));\\n\" +\n                           \"    return localVar;\\n\" +\n                           \"}\\n\" +\n                           \"\\n\" +\n                           \"writeOutput('Outside function - global: ' & globalVar & chr(10));\\n\" +\n                           \"result = testFunction();\\n\" +\n                           \"writeOutput('Function returned: ' & result & chr(10));\";\n        \n        Path testFile = createTestFile(\"scope_test.cfs\", testScript);\n        executeAndReport(testFile, new String[]{});\n    }\n    \n    private static void testComplexCFMLFeatures() throws Exception {\n        System.out.println(\"🧪 Testing complex CFML features...\");\n        \n        String testScript = \"// Complex CFML features test\\n\" +\n                           \"\\n\" +\n                           \"// Arrays and loops\\n\" +\n                           \"myArray = ['apple', 'banana', 'cherry'];\\n\" +\n                           \"writeOutput('Array contents:' & chr(10));\\n\" +\n                           \"for (var i = 1; i <= arrayLen(myArray); i++) {\\n\" +\n                           \"    writeOutput('  ' & i & ': ' & myArray[i] & chr(10));\\n\" +\n                           \"}\\n\" +\n                           \"\\n\" +\n                           \"// Structs and iteration\\n\" +\n                           \"myStruct = {'name': 'Test', 'version': '1.0', 'active': true};\\n\" +\n                           \"writeOutput('Struct contents:' & chr(10));\\n\" +\n                           \"for (var key in myStruct) {\\n\" +\n                           \"    writeOutput('  ' & key & ': ' & myStruct[key] & chr(10));\\n\" +\n                           \"}\\n\" +\n                           \"\\n\" +\n                           \"// JSON handling\\n\" +\n                           \"jsonString = serializeJSON(myStruct);\\n\" +\n                           \"writeOutput('JSON: ' & jsonString & chr(10));\\n\" +\n                           \"parsedStruct = deserializeJSON(jsonString);\\n\" +\n                           \"writeOutput('Parsed back: ' & parsedStruct.name & chr(10));\\n\" +\n                           \"\\n\" +\n                           \"// File operations (if available)\\n\" +\n                           \"try {\\n\" +\n                           \"    tempFile = getTempFile(getTempDirectory(), 'lucli_test');\\n\" +\n                           \"    fileWrite(tempFile, 'Test file content');\\n\" +\n                           \"    content = fileRead(tempFile);\\n\" +\n                           \"    writeOutput('File content: ' & content & chr(10));\\n\" +\n                           \"    fileDelete(tempFile);\\n\" +\n                           \"    writeOutput('File operations successful' & chr(10));\\n\" +\n                           \"} catch (any e) {\\n\" +\n                           \"    writeOutput('File operations not available: ' & e.message & chr(10));\\n\" +\n                           \"}\";\n        \n        Path testFile = createTestFile(\"complex_test.cfs\", testScript);\n        executeAndReport(testFile, new String[]{});\n    }\n    \n    private static Path createTestFile(String fileName, String content) throws IOException {\n        Path testFile = Paths.get(TEST_DIR, fileName);\n        Files.writeString(testFile, content);\n        System.out.println(\"  📁 Created: \" + fileName);\n        return testFile;\n    }\n    \n    private static void executeAndReport(Path testFile, String[] args) {\n        System.out.println(\"  ▶️  Executing: \" + testFile.getFileName() + \" with args: \" + Arrays.toString(args));\n        System.out.println(\"  📤 Output:\");\n        \n        try {\n            LuceeScriptEngine engine = LuceeScriptEngine.getInstance(false, false);\n            engine.executeScript(testFile.toString(), args);\n            System.out.println(\"  ✅ Execution completed successfully\");\n        } catch (Exception e) {\n            System.out.println(\"  ❌ Execution failed: \" + e.getMessage());\n            if (e.getCause() != null) {\n                System.out.println(\"     Cause: \" + e.getCause().getMessage());\n            }\n        }\n        \n        System.out.println();\n    }\n    \n    private static void cleanupTestEnvironment() {\n        System.out.println(\"🧹 Cleaning up test environment...\");\n        try {\n            Path testDirPath = Paths.get(TEST_DIR);\n            if (Files.exists(testDirPath)) {\n                Files.walk(testDirPath)\n                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories\n                    .forEach(path -> {\n                        try {\n                            Files.deleteIfExists(path);\n                        } catch (IOException e) {\n                            System.err.println(\"Failed to delete: \" + path + \" - \" + e.getMessage());\n                        }\n                    });\n                System.out.println(\"Cleaned up test directory\");\n            }\n        } catch (Exception e) {\n            System.err.println(\"Failed to cleanup: \" + e.getMessage());\n        }\n    }\n}
