package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Test suite for LuceeScriptEngine to verify various CFML execution contexts
 * Tests different file types, argument passing, and execution modes
 */
public class LuceeScriptEngineTest {
   

    @BeforeEach
    void setUp() {
        // Setup code if needed before each test
    }
    

    @Test
    void parseArguments() throws IOException{
        LuceeScriptEngine engine = LuceeScriptEngine.getInstance();
        String[] args = null;
        LuceeScriptEngine.ParsedArguments parsed = null;

        args = new String[] { "script", "arg1", "arg2", "arg3" };
        parsed = engine.parseArguments(args);
        assertEquals(parsed.subCommand, "script");

        args = new String[] { };
        parsed = engine.parseArguments(args);
        assertEquals(parsed.subCommand, "main");

        args = new String[] { "elvis", "arg1=2", "arg2=4", "arg3=elvis" };
        parsed = engine.parseArguments(args);
        assertEquals(parsed.subCommand, "elvis");
        
        assertTrue(parsed.argsMap.containsKey("arg1"));
        assertEquals(parsed.argsMap.get("arg1"), "2");
        assertTrue(parsed.argsMap.containsKey("arg2"));

        args = new String[] { "--no-clean", "--verbose", "--debug", "arg1=value1", "arg2=value2" };
        parsed = engine.parseArguments(args);
        assertEquals(parsed.subCommand, "main");
        assertTrue(parsed.argsMap.containsKey("clean"));
        assertEquals(parsed.argsMap.get("clean"), "false");
    } 

    @Test
    void testGetDottedPathFromCWD() throws IOException {
        LuceeScriptEngine engine = LuceeScriptEngine.getInstance();
        // Path originalCwd = Paths.get("").toAbsolutePath();
     

        String dottedPath = engine.getDottedPathFromCWD("my/project/Component.cfc");
        assertEquals(dottedPath, "my.project.Component");

     
    }
}


