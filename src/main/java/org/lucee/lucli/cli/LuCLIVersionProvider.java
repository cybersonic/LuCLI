package org.lucee.lucli.cli;

import picocli.CommandLine.IVersionProvider;
import org.lucee.lucli.LuCLI;

/**
 * Version provider for Picocli that dynamically retrieves version information
 */
public class LuCLIVersionProvider implements IVersionProvider {
    
    @Override
    public String[] getVersion() throws Exception {
        return new String[]{ LuCLI.getFullVersionInfo() };
    }
}