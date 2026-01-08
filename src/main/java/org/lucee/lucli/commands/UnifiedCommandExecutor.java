package org.lucee.lucli.commands;

import java.nio.file.Path;

/**
 * @deprecated This class has been replaced by {@link org.lucee.lucli.server.ServerCommandHandler}.
 *             It remains only as a compatibility shim for older code paths and will be
 *             removed in a future release.
 */
@Deprecated
public class UnifiedCommandExecutor extends org.lucee.lucli.server.ServerCommandHandler {

    /**
     * Compatibility constructor that delegates to the new ServerCommandHandler.
     *
     * @param isTerminalMode       true when used from the interactive terminal,
     *                             false when used from one-shot CLI mode.
     * @param currentWorkingDirectory the working directory to resolve server configs from.
     */
    public UnifiedCommandExecutor(boolean isTerminalMode, Path currentWorkingDirectory) {
        super(isTerminalMode, currentWorkingDirectory);
    }
}
