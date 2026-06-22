# LuCLI Release Process
This document describes how releases are created and how contributors should apply version bumps.

## Overview
LuCLI uses automated releases driven by the version in `pom.xml`:
- versions ending in `-SNAPSHOT` are development versions
- non-snapshot versions are release versions
- releases are created by GitHub Actions when a new release version is pushed

## Versioning Policy
LuCLI uses `MAJOR.MINOR.PATCH[-SNAPSHOT]`.

### Which version to bump
- **Patch** (`x.y.Z`): bug fixes, docs, CI/tooling changes, and low-risk improvements.
- **Minor** (`x.Y.0`): new features or user-visible behavior additions.
- **Major** (`X.0.0`): breaking changes (removed/renamed commands, incompatible config/output changes, or runtime requirement changes).

When in doubt:
- if users/scripts should not need changes, use **patch**
- if behavior/features expand in user-facing ways, use **minor**
- if users/scripts must change, use **major**

## Snapshot and Release Flow
1. Keep development on a `-SNAPSHOT` version.
2. Prepare a release by removing `-SNAPSHOT`.
3. Commit and push the version change.
4. After release completes, bump to the next `-SNAPSHOT`.

Using the helper script:
```bash
./scripts/release.sh status
./scripts/release.sh bump patch|minor|major
./scripts/release.sh release
./scripts/release.sh next-snapshot
```

## Typical Release Steps
```bash
# Check current status
./scripts/release.sh status

# Prepare release (remove -SNAPSHOT)
./scripts/release.sh release
git add pom.xml
git commit -m "Prepare release X.Y.Z"
git push

# After release automation completes, move to next dev version
./scripts/release.sh next-snapshot
git add pom.xml
git commit -m "Prepare next development iteration"
git push
```

## Troubleshooting
- No release created: confirm `pom.xml` version does not end with `-SNAPSHOT`.
- Duplicate version: confirm a tag/release for that version does not already exist.
- Build failure: run `mvn test` locally and check workflow logs.
