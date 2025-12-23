package org.lucee.lucli.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.lucee.lucli.deps.LockedDependency;
import org.lucee.lucli.LuCLI;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the lucee-lock.json file structure
 */
public class LuceeLockFile {
    
    private static final String LOCK_FILE_NAME = "lucee-lock.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    
    @JsonProperty("lockfileVersion")
    private int lockfileVersion = 1;
    
    @JsonProperty("generatedAt")
    private String generatedAt;
    
    @JsonProperty("lucliVersion")
    private String lucliVersion;
    
    @JsonProperty("dependencies")
    private Map<String, LockedDependency> dependencies;
    
    @JsonProperty("devDependencies")
    private Map<String, LockedDependency> devDependencies;
    
    public LuceeLockFile() {
        this.generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.lucliVersion = LuCLI.getVersion();
        this.dependencies = new LinkedHashMap<>();
        this.devDependencies = new LinkedHashMap<>();
    }
    
    /**
     * Read existing lock file from current directory
     * @return LuceeLockFile instance or empty one if file doesn't exist
     */
    public static LuceeLockFile read() {
        return read(new File("."));
    }
    
    /**
     * Read existing lock file from specified Path
     * @param projectDir Project directory containing lucee-lock.json
     * @return LuceeLockFile instance or empty one if file doesn't exist
     */
    public static LuceeLockFile read(Path projectDir) {
        return read(projectDir.toFile());
    }
    
    /**
     * Read existing lock file from specified directory
     * @param projectDir Project directory containing lucee-lock.json
     * @return LuceeLockFile instance or empty one if file doesn't exist
     */
    public static LuceeLockFile read(File projectDir) {
        File lockFile = new File(projectDir, LOCK_FILE_NAME);
        
        if (!lockFile.exists()) {
            return new LuceeLockFile();
        }
        
        try {
            return MAPPER.readValue(lockFile, LuceeLockFile.class);
        } catch (IOException e) {
            // If lock file is corrupted, return empty one
            System.err.println("Warning: Could not read " + LOCK_FILE_NAME + ": " + e.getMessage());
            return new LuceeLockFile();
        }
    }
    
    /**
     * Write lock file to current directory
     */
    public void write() throws IOException {
        write(new File("."));
    }
    
    /**
     * Write lock file to specified directory
     * @param projectDir Project directory to write lucee-lock.json
     */
    public void write(File projectDir) throws IOException {
        File lockFile = new File(projectDir, LOCK_FILE_NAME);
        
        // Update generation timestamp
        this.generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.lucliVersion = LuCLI.getVersion();
        
        MAPPER.writeValue(lockFile, this);
    }
    
    // Getters and setters
    
    public int getLockfileVersion() {
        return lockfileVersion;
    }
    
    public void setLockfileVersion(int lockfileVersion) {
        this.lockfileVersion = lockfileVersion;
    }
    
    public String getGeneratedAt() {
        return generatedAt;
    }
    
    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }
    
    public String getLucliVersion() {
        return lucliVersion;
    }
    
    public void setLucliVersion(String lucliVersion) {
        this.lucliVersion = lucliVersion;
    }
    
    public Map<String, LockedDependency> getDependencies() {
        return dependencies;
    }
    
    public void setDependencies(Map<String, LockedDependency> dependencies) {
        this.dependencies = dependencies;
    }
    
    public Map<String, LockedDependency> getDevDependencies() {
        return devDependencies;
    }
    
    public void setDevDependencies(Map<String, LockedDependency> devDependencies) {
        this.devDependencies = devDependencies;
    }
}
