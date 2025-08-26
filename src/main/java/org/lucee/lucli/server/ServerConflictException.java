package org.lucee.lucli.server;

/**
 * Exception thrown when a server name conflicts with an existing server instance
 */
public class ServerConflictException extends Exception {
    
    private final String conflictingName;
    private final String suggestedName;
    private final boolean existingServerRunning;
    
    public ServerConflictException(String conflictingName, String suggestedName, boolean existingServerRunning) {
        super(String.format("Server '%s' already exists. %s", 
            conflictingName, 
            existingServerRunning ? "The existing server is currently running." : "The existing server is stopped."));
        this.conflictingName = conflictingName;
        this.suggestedName = suggestedName;
        this.existingServerRunning = existingServerRunning;
    }
    
    public String getConflictingName() {
        return conflictingName;
    }
    
    public String getSuggestedName() {
        return suggestedName;
    }
    
    public boolean isExistingServerRunning() {
        return existingServerRunning;
    }
}
