package org.lucee.lucli.profile;

/**
 * Markspresso profile — activated when the CLI is invoked as {@code markspresso}.
 * Uses Markspresso-specific branding and a {@code ~/.markspresso} home directory.
 */
public class MarkspressoProfile implements CliProfile {

    @Override
    public String name() {
        return "markspresso";
    }

    @Override
    public String homeDirName() {
        return ".markspresso";
    }

    @Override
    public String promptPrefix() {
        return "markspresso";
    }

    @Override
    public String displayName() {
        return "Markspresso";
    }

    @Override
    public String bannerText() {
        return " __  __            _                        \n"
             + "|  \\/  | __ _ _ __| | _____ _ __  _ __ ___ \n"
             + "| |\\/| |/ _` | '__| |/ / __| '_ \\| '__/ _ \\\n"
             + "| |  | | (_| | |  |   <\\__ \\ |_) | | |  __/\n"
             + "|_|  |_|\\__,_|_|  |_|\\_\\___/ .__/|_|  \\___|\n"
             + "                           |_|              \n";
    }
}
