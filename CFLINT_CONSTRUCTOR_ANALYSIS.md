# CFLint Constructor Analysis and Implementation

## Summary

After analyzing the CFLint source code from https://github.com/cfmleditor/CFLint/, I've identified the correct constructor arguments and updated the LuCLI implementation accordingly.

## CFLint Constructor Patterns

### 1. CFLint Class (Direct Usage)
```java
// Constructor requiring CFLintConfiguration
public CFLint(final CFLintConfiguration configFile) throws IOException

// Constructor with configuration and scanners
public CFLint(final CFLintConfiguration configuration, final CFLintScanner... bugsScanners)
```

### 2. CFLintAPI Class (Recommended)
```java
// No-argument constructor (uses default configuration)
public CFLintAPI() throws CFLintConfigurationException

// Constructor with custom configuration
public CFLintAPI(final CFLintConfiguration configuration) throws CFLintConfigurationException
```

**Key Insight:** CFLint no longer has a no-argument constructor. It always requires a `CFLintConfiguration` parameter.

## Implementation Changes Made

### 1. Updated CFLintDownloader.java

The constructor logic now:
1. **First attempts CFLintAPI** (recommended approach):
   ```java
   Class<?> cfLintAPIClass = cfLintClassLoader.loadClass("com.cflint.api.CFLintAPI");
   cfLintInstance = cfLintAPIClass.getDeclaredConstructor().newInstance();
   ```

2. **Falls back to CFLint class** with proper configuration:
   ```java
   Class<?> cfLintConfigClass = cfLintClassLoader.loadClass("com.cflint.config.CFLintConfiguration");
   Class<?> configBuilderClass = cfLintClassLoader.loadClass("com.cflint.config.ConfigBuilder");
   
   Object configBuilder = configBuilderClass.getDeclaredConstructor().newInstance();
   Method buildMethod = configBuilderClass.getMethod("build");
   Object config = buildMethod.invoke(configBuilder);
   
   cfLintInstance = cfLintClass.getDeclaredConstructor(cfLintConfigClass).newInstance(config);
   ```

### 2. Updated CFLintCommand.java

Enhanced to handle both CFLintAPI and CFLint workflows:

- **CFLintAPI workflow**: Uses `scan(List<String>)` method that handles file processing internally
- **CFLint workflow**: Uses traditional `process(String, String)` method for each file, then `getResult()`

### 3. Configuration Handling

- **CFLintAPI**: Configuration is handled internally (default configuration)
- **CFLint**: Supports custom configuration via `.cflintrc` files

## API Differences

| Feature | CFLintAPI | CFLint |
|---------|-----------|---------|
| Constructor | `new CFLintAPI()` | `new CFLint(configuration)` |
| File Processing | `scan(List<String>)` | `process(String, String)` per file |
| Results | Returns `CFLintResult` | `getResult()` method |
| Configuration | Internal/default | External via CFLintConfiguration |

## Testing

The implementation automatically tries CFLintAPI first (as it's the recommended approach from the external context), and falls back to direct CFLint usage if the API class is not available.

Both approaches will work with the CFLint 1.5.6 JAR that LuCLI downloads automatically.

## Usage

Users can now use CFLint through LuCLI with commands like:

```bash
lucli lint .                      # Lint current directory
lucli lint src/                   # Lint src directory  
lucli lint Application.cfc        # Lint specific file
lucli lint --format json src/     # Output as JSON
lucli lint status                 # Check CFLint status
```

The implementation automatically downloads CFLint 1.5.6 on first use and handles all the constructor complexity transparently.
