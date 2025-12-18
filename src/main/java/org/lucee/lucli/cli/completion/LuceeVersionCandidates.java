package org.lucee.lucli.cli.completion;

import java.util.Iterator;
import java.util.List;

import org.lucee.lucli.commands.ServerConfigHelper;

/**
 * Picocli completion candidates provider for Lucee versions.
 *
 * This class delegates to {@link ServerConfigHelper#getAvailableVersions()}
 * so that all Lucee version discovery logic (remote API, cache, fallbacks)
 * is centralized in one place.
 */
public class LuceeVersionCandidates implements Iterable<String> {

    @Override
    public Iterator<String> iterator() {
        ServerConfigHelper helper = new ServerConfigHelper();
        List<String> versions = helper.getAvailableVersions();
        return versions.iterator();
    }
}
