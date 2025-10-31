package org.lucee.lucli;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class SimpleLucee {
    public static void main(String[] args) throws Exception {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("CFML");

        // String script = readScriptTemplate("/examples/create_component.cfs");
        engine.eval("<cfscript>echo(\"Hello, Lucee!\");</cfscript>");
        return;
    }


    private static String readScriptTemplate(String templatePath) throws Exception {
        try (java.io.InputStream is = SimpleLucee.class.getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new java.io.FileNotFoundException("Script template not found: " + templatePath);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
