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
get_unreleased_changelog_entries() {
    local changelog_file="$PROJECT_ROOT/CHANGELOG.md"

    if [ ! -f "$changelog_file" ]; then
        return 1
    fi

    awk '
        /^##[[:space:]]+Unreleased[[:space:]]*$/ { in_unreleased=1; next }
        /^##[[:space:]]+/ { if (in_unreleased) exit }
        in_unreleased && /^[[:space:]]*-[[:space:]]/ { print }
    ' "$changelog_file"
}

suggest_bump_from_changelog() {
    local changelog_file="$PROJECT_ROOT/CHANGELOG.md"
    local entries

    if [ ! -f "$changelog_file" ]; then
        log_error "CHANGELOG.md not found at $changelog_file"
        exit 1
    fi

    entries=$(get_unreleased_changelog_entries)

    if [ -z "$entries" ]; then
        log_warning "No bullet entries found under ## Unreleased. Defaulting to patch."
        echo
        log_info "Suggested bump: patch"
        log_info "Suggested command: $0 bump patch"
        return 0
    fi

    local major_count=0
    local minor_count=0
    local patch_count=0
    local total_count=0

    while IFS= read -r entry; do
        [ -z "$entry" ] && continue

        total_count=$((total_count + 1))
        local normalized_entry
        normalized_entry=$(printf '%s\n' "$entry" | tr '[:upper:]' '[:lower:]')

        if printf '%s\n' "$normalized_entry" | grep -Eq '\[(major|breaking)\]|breaking[[:space:]-]?change|(^|[^a-z])breaking([^a-z]|$)|incompatible|removed'; then
            major_count=$((major_count + 1))
        elif printf '%s\n' "$normalized_entry" | grep -Eq '\[patch\]|^[[:space:]]*-[[:space:]]+\*\*(fix|docs?|documentation|test|tests|regression|chore|ci|refactor|cleanup|contract clarification|hotfix)[: ]'; then
            patch_count=$((patch_count + 1))
        else
            minor_count=$((minor_count + 1))
        fi
    done <<< "$entries"

    local suggestion="patch"
    if [ "$major_count" -gt 0 ]; then
        suggestion="major"
    elif [ "$minor_count" -gt 0 ]; then
        suggestion="minor"
    fi

    log_info "Suggested bump from CHANGELOG.md: $suggestion"
    log_info "Entries scanned under ## Unreleased: $total_count (major signals: $major_count, minor signals: $minor_count, patch signals: $patch_count)"
    log_info "Suggested command: $0 bump $suggestion"
}

get_suggested_bump_value() {
    local changelog_file="$PROJECT_ROOT/CHANGELOG.md"
    local entries

    if [ ! -f "$changelog_file" ]; then
        log_error "CHANGELOG.md not found at $changelog_file"
        exit 1
    fi

    entries=$(get_unreleased_changelog_entries)

    if [ -z "$entries" ]; then
        echo "patch"
        return 0
    fi

    local major_count=0
    local minor_count=0

    while IFS= read -r entry; do
        [ -z "$entry" ] && continue
        local normalized_entry
        normalized_entry=$(printf '%s\n' "$entry" | tr '[:upper:]' '[:lower:]')

        if printf '%s\n' "$normalized_entry" | grep -Eq '\[(major|breaking)\]|breaking[[:space:]-]?change|(^|[^a-z])breaking([^a-z]|$)|incompatible|removed'; then
            major_count=$((major_count + 1))
        elif ! printf '%s\n' "$normalized_entry" | grep -Eq '\[patch\]|^[[:space:]]*-[[:space:]]+\*\*(fix|docs?|documentation|test|tests|regression|chore|ci|refactor|cleanup|contract clarification|hotfix)[: ]'; then
            minor_count=$((minor_count + 1))
        fi
    done <<< "$entries"

    if [ "$major_count" -gt 0 ]; then
        echo "major"
    elif [ "$minor_count" -gt 0 ]; then
        echo "minor"
    else
        echo "patch"
    fi
}

print_unreleased_count() {
    local entries
    local count=0

    entries=$(get_unreleased_changelog_entries || true)
    if [ -z "$entries" ]; then
        echo "0"
        return 0
    fi

    while IFS= read -r entry; do
        [ -z "$entry" ] && continue
        count=$((count + 1))
    done <<< "$entries"

    echo "$count"
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

run_jreleaser() {
    local dry_run="$1"
    local current_version
    current_version=$(get_current_version)

    if is_snapshot_version "$current_version"; then
        log_error "Current version $current_version is a SNAPSHOT. Please run '$0 release' and commit/tag before running JReleaser."
        exit 1
    fi

    cd "$PROJECT_ROOT"

    # IMPORTANT: do NOT use -Pbinary here.
    # The binary profile auto-bumps the version to the next *-SNAPSHOT*,
    # which prevents JReleaser from treating this as a proper release.
    # We build the standard JAR and let JReleaser upload/package that.
    local mvn_cmd="mvn -DskipTests=true clean package jreleaser:full-release"
    if [[ "$dry_run" == "true" ]]; then
        mvn_cmd+=" -Djreleaser.dry.run=true"
    fi

    log_info "Running JReleaser full-release for version $current_version${dry_run:+ (dry run)}"
    log_info "$mvn_cmd"

    # Use eval so the composed command (with flags) is executed correctly
    eval "$mvn_cmd"

    if [[ "$dry_run" == "true" ]]; then
        log_success "JReleaser dry run completed for version $current_version"
    else
        log_success "JReleaser full release completed for version $current_version"
    fi
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
    status                  Show current version and git status
    suggest-bump            Suggest major/minor/patch from CHANGELOG.md Unreleased entries
    suggest-bump-value      Output only suggested major/minor/patch value
    unreleased-count        Output number of bullets under CHANGELOG.md ## Unreleased
    bump <type>             Bump version (major, minor, patch)
    release                 Prepare release version (remove SNAPSHOT)
    next-snapshot           Set next development version (after release)
    jreleaser-dry-run       Build binary and run JReleaser full-release in dry-run mode
    jreleaser-release       Build binary and run JReleaser full-release (actual release)
    
Examples:
    $0 status                        # Check current status
    $0 suggest-bump                  # Recommend bump based on changelog entries
    $0 bump patch                   # Bump to next patch version
    $0 release                      # Prepare for release
    $0 jreleaser-dry-run           # Validate JReleaser config without publishing
    $0 jreleaser-release           # Perform a full JReleaser release
    $0 next-snapshot               # Set next development version

Typical Release Workflow:
    1. $0 bump patch               # or minor/major
    2. Make your changes and commit them
    3. $0 release                  # Remove SNAPSHOT suffix
    4. git add pom.xml && git commit -m "Prepare release X.X.X"
    5. git push                    # Optional: push tags/commits
    6. export JRELEASER_GITHUB_TOKEN=...   # Ensure GitHub token is set
    7. $0 jreleaser-release        # Run JReleaser full-release
    8. $0 next-snapshot           # Set next development version
    9. git add pom.xml && git commit -m "Prepare for next development iteration"
   10. git push

Notes:
    - JReleaser requires a non-SNAPSHOT version in pom.xml
    - JReleaser uses JRELEASER_GITHUB_TOKEN for authentication
    - The auto-release workflow will only create a release for non-SNAPSHOT versions
    - Each release includes binaries for Linux, macOS, and Windows
EOF
}

# Main script logic
case "${1:-}" in
    status)
        check_status
        ;;
    suggest-bump)
        suggest_bump_from_changelog
        ;;
    suggest-bump-value)
        get_suggested_bump_value
        ;;
    unreleased-count)
        print_unreleased_count
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
    jreleaser-dry-run)
        run_jreleaser "true"
        ;;
    jreleaser-release)
        run_jreleaser "false"
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
