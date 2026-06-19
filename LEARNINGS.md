# LEARNINGS.md

Codebase-specific patterns, gotchas, and useful knowledge accumulated over time.
Append new entries at the bottom under the appropriate date/session.

---

## 2026-03-18

- `CHANGELOG.md` uses an `## Unreleased` section at the top (no version/date) for in-progress work. Versioned releases follow below it (e.g. `## 0.2.23`). Each entry is a bullet with a **Bold Title:** followed by a short description.
- The test suite entry point is `./tests/test.sh`. A lightweight alternative is `./tests/test-simple.sh`. Always run the full suite before and after making changes.
- `./dev-lucli.sh` is the fast development loop — it runs LuCLI via `mvn exec:java` without rebuilding the JAR, so changes to source are picked up quickly for manual testing.
- The `#env:VAR#` syntax (not `${VAR}`) is the preferred variable substitution in `lucee.json`. `${VAR}` is reserved for Lucee/JVM runtime resolution and is intentionally left untouched in protected zones (`configuration`, `jvm.additionalArgs`).

## 2026-03-20

- BATS migration is being introduced in parallel via `tests/bats/` and `tests/test-bats.sh`; existing shell suites remain the source of truth until parity is reached.
- The current safe conversion target for BATS is deterministic tests (help/version/error code and `server start --dry-run` assertions) that avoid starting long-running server processes.
- A reliable second-pass BATS target is module and run-command smoke behavior (`modules help/list/init`, `.cfm` run, `.cfc` run-blocking, `.lucli` shortcut/run) plus `--config` dry-run selection tests.
- `.sdkmanrc` values can include trailing whitespace; trim parsed `java=` values in scripts before resolving `${HOME}/.sdkman/candidates/java/<version>` to avoid false toolchain mismatches.
- `tests/test-bats.sh` should own BATS-native JUnit output (`--report-formatter junit`) and publish a stable artifact path (`test-bats-results.xml` or `BATS_JUNIT_XML_OUTPUT`) for CI consumers.
- Keep BATS integration in `tests/test-all.sh` opt-in (`RUN_BATS=true`) during migration to avoid changing default runtime/coverage expectations for existing pipelines.
- In strict shell scripts (`set -euo pipefail`), avoid `sdk env` in hooks; prefer parsing `.sdkmanrc` and exporting `JAVA_HOME/PATH` directly to prevent silent early exits from SDKMAN shell functions.
- CI runners do not guarantee BATS availability; explicitly install it in workflow steps (`apt-get install bats` on Linux, `brew install bats-core` on macOS) before invoking `tests/test-bats.sh`.
- When temporarily skipping unstable migration tests, prefer method-level `@Disabled("reason")` with specific scope and rationale rather than broad class-level disables.
- In CI, prefer `bats -F tap --report-formatter junit` (and set `TERM=dumb` when missing) to avoid `tput`/broken-pipe formatter failures that can occur with `-F pretty` in non-interactive environments.

## 2026-03-23

- GitHub surfaces repository contribution guidance from `CONTRIBUTING.md` (root or `.github/`); `CONTRIBUTOR.md` is not used for the built-in Contributing entry point.
- GitHub Actions badges should use workflow-specific endpoints (`/actions/workflows/<workflow>.yml/badge.svg`) and link back to the corresponding workflow page for quick status drill-down.

## 2026-03-24

- For lightweight AI cost previews, a dependency-free estimate can be composed from three parts: composed text heuristic tokens, JSON envelope overhead tokens, and image tile heuristic tokens; output should explicitly label the result as rough and non-billable.
- In `.lucli` preprocessing, continuation joining runs before comment stripping; continuation logic must explicitly skip regular `#` comment lines during accumulation or commented-out options can leak into executed commands.
- In BATS, when passing positional parameters to `bash -c` via `run`, keep the command string single-quoted (for example, `'cd "$1" && java -jar "$2" ...'`); double-quoted strings expand `$1/$2` in the parent test shell first and can silently erase forwarded args like JAR paths.
- In BATS helpers, `BATS_TEST_TMPDIR` can be unavailable in `setup_file`; use a fallback chain like `BATS_TEST_TMPDIR -> BATS_FILE_TMPDIR -> TMPDIR` when creating shared temp directories.
- For `modules run` migration tests, prefer injecting a minimal deterministic `Module.cfc` after `modules init` so the test validates command wiring even if template defaults change.
- For whitespace-mode migration tests, direct output byte/length comparisons can be unstable due one-time engine initialization output and shell/BATS newline trimming; a deterministic assertion is to run with `--verbose` and verify `cfmlWriter` mode switches (`regular` vs `white-space`).

## 2026-03-25

- For `.lucli` AI automation pipelines, prefer `ai prompt --output-file <path> --force` over shell redirection for model outputs because LuCLI creates parent directories automatically and gives consistent write behavior across scripts.
- For project-scoped lifecycle commands, `.project-path` must be treated as a one-to-many index (`project -> [server slugs]`), not a one-to-one identity; when multiple slugs map to the same project, commands like no-arg `server status/stop/prune` should return an explicit disambiguation error and require `--name` (or `--config` where supported).
- In BATS helpers, avoid `bash -lc` wrappers for command execution because login shells can reset PATH/JAVA selection and cause runtime/class-version mismatches; use `bash -c` so the test runner’s exported toolchain environment is preserved.

## 2026-03-27

- In `lucli ai config add`, deriving provider engine class from `--type` in command logic is safer than a hardcoded picocli default for `--class`; otherwise non-OpenAI provider types can silently serialize as `OpenAIEngine`.
- Lucee AI provider config is key-name sensitive by engine family: OpenAI-compatible engines use `custom.secretKey` (and often `custom.type`), while Claude/Gemini use `custom.apiKey`; masking/listing/redaction helpers should support both.
- Gemini config template compatibility can require provider-specific key casing and value formats (`custom.apikey` lower-case plus string-valued timeout/temperature/beta fields), so a generic OpenAI-style payload builder is not sufficient for `GeminiEngine`.
- For one-shot local coverage runs, a dedicated script under `tests/` can safely standardize toolchain setup (`.sdkmanrc` -> `JAVA_HOME`) and execute `mvn clean test jacoco:report`, then surface `target/site/jacoco/index.html` for quick inspection.

## 2026-04-10

- Server lifecycle commands should resolve environment with explicit-first precedence: `--env/--environment` on `server start/run` wins, and only when missing should command handling fall back to `LuCLI.getCurrentEnvironment()` (which includes `LUCLI_ENV`).

## 2026-04-14

- `LuceeServerConfig.writeCfConfigIfPresent(...)` is the common write path used across runtime providers (`lucee-express`, `tomcat`, `jetty`) and sandbox flows, so startup messaging about generated `.CFConfig.json` should be emitted there to stay consistent for `server start` and `server run`.

## 2026-04-15

- Adding or changing `server` CLI flags requires updating multiple surfaces in sync: Picocli options in `src/main/java/org/lucee/lucli/cli/commands/ServerCommand.java`, manual parser logic in `src/main/java/org/lucee/lucli/server/ServerCommandHandler.java`, terminal completion metadata in `src/main/java/org/lucee/lucli/LucliCompleter.java`, and human-readable help text in `src/main/resources/text/server-help.txt`.
- In GitHub Actions, `EnricoMi/publish-unit-test-result-action` can create checks with `checks: write` but PR comment publishing still requires additional token scope; add `pull-requests: write` and `issues: write` at the job level (or disable comments) to avoid `403 Resource not accessible by integration` on PR runs.

## 2026-04-16

- `type: "extension"` dependencies now have an explicit install-time materialization toggle via `dependencySettings.materializeExtensionsOnInstall` (default `true`). When enabled, `deps install` writes concrete `.lex` files to `installPath`; when disabled, it keeps metadata/cache-style behavior.
- For end-to-end extension lifecycle coverage, the reliable integration assertion is that a local `.lex` dependency appears at `~/.lucli/servers/<server>/lucee-server/deploy/<file>.lex` after `deps install` + `server start`; deploy-path existence is a more stable contract check than strict byte-for-byte equality in BATS.
- Lucee extension provider IDs can appear in a non-canonical `8-4-4-16` hex grouping (for example `465E1E35-2425-4F4E-8B3FAB638BD7280A`), so extension ID validation/resolution must support that format in addition to canonical UUID and 32-hex forms.

## 2026-04-24

- Security-critical server flags derived from `lucee.json` (for example `admin.enabled=false`) should be enforced in both direct process environment injection and generated startup scripts (`setenv.sh`/`setenv.bat`) so manual or alternate startup paths cannot bypass intended runtime behavior.

## 2026-05-06

- In `.lucli` script execution, picocli subcommands return an integer exit code from `picocli.execute(...)`; if that value is ignored, script runs can print command errors but still exit with status `0`. Always propagate that exit code into the script-level result.
- OpenAI-compatible provider entries in Lucee AI config should not include `custom.timeout`; recent OpenAI-compatible APIs can reject it as an unsupported request argument. In `lucli ai config add`, gate timeout serialization by provider/engine compatibility and keep explicit user feedback when `--timeout` is ignored.

## 2026-05-07

- In `.lucli` command normalization, stripping a leading `lucli` prefix must operate on the raw command string (substring after `lucli`) rather than reparsing/rejoining tokens; rebuilding from `parseCommand(...)` drops original quoting and can split values like `\"Mark Drew\"` into multiple arguments.

## 2026-05-08

- For `lucli --version`, the most user-friendly Java runtime line comes from the first line of `java -version` output (normalized to remove quotes and the literal `version` token), with fallback to `java.runtime.name` + `java.runtime.version` when process execution is unavailable.
- `tests/test-bats.sh` only rebuilds `target/lucli.jar` / `target/lucli` when artifacts are missing or unrunnable; after source changes, rebuild artifacts manually before running BATS to avoid stale-binary false negatives.

## 2026-05-18

- Dependency descriptors in `lucee.json` now support `enabled` (default `true`); setting `enabled: false` on `type: "extension"` entries suppresses extension install/materialization and runtime activation, and environment-level dependency overrides can flip this flag via `environments.<env>.dependencies.<name>.enabled`.

## 2026-05-19

- To make `server start --dry-run --include-env` explain config substitution clearly, track a dedicated ordered map of realized placeholders in `LuceeServerConfig` during `#env:`/legacy `${}` resolution (including `:-default` fallbacks) and render that map separately from runtime process env vars.
- After `applyEnvironment(...)` deep-merges `environments.<env>` overrides into base config, rerun placeholder substitution and relative env path normalization on the merged tree; env-specific overlays can introduce new placeholders and relative `envVars` paths that are not present in the base config.
- Dry-run preview selection now uses an explicit section model: default `--dry-run` prints realized `config`, but any explicit selector (`--include ...` or alias flags like `--include-env`) disables implicit config output unless `config` is explicitly requested (for example `--include config,env`).

## 2026-05-20

- When applying environment overrides, reloading the realized merged `envFile` is required before building runtime env previews/process env; otherwise `--env <name>` can show/use variables from the base env file even when `envFile` is correctly overridden in `environments.<name>`.

## 2026-05-27

- `LuceeServerConfig` does strict Jackson binding to typed numeric fields (`port`, `shutdownPort`, `https.port`, `ajp.port`, `monitoring.jmx.port`). If those values can contain placeholders (for example `"#env:HTTP_PORT#"`), resolve them on the raw JSON tree before `treeToValue(...)`; otherwise deserialization fails before normal string substitution runs.

## 2026-05-28

- Runtime startup guards for distributed binaries belong in wrapper sources (`src/bin/lucli.sh`, `src/bin/lucli.bat`): `build.sh` and Maven's `binary` profile concatenate `src/bin/lucli.sh` with `target/lucli.jar` to produce `target/lucli`, so wrapper-level checks are the authoritative first gate for self-executing launches.

## 2026-06-06

- Current server dry-run semantics for unknown environments are intentionally non-fatal in `LuceeServerConfig.applyEnvironment(...)`: LuCLI warns and falls back to base config, so integration tests should assert success + warning text rather than expecting a non-zero exit.
- In `release.yml`, the `publish` job can fail resolving Lucee snapshot artifacts even when the matrix build already succeeded; restoring `~/.m2` cache in `publish` and using `mvn -o` for the Docker-image JAR build avoids flaky remote snapshot lookups.

## 2026-06-07

- For reusable workflow calls (`jobs.<job>.uses`), the caller workflow must grant at least the same `GITHUB_TOKEN` scopes that the called workflow requests; if omitted, caller defaults can remain `contents: read` / `pull-requests: none` and GitHub rejects the workflow as invalid before it runs.
- After introducing app-level top-level `version` defaults (for example `1.0.0`), Lucee runtime version resolution must only treat top-level `version` as a legacy engine value when it matches Lucee's `N.N.N.N`-style version pattern; otherwise fallback to `lucee.version` defaults to avoid misclassifying app versions as unsupported Lucee versions.

## 2026-06-08

- To gate release publishing on the exact same validation matrix as normal CI, add `workflow_call` to `.github/workflows/ci.yml` and invoke that workflow from `release.yml` via `jobs.<name>.uses`, instead of duplicating unit/integration/build jobs.
- In this repo, release publishing with JReleaser should not use `-Pbinary` because that profile bumps `project.version` to the next `*-SNAPSHOT`; build without `-Pbinary` and assemble launcher artifacts before running `jreleaser:full-release`.
- Markspresso's `build` command treats extra positional tokens as source path input; `lucli markspresso build clean` resolves source as `./clean` and can break Pages builds. Use `lucli markspresso build` or explicit flags like `--clean true` instead.

## 2026-06-19

- For LuCLI performance coverage, keep BATS perf checks as coarse smoke guards with conservative, env-overridable thresholds (for example `LUCLI_PERF_SMOKE_VERSION_MS` and `LUCLI_PERF_SMOKE_CFML_NOW_MS`) to avoid flaky CI while still catching major regressions.
- `BATS_TEST_TIMEOUT` applies to setup/teardown hooks too; expensive one-time runtime prep should happen in the runner (`tests/test-bats.sh` / `tests/test.sh`) rather than `setup_file`, otherwise hook prewarm can be killed as a timeout failure before any test runs.
- For BATS suites that create fresh `LUCLI_HOME` sandboxes per test, exporting a shared express cache path (for example `LUCLI_BATS_EXPRESS_CACHE_DIR`) and symlinking it in `setup_lucli_home` avoids repeated Lucee runtime downloads while preserving per-test home isolation.
- When enforcing `admin.enabled=false` for Tomcat runtimes, environment variables alone are not sufficient; `conf/web.xml` also needs explicit enforcement (remove `/lucee/admin.cfm`/`/lucee/admin/*` servlet mappings when present and add a deny-all `security-constraint` for admin URL patterns).
- In BATS helpers, avoid copying `~/.lucli/express` into each temporary `LUCLI_HOME` (slow and glob-fragile); symlink `${LUCLI_HOME}/express -> ${HOME}/.lucli/express` for fast local cache reuse.
- For URL rewrite regressions, validate both config artifacts and behavior: `urlRewrite.enabled=true` must inject `org.apache.catalina.valves.rewrite.RewriteValve` into `conf/server.xml` and `/hello` must route through `index.cfm` (with direct `.cfm` passthrough still intact).
