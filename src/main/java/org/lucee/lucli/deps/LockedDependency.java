package org.lucee.lucli.deps;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a locked dependency in lucee-lock.json
 */
public class LockedDependency {
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("resolved")
    private String resolved;  // URL or git commit
    
    @JsonProperty("integrity")
    private String integrity; // SHA-512 checksum
    
    @JsonProperty("source")
    private String source;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("installPath")
    private String installPath;
    
    @JsonProperty("mapping")
    private String mapping;
    
    @JsonProperty("installedAt")
    private String installedAt;
    
    @JsonProperty("gitCommit")
    private String gitCommit; // For git sources
    
    @JsonProperty("subPath")
    private String subPath;   // If used
    
    public LockedDependency() {
        this.installedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    // Getters and setters
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getResolved() {
        return resolved;
    }
    
    public void setResolved(String resolved) {
        this.resolved = resolved;
    }
    
    public String getIntegrity() {
        return integrity;
    }
    
    public void setIntegrity(String integrity) {
        this.integrity = integrity;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getInstallPath() {
        return installPath;
    }
    
    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }
    
    public String getMapping() {
        return mapping;
    }
    
    public void setMapping(String mapping) {
        this.mapping = mapping;
    }
    
    public String getInstalledAt() {
        return installedAt;
    }
    
    public void setInstalledAt(String installedAt) {
        this.installedAt = installedAt;
    }
    
    public String getGitCommit() {
        return gitCommit;
    }
    
    public void setGitCommit(String gitCommit) {
        this.gitCommit = gitCommit;
    }
    
    public String getSubPath() {
        return subPath;
    }
    
    public void setSubPath(String subPath) {
        this.subPath = subPath;
    }
}
