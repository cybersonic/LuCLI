package org.lucee.lucli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for the dependencySettings section in lucee.json
 * Controls how dependencies are installed and managed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DependencySettingsConfig {
    
    @JsonProperty("installLocation")
    private String installLocation = "dependencies";
    
    @JsonProperty("autoInstallOnServerStart")
    private boolean autoInstallOnServerStart = true;
    
    @JsonProperty("verifyIntegrity")
    private boolean verifyIntegrity = true;
    
    @JsonProperty("pruneOnInstall")
    private boolean pruneOnInstall = false;
    
    @JsonProperty("installMethod")
    private String installMethod = "symlink";
    
    @JsonProperty("installDevDependencies")
    private Boolean installDevDependencies;
    
    // Constructors
    public DependencySettingsConfig() {
    }
    
    // Getters and setters
    
    public String getInstallLocation() {
        return installLocation;
    }
    
    public void setInstallLocation(String installLocation) {
        this.installLocation = installLocation;
    }
    
    public boolean isAutoInstallOnServerStart() {
        return autoInstallOnServerStart;
    }
    
    public void setAutoInstallOnServerStart(boolean autoInstallOnServerStart) {
        this.autoInstallOnServerStart = autoInstallOnServerStart;
    }
    
    public boolean isVerifyIntegrity() {
        return verifyIntegrity;
    }
    
    public void setVerifyIntegrity(boolean verifyIntegrity) {
        this.verifyIntegrity = verifyIntegrity;
    }
    
    public boolean isPruneOnInstall() {
        return pruneOnInstall;
    }
    
    public void setPruneOnInstall(boolean pruneOnInstall) {
        this.pruneOnInstall = pruneOnInstall;
    }
    
    public String getInstallMethod() {
        return installMethod;
    }
    
    public void setInstallMethod(String installMethod) {
        this.installMethod = installMethod;
    }
    
    public Boolean getInstallDevDependencies() {
        return installDevDependencies;
    }
    
    public void setInstallDevDependencies(Boolean installDevDependencies) {
        this.installDevDependencies = installDevDependencies;
    }
}
