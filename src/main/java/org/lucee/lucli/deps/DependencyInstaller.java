package org.lucee.lucli.deps;

import org.lucee.lucli.config.DependencyConfig;

/**
 * Interface for dependency installers
 */
public interface DependencyInstaller {
    
    /**
     * Install a dependency and return lock file info
     * 
     * @param dep The dependency configuration
     * @return LockedDependency with installation details
     * @throws Exception if installation fails
     */
    LockedDependency install(DependencyConfig dep) throws Exception;
    
    /**
     * Check if this installer supports the given dependency
     * 
     * @param dep The dependency configuration
     * @return true if this installer can handle this dependency
     */
    boolean supports(DependencyConfig dep);
}
