# LuCLI Release Process
This document describes how releases are created and how contributors should apply version bumps.

## Overview
LuCLI uses automated workflows plus a weekly release-train PR:
- versions ending in `-SNAPSHOT` are development versions
- non-snapshot versions are release versions
- a weekly `Release Friday` workflow prepares a release PR from `main` when `CHANGELOG.md` has Unreleased entries
- stable publishing is executed from the `Release` workflow via `workflow_dispatch` after that PR is merged

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
1. Keep development on a `-SNAPSHOT` version on `main`.
2. Let `Release Friday` create a release PR (or trigger it manually) when `## Unreleased` has entries.
3. Merge the release PR (this removes `-SNAPSHOT`).
4. Run the stable `Release` workflow manually (`workflow_dispatch`) to publish artifacts.
5. Set next development version with `next-snapshot` in a follow-up PR.

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
# Option A: Let Release Friday create the PR automatically
# (scheduled Fridays, or manually run workflow "Release Friday")
git push

# Option B: Manual equivalent of release-train prep
./scripts/release.sh release
git add pom.xml
git commit -m "Prepare release X.Y.Z"
git push

# After release PR merge, run workflow "Release" manually to publish

# Then move to next dev version
./scripts/release.sh next-snapshot
git add pom.xml
git commit -m "Prepare next development iteration"
git push
```

## Release Friday Behavior
- Workflow file: `.github/workflows/release-friday.yml`
- Schedule: Fridays at 14:00 UTC
- Guardrails:
  - skips when current version is not `-SNAPSHOT`
  - skips when there are no bullet entries under `CHANGELOG.md` `## Unreleased`
- Output:
  - opens/updates PR `release/friday-<version>` that removes `-SNAPSHOT`

## Troubleshooting
- No Release Friday PR created: check that `## Unreleased` has at least one bullet and `pom.xml` is a `-SNAPSHOT` version.
- Stable release not published: run workflow `Release` via `workflow_dispatch` after merge.
- Duplicate version: confirm a tag/release for that version does not already exist.
- Build failure: run `mvn test` locally and check workflow logs.
