package org.lucee.lucli.cli;

import org.lucee.lucli.LuCLI;

import picocli.CommandLine.IVersionProvider;

/**
 * Version provider for Picocli that dynamically retrieves version information
 */
public class LuCLIVersionProvider implements IVersionProvider {
    
    @Override
    public String[] getVersion() throws Exception {
        return new String[]{ LuCLI.getFullVersionInfo() };
    }
}