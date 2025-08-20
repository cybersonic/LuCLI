package org.lucee.lucli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages file system state for the terminal, including current working directory
 * and path resolution utilities.
 */
public class FileSystemState {
    private Path currentWorkingDirectory;
    private final Path homeDirectory;
    private final Path initialDirectory;
    
    public FileSystemState() {
        // Initialize with system's current working directory
        this.initialDirectory = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        this.currentWorkingDirectory = this.initialDirectory;
        this.homeDirectory = Paths.get(System.getProperty("user.home")).toAbsolutePath();
    }
    
    /**
     * Get the current working directory
     */
    public Path getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }
    
    /**
     * Get the home directory
     */
    public Path getHomeDirectory() {
        return homeDirectory;
    }
    
    /**
     * Change directory to the specified path
     * @param targetPath The path to change to
     * @return true if successful, false otherwise
     */
    public boolean changeDirectory(String targetPath) {
        if (targetPath == null || targetPath.trim().isEmpty()) {
            // Empty path means go to home directory
            currentWorkingDirectory = homeDirectory;
            return true;
        }
        
        Path resolvedPath = resolvePath(targetPath);
        
        if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
            try {
                currentWorkingDirectory = resolvedPath.toRealPath();
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Resolve a path relative to the current working directory
     * Handles special cases like ~, ., .., and absolute paths
     */
    public Path resolvePath(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) {
            return currentWorkingDirectory;
        }
        
        pathStr = pathStr.trim();
        
        // Handle home directory shortcut
        if (pathStr.equals("~") || pathStr.startsWith("~/")) {
            if (pathStr.equals("~")) {
                return homeDirectory;
            } else {
                return homeDirectory.resolve(pathStr.substring(2));
            }
        }
        
        // Handle current directory
        if (pathStr.equals(".")) {
            return currentWorkingDirectory;
        }
        
        // Handle parent directory
        if (pathStr.equals("..")) {
            Path parent = currentWorkingDirectory.getParent();
            return parent != null ? parent : currentWorkingDirectory;
        }
        
        // Create path and resolve it
        Path path = Paths.get(pathStr);
        
        if (path.isAbsolute()) {
            return path.normalize();
        } else {
            return currentWorkingDirectory.resolve(path).normalize();
        }
    }
    
    /**
     * Get a display-friendly version of the current directory
     * Replaces home directory with ~ for brevity
     */
    public String getDisplayPath() {
        if (currentWorkingDirectory.startsWith(homeDirectory)) {
            Path relativePath = homeDirectory.relativize(currentWorkingDirectory);
            if (relativePath.toString().isEmpty()) {
                return "~";
            } else {
                return "~/" + relativePath.toString();
            }
        }
        return currentWorkingDirectory.toString();
    }
    
    /**
     * Check if a path exists
     */
    public boolean exists(String pathStr) {
        return Files.exists(resolvePath(pathStr));
    }
    
    /**
     * Check if a path is a directory
     */
    public boolean isDirectory(String pathStr) {
        Path path = resolvePath(pathStr);
        return Files.exists(path) && Files.isDirectory(path);
    }
    
    /**
     * Check if a path is a regular file
     */
    public boolean isRegularFile(String pathStr) {
        Path path = resolvePath(pathStr);
        return Files.exists(path) && Files.isRegularFile(path);
    }
    
    /**
     * Reset to initial directory
     */
    public void reset() {
        currentWorkingDirectory = initialDirectory;
    }
}
