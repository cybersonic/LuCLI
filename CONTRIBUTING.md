# Contributing to LuCLI
Thanks for helping improve LuCLI.
This guide covers the expected workflow for proposing changes.

## Before You Start
- Open an issue or discussion for larger changes so implementation direction is clear.
- Keep changes focused and incremental when possible.
- Follow the LuCLI command pattern:
  - `lucli <action> <subcommand> [options] [parameters]`

## Local Setup
```bash
# Clone your fork/repository
git clone https://github.com/cybersonic/LuCLI.git
cd LuCLI

# Build project JAR
mvn clean package

# Fast development loop (runs via mvn exec:java)
./dev-lucli.sh
```

## Validate Your Changes
Run the standard test suites before opening a PR:
```bash
mvn test
./tests/test-bats.sh
```

Use `mvn test` for unit tests (JUnit in `src/test/java`) and `./tests/test-bats.sh` for integration/end-to-end CLI coverage.

Useful additional checks:
```bash
# Quick smoke cycle
./dev-lucli.sh --version

# Build self-executing binary
mvn clean package -Pbinary
```

## Documentation and Changelog
- Update docs when behavior or command usage changes.
- Add a short bullet to `CHANGELOG.md` under `## Unreleased` for user-facing changes.
- Keep examples aligned with current Java/LuCLI requirements.

## Pull Request Guidelines
- Use a clear title and description of what changed and why.
- Include example commands/output when relevant.
- Mention any known limitations or follow-up work.
- Keep PR scope narrow to simplify review.
- All external contributions must pass the repository's contribution agreement check (CLA or DCO) before merge.

## Branching and Release Workflow (Trunk-Based)
LuCLI uses a trunk-based workflow instead of full GitFlow.

- `main` is the trunk and should remain releasable.
- Use short-lived branches for work (for example: `feat/...`, `fix/...`, `docs/...`).
- Open PRs into `main` and merge frequently in small increments.
- Use release branches only when stabilizing a release (for example: `release/x.y`).
- Apply urgent fixes from the release branch/tag, publish the patch release, then merge those fixes back to `main`.

Release flow summary:
1. Develop on short-lived branches and merge to `main`.
2. Optionally cut `release/x.y` for stabilization.
3. Remove `-SNAPSHOT` to publish a release version.
4. Immediately bump to the next `-SNAPSHOT` after release.

## CLA / DCO Requirement
Every PR must satisfy the configured contribution agreement gate:
- **CLA mode:** sign the CLA when the bot requests it in the PR.
- **DCO mode:** sign commits with `Signed-off-by` (for example, `git commit -s`).

Maintainers enforce this with required status checks in branch protection.

## Versioning for Contributors
LuCLI uses `MAJOR.MINOR.PATCH[-SNAPSHOT]` in `pom.xml`.

### Which version to bump
- **Patch** (`x.y.Z`): bug fixes, docs, CI/tooling changes, and low-risk improvements.
- **Minor** (`x.Y.0`): new features or user-visible behavior additions.
- **Major** (`X.0.0`): breaking changes (removed/renamed commands, incompatible config/output changes, or runtime requirement changes).

When in doubt:
- If users/scripts should not need changes, use **patch**.
- If behavior/features expand in user-facing ways, use **minor**.
- If users/scripts must change, use **major**.

### Snapshot and release flow
- Keep development on `-SNAPSHOT` versions.
- Release by removing `-SNAPSHOT`, committing, and pushing.
- Immediately bump to the next `-SNAPSHOT` after a release.

Use the helper script:
```bash
./scripts/release.sh status
./scripts/release.sh bump patch|minor|major
./scripts/release.sh release
./scripts/release.sh next-snapshot
```

## Release and Process References
- Release details: [RELEASE_PROCESS.md](RELEASE_PROCESS.md)
- Project conventions and architecture notes: [WARP.md](WARP.md)
