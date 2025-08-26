#!/bin/bash

# LuCLI Release Management Script
# This script helps manage versions and prepare releases

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

get_current_version() {
    cd "$PROJECT_ROOT"
    mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null
}

is_snapshot_version() {
    [[ "$1" == *"-SNAPSHOT" ]]
}

bump_version() {
    local version_type=$1
    cd "$PROJECT_ROOT"
    
    case $version_type in
        major)
            mvn build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.nextMajorVersion}.0.0-SNAPSHOT -q
            ;;
        minor)
            mvn build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.nextMinorVersion}.0-SNAPSHOT -q
            ;;
        patch)
            mvn build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.nextIncrementalVersion}-SNAPSHOT -q
            ;;
        *)
            log_error "Invalid version type: $version_type. Use: major, minor, or patch"
            exit 1
            ;;
    esac
    
    # Remove backup files
    find . -name "pom.xml.versionsBackup" -delete
}

prepare_release() {
    local current_version=$(get_current_version)
    
    if ! is_snapshot_version "$current_version"; then
        log_error "Current version $current_version is not a SNAPSHOT. Cannot prepare release."
        exit 1
    fi
    
    # Remove -SNAPSHOT suffix for release version
    local release_version=${current_version%-SNAPSHOT}
    
    log_info "Preparing release version: $release_version"
    
    cd "$PROJECT_ROOT"
    
    # Set release version
    mvn versions:set -DnewVersion="$release_version" -q
    find . -name "pom.xml.versionsBackup" -delete
    
    log_success "Release version set to: $release_version"
    echo
    log_info "Next steps:"
    echo "1. Review the changes: git diff"
    echo "2. Commit the version change: git add pom.xml && git commit -m 'Prepare release $release_version'"
    echo "3. Push to trigger release: git push"
    echo "4. After release, run: $0 next-snapshot"
}

set_next_snapshot() {
    local current_version=$(get_current_version)
    
    if is_snapshot_version "$current_version"; then
        log_error "Current version $current_version is already a SNAPSHOT."
        exit 1
    fi
    
    # Calculate next patch version as snapshot
    cd "$PROJECT_ROOT"
    mvn build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.nextIncrementalVersion}-SNAPSHOT -q
    find . -name "pom.xml.versionsBackup" -delete
    
    local new_version=$(get_current_version)
    log_success "Next development version set to: $new_version"
}

check_status() {
    local current_version=$(get_current_version)
    
    echo
    log_info "LuCLI Version Status"
    echo "===================="
    echo "Current version: $current_version"
    
    if is_snapshot_version "$current_version"; then
        echo "Status: Development version (SNAPSHOT)"
        echo "Ready for release: No"
    else
        echo "Status: Release version"
        echo "Ready for release: Yes"
    fi
    
    echo
    log_info "Git Status"
    echo "=========="
    cd "$PROJECT_ROOT"
    git status --porcelain
    
    echo
    log_info "Recent commits"
    echo "=============="
    git log --oneline -5
    
    echo
    log_info "Available tags"
    echo "=============="
    git tag -l "v*" --sort=-version:refname | head -5
}

show_help() {
    cat << EOF
LuCLI Release Management Script

Usage: $0 <command>

Commands:
    status              Show current version and git status
    bump <type>         Bump version (major, minor, patch)
    release             Prepare release version (remove SNAPSHOT)
    next-snapshot       Set next development version (after release)
    
Examples:
    $0 status                    # Check current status
    $0 bump patch               # Bump to next patch version
    $0 release                  # Prepare for release
    $0 next-snapshot           # Set next development version

Typical Release Workflow:
    1. $0 bump patch           # or minor/major
    2. Make your changes and commit them
    3. $0 release              # Remove SNAPSHOT suffix
    4. git add pom.xml && git commit -m "Prepare release X.X.X"
    5. git push                # This triggers the auto-release workflow
    6. $0 next-snapshot       # Set next development version
    7. git add pom.xml && git commit -m "Prepare for next development iteration"
    8. git push

Notes:
    - The auto-release workflow will only create a release for non-SNAPSHOT versions
    - Releases are automatically created when pom.xml version is changed to a non-SNAPSHOT
    - Each release includes binaries for Linux, macOS, and Windows
    - Installation scripts are automatically generated
EOF
}

# Main script logic
case "${1:-}" in
    status)
        check_status
        ;;
    bump)
        if [ -z "$2" ]; then
            log_error "Version type required: major, minor, or patch"
            exit 1
        fi
        current_version=$(get_current_version)
        bump_version "$2"
        new_version=$(get_current_version)
        log_success "Version bumped from $current_version to $new_version"
        ;;
    release)
        prepare_release
        ;;
    next-snapshot)
        set_next_snapshot
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        log_error "Unknown command: ${1:-}"
        echo
        show_help
        exit 1
        ;;
esac
