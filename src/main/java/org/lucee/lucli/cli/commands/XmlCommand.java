package org.lucee.lucli.cli.commands;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;

/**
 * Hidden root command for XML/XPath diagnostic utilities.
 *
 * <p>This is primarily intended for internal/testing use while iterating on
 * Tomcat XML patching logic. It is not advertised in top-level help, but
 * running {@code lucli xml --help} will show usage.</p>
 */
@Command(
    name = "xml",
    description = "Experimental XML/XPath utilities (intended for internal use)",
    hidden = true,
    subcommands = {
        XmlExistsCommand.class,
        XmlFindCommand.class,
        XmlRemoveAllCommand.class,
        XmlAppendCommand.class,
        XmlSetAttributeCommand.class
    },
    footer = {
        "",
        "Handy XPath examples:",
        "  //Connector                              - all Connector elements",
        "  //Connector[@protocol='HTTP/1.1']        - primary HTTP connector",
        "  //Connector[contains(@protocol,'AJP')]   - all AJP connectors",
        "  //Engine/@defaultHost                    - default host name on Engine",
        "  //Host[@name='localhost']                - Host named 'localhost'",
        "  //servlet[servlet-class[starts-with(.,'lucee.loader.servlet')]]",
        "      - Lucee CFML servlets",
        "  //security-constraint[web-resource-collection/url-pattern='/lucee.json']",
        "      - lucee.json protection rule"
    }
)
public class XmlCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        // If invoked without subcommand, show help for this command.
        new picocli.CommandLine(this).usage(System.out);
        return 0;
    }
}
