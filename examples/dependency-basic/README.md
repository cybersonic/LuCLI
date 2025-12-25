# Basic Dependency Management

Demonstrates installing CFML dependencies from Git repositories using LuCLI's dependency management.

## What This Shows

- Installing Git-based CFML dependencies
- Automatic dependency mapping generation
- Using the lock file for reproducible installs
- Accessing installed dependencies via mappings

## Prerequisites

- LuCLI installed
- Git installed
- Internet connection

## Quick Start

```bash
cd examples/dependency-basic

# Install dependencies
lucli install

# Start the server
lucli server start

# Open http://localhost:8080 to see the demo
```

## What Happens

1. **Install** - LuCLI reads `lucee.json`, clones the FW/1 repository, extracts the framework directory, and generates a lock file
2. **Mapping** - The `/fw1/` mapping is automatically created in the Lucee server configuration
3. **Demo** - The index.cfm page instantiates FW/1 components to prove the dependency works

## Key Files

- **lucee.json** - Defines the FW/1 dependency and server configuration
- **lucee-lock.json** - Generated lock file with exact git commit hashes
- **index.cfm** - Demo page that uses the installed dependency

## Expected Output

When you visit http://localhost:8080, you should see:
- Confirmation that FW/1 is accessible
- The FW/1 version number
- The physical path where FW/1 is installed

## Clean Up

```bash
lucli server stop
rm -rf framework lucee-lock.json
```

## Learn More

- [Dependency Management Documentation](../../documentation/todo/DEPENDENCY_MANAGEMENT.md)
- [FW/1 Framework](https://github.com/framework-one/fw1)
