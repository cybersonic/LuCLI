package org.lucee.lucli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a single dependency in lucee.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DependencyConfig {
    
    // The dependency name (derived from the key in dependencies object)
    private String name;
    
    // Type of dependency: cfml, jar, java, extension
    @JsonProperty("type")
    private String type = "cfml";
    
    // Version specification (semantic versioning)
    @JsonProperty("version")
    private String version;
    
    // Source: git, http, file, maven
    @JsonProperty("source")
    private String source = "git";
    
    // Git-specific properties
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("ref")
    private String ref = "main";
    
    // Optional subdirectory within source
    @JsonProperty("subPath")
    private String subPath;
    
    // Installation path (relative to project root)
    @JsonProperty("installPath")
    private String installPath;
    
    // Lucee mapping (e.g., /fw-one)
    @JsonProperty("mapping")
    private String mapping;
    
    // File source specific
    @JsonProperty("path")
    private String path;
    
    // Java/Maven specific
    @JsonProperty("groupId")
    private String groupId;
    
    @JsonProperty("artifactId")
    private String artifactId;
    
    @JsonProperty("repository")
    private String repository;
    
    // Constructors
    public DependencyConfig() {
    }
    
    public DependencyConfig(String name) {
        this.name = name;
    }
    
    /**
     * Apply defaults based on dependency name, type, and source
     */
    public void applyDefaults() {
        // Infer type from properties if not set
        if (type == null || type.equals("cfml")) {
            if (groupId != null && artifactId != null) {
                type = "java";
            } else if (url != null && url.endsWith(".jar")) {
                type = "jar";
            } else if (url != null && url.endsWith(".lex")) {
                type = "extension";
            } else {
                type = "cfml";
            }
        }
        
        // Infer source from type
        if (source == null || source.equals("git")) {
            if ("java".equals(type)) {
                source = "maven";
            } else if (url != null) {
                if (url.startsWith("git") || url.contains("github.com") || url.contains("gitlab.com")) {
                    source = "git";
                } else {
                    source = "http";
                }
            } else if (path != null) {
                source = "file";
            } else {
                // Default to git for CFML dependencies
                source = "git";
            }
        }
        
        // Apply type-specific defaults for installPath
        if (installPath == null) {
            switch (type) {
                case "cfml":
                    installPath = "dependencies/" + name;
                    break;
                case "jar":
                    installPath = "lib/" + name + ".jar";
                    break;
                case "java":
                    if (artifactId != null && version != null) {
                        installPath = "lib/" + artifactId + "-" + version + ".jar";
                    } else {
                        installPath = "lib/" + name + ".jar";
                    }
                    break;
                case "extension":
                case "lex":
                    installPath = "extensions/" + name + ".lex";
                    break;
                default:
                    installPath = "dependencies/" + name;
            }
        }
        
        // Apply mapping default for cfml type only
        if (mapping == null && "cfml".equals(type)) {
            mapping = "/" + name;
        }
        
        // Maven defaults
        if ("maven".equals(source) || "java".equals(type)) {
            if (repository == null) {
                repository = "https://repo1.maven.org/maven2/";
            }
        }
    }
    
    // Getters and setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getRef() {
        return ref;
    }
    
    public void setRef(String ref) {
        this.ref = ref;
    }
    
    public String getSubPath() {
        return subPath;
    }
    
    public void setSubPath(String subPath) {
        this.subPath = subPath;
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
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getArtifactId() {
        return artifactId;
    }
    
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
    
    public String getRepository() {
        return repository;
    }
    
    public void setRepository(String repository) {
        this.repository = repository;
    }
    
    @Override
    public String toString() {
        return name + "@" + version + " (" + source + ")";
    }
}
