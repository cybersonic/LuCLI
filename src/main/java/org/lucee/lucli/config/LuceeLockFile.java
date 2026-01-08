package org.lucee.lucli.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.lucee.lucli.deps.LockedDependency;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.server.LuceeServerConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents the lucee-lock.json file structure
 */
public class LuceeLockFile {
    
    private static final String LOCK_FILE_NAME = "lucee-lock.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /**
     * Per-environment server lock configuration.
     * Key is the environment key used by LuCLI:
     * - "_default" for no explicit environment (plain `lucli server start`)
     * - Named environments such as "prod", "dev", etc.
     */
    public static class ServerLock {
        @JsonProperty("locked")
        public boolean locked;

        @JsonProperty("environment")
        public String environment;

        @JsonProperty("configFile")
        public String configFile;

        @JsonProperty("configHash")
        public String configHash;

        @JsonProperty("effectiveConfig")
        public LuceeServerConfig.ServerConfig effectiveConfig;

        @JsonProperty("lockedAt")
        public String lockedAt;
    }
    
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

    @JsonProperty("serverLocks")
    private Map<String, ServerLock> serverLocks;
    
    public LuceeLockFile() {
        this.generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.lucliVersion = LuCLI.getVersion();
        this.dependencies = new LinkedHashMap<>();
        this.devDependencies = new LinkedHashMap<>();
        this.serverLocks = new LinkedHashMap<>();
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

    public Map<String, ServerLock> getServerLocks() {
        if (serverLocks == null) {
            serverLocks = new LinkedHashMap<>();
        }
        return serverLocks;
    }

    public void setServerLocks(Map<String, ServerLock> serverLocks) {
        this.serverLocks = serverLocks;
    }

    /**
     * Get the lock entry for a given environment key (e.g. "_default", "prod").
     */
    public ServerLock getServerLock(String envKey) {
        if (serverLocks == null || envKey == null) {
            return null;
        }
        return serverLocks.get(envKey);
    }

    /**
     * Put or replace the lock entry for a given environment key.
     */
    public void putServerLock(String envKey, ServerLock lock) {
        if (serverLocks == null) {
            serverLocks = new LinkedHashMap<>();
        }
        serverLocks.put(envKey, lock);
    }

    /**
     * Return the set of environment keys that are currently locked.
     */
    public Set<String> getLockedEnvironments() {
        Set<String> result = new LinkedHashSet<>();
        if (serverLocks == null || serverLocks.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, ServerLock> entry : serverLocks.entrySet()) {
            ServerLock lock = entry.getValue();
            if (lock != null && lock.locked) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Compute a SHA-256 hash of the given configuration file.
     * Returns null if the file does not exist or hashing fails.
     */
    public static String computeConfigHash(Path configFile) {
        try {
            if (configFile == null || !Files.exists(configFile)) {
                return null;
            }
            byte[] data = Files.readAllBytes(configFile);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.println("Warning: Failed to compute config hash for " + configFile + ": " + e.getMessage());
            return null;
        }
    }
}
