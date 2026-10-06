package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A .cfm run by `lucli run` gets the built-in variables setup inside <cfscript>, so the
 * setup runs instead of being printed before the template's output
 * (upstream cybersonic/LuCLI#137).
 */
class TemplatePreludeTest {

    @Test
    void setupRunsInsideCfscriptAndTheTemplateFollowsUnchanged() {
        String setup = "// LuCLI Built-in Variables Setup\n__scriptDir = '/tmp';";
        String template = "<cfoutput>hello #1+1#</cfoutput>";
        String wrapped = LuceeScriptEngine.wrapBuiltinSetupForTemplate(setup, template);

        assertTrue(wrapped.startsWith("<cfscript>\n" + setup + "\n</cfscript>"));
        assertTrue(wrapped.endsWith(template));
        assertEquals(wrapped.indexOf("</cfscript>") + "</cfscript>".length(), wrapped.indexOf(template),
            "nothing may sit between the setup and the template, or it is output");
        assertFalse(wrapped.contains("Original Script Content"));
    }

    @Test
    void emptySetupLeavesTheTemplateAsIs() {
        assertEquals("<p>x</p>", LuceeScriptEngine.wrapBuiltinSetupForTemplate("", "<p>x</p>"));
        assertEquals("<p>x</p>", LuceeScriptEngine.wrapBuiltinSetupForTemplate(null, "<p>x</p>"));
    }
}
