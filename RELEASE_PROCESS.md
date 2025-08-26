# LuCLI Release Process

This document describes the automated release process for LuCLI and how to manage versions.

## Overview

LuCLI uses an automated release process that:
- Monitors the `pom.xml` version in the main branch
- Automatically creates releases when the version is not a SNAPSHOT
- Builds cross-platform binaries (Linux, macOS, Windows)
- Generates installation scripts and comprehensive release notes

## Automated Release Workflow

### Triggers
The release workflow (`auto-release.yml`) is triggered by:
- Pushes to the `main` branch that modify `pom.xml` or `src/**`
- Manual workflow dispatch from GitHub Actions tab

### Release Conditions
A release is only created when:
1. The version in `pom.xml` does not end with `-SNAPSHOT`
2. A Git tag with that version doesn't already exist

### Generated Artifacts
Each release includes:
- `lucli.jar` - Universal JAR file (requires Java 17+)
- `lucli-X.X.X-Linux` - Native Linux binary
- `lucli-X.X.X-macOS` - Native macOS binary  
- `lucli-X.X.X.bat` - Windows batch script
- `lucli-X.X.X.ps1` - Windows PowerShell script
- `install.sh` - One-liner installation script for Unix systems

## Release Management Script

The `scripts/release.sh` script helps manage versions and releases:

### Commands

```bash
# Check current status
./scripts/release.sh status

# Bump version (creates new SNAPSHOT)
./scripts/release.sh bump patch   # 1.0.1-SNAPSHOT → 1.0.2-SNAPSHOT
./scripts/release.sh bump minor   # 1.0.1-SNAPSHOT → 1.1.0-SNAPSHOT  
./scripts/release.sh bump major   # 1.0.1-SNAPSHOT → 2.0.0-SNAPSHOT

# Prepare release (removes SNAPSHOT)
./scripts/release.sh release      # 1.0.1-SNAPSHOT → 1.0.1

# Set next development version (after release)
./scripts/release.sh next-snapshot # 1.0.1 → 1.0.2-SNAPSHOT
```

## Typical Release Workflow

### 1. Development Phase
```bash
# Work with SNAPSHOT versions during development
git checkout main
git pull origin main

# Check current status
./scripts/release.sh status
# Current version: 1.0.40-SNAPSHOT
```

### 2. Prepare for Release
```bash
# Make sure all changes are committed and pushed
git add .
git commit -m "Final changes for release"
git push

# Remove SNAPSHOT suffix to trigger release
./scripts/release.sh release
# This changes: 1.0.40-SNAPSHOT → 1.0.40

# Commit the version change
git add pom.xml
git commit -m "Prepare release 1.0.40"
git push
```

### 3. Release is Created Automatically
- GitHub Actions detects the non-SNAPSHOT version
- Builds binaries for all platforms
- Creates Git tag `v1.0.40`
- Creates GitHub release with all artifacts
- Generates changelog from commits

### 4. Resume Development
```bash
# Set next development version
./scripts/release.sh next-snapshot
# This changes: 1.0.40 → 1.0.41-SNAPSHOT

# Commit the version change  
git add pom.xml
git commit -m "Prepare for next development iteration"
git push
```

## Manual Release Process

If you prefer to handle releases manually:

### 1. Update Version Manually
```bash
# Edit pom.xml to change version from X.X.X-SNAPSHOT to X.X.X
vim pom.xml

# Or use Maven
mvn versions:set -DnewVersion=1.0.40
mvn versions:commit
```

### 2. Commit and Push
```bash
git add pom.xml
git commit -m "Prepare release 1.0.40"
git push
```

### 3. Monitor GitHub Actions
- Go to the Actions tab in GitHub
- Watch the "Auto Release from POM Version" workflow
- The release will appear in the Releases section when complete

## Version Strategy

### Version Format
- `MAJOR.MINOR.PATCH[-SNAPSHOT]`
- Example: `1.2.3-SNAPSHOT` (development), `1.2.3` (release)

### When to Bump
- **Patch**: Bug fixes, minor improvements, documentation updates
- **Minor**: New features, backward-compatible changes  
- **Major**: Breaking changes, major architectural changes

### Current Versioning
- Development versions: `X.X.X-SNAPSHOT`
- Release versions: `X.X.X` (triggers automatic release)

## Installation for End Users

### One-liner Installation (Linux/macOS)
```bash
curl -L https://github.com/cybersonic/LuCLI/releases/latest/download/install.sh | bash
```

### Manual Installation
1. Download appropriate binary from [Releases](https://github.com/cybersonic/LuCLI/releases)
2. Make executable: `chmod +x lucli-X.X.X-Linux`
3. Move to PATH: `mv lucli-X.X.X-Linux /usr/local/bin/lucli`

### Using JAR
```bash
# Download lucli.jar from releases
java -jar lucli.jar --help
```

## Troubleshooting

### Release Not Created
- Check that version doesn't end with `-SNAPSHOT`
- Verify the Git tag doesn't already exist
- Check GitHub Actions logs for errors

### Build Failures
- Ensure all tests pass: `mvn test`
- Check Java version compatibility
- Verify all dependencies are available

### Binary Issues
- Native binaries require the binary profile: `mvn package -Pbinary`
- Ensure `build.sh` script works locally
- Check platform-specific build steps

## Rollback Process

If a release has issues:

### 1. Delete Bad Release
```bash
# Delete the Git tag
git tag -d v1.0.40
git push origin :refs/tags/v1.0.40

# Delete GitHub release (manual via UI)
```

### 2. Fix Issues
```bash
# Make fixes
git add .
git commit -m "Fix release issues"

# Bump to next patch version if needed
./scripts/release.sh bump patch
```

### 3. Re-release
Follow the normal release process with the fixed code.

## Monitoring

### GitHub Actions
- Check workflow status in the Actions tab
- Review build logs for any issues
- Monitor artifact uploads

### Release Quality
- Test installation scripts
- Verify binary functionality
- Check release notes accuracy

## Security Considerations

- All releases are built in GitHub's secure infrastructure
- No secrets required for public releases
- Binaries are deterministically built
- Source code is tagged for auditability
