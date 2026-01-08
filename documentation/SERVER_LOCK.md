# Server Locking (lucee-lock.json)

LuCLI can lock server configuration per environment so that future `lucli server start` and `lucli server run` commands use a stable, reproducible configuration even if `lucee.json` changes.

This is built on top of `lucee-lock.json`, which is already the source of truth for dependency locking.

## Concepts

- **Per-environment locks**
  - Locks are stored under a `serverLocks` section in `lucee-lock.json`.
  - Each entry is keyed by an "environment key":
    - `_default` – used when no `--env`/`--environment` flag is passed.
    - Named envs such as `prod`, `dev`, `staging`, etc.
  - Each lock stores:
    - Whether it is active (`locked: true|false`).
    - The config file name (usually `lucee.json`).
    - A hash of the config file at lock time.
    - The fully realized `ServerConfig` that LuCLI will use when starting servers.

- **Lenient runtime behaviour**
  - Locked configuration never prevents a server from starting.
  - If a lock is present for the requested env, LuCLI uses the locked `effectiveConfig` snapshot.
  - If `lucee.json` has changed since the lock was created, LuCLI logs a warning explaining that the project is locked and that the live file differs from the locked snapshot.

- **Mutation guard**
  - `lucli server config set` refuses to write `lucee.json` when **any** environment is locked, unless you use `--dry-run`.
  - This keeps `lucee.json` and `lucee-lock.json` in sync by forcing you to unlock or explicitly update the lock.

## CLI commands

### Locking configuration

```bash
# Lock default (no-env) configuration for the current project directory
lucli server lock

# Lock configuration for a specific environment
lucli server lock --env=prod

# Lock using a custom config file
lucli server lock --config=lucee-static.json

# Refresh an existing lock from the current lucee.json
lucli server lock --env=prod --update
```

Semantics:

- `lucli server lock` (no `--env`) locks the effective configuration for the default environment under the `_default` key.
- `lucli server lock --env=prod` locks the effective configuration that would be used for `--env=prod`.
- `--update` replaces an existing lock entry with a new snapshot from the current `lucee.json`.

If a lock is already active for that environment and you do **not** pass `--update`, LuCLI will print a helpful error explaining that the env is already locked and how to update or unlock it.

### Unlocking configuration

```bash
# Unlock default (no-env) configuration
lucli server unlock

# Unlock a specific environment
lucli server unlock --env=prod
```

Unlocking:

- Marks that environment as unlocked in `lucee-lock.json`.
- Does **not** stop any running servers; it only affects future `server start`/`run` commands.

## Effect on server start / run

When you start a server, `LuceeServerManager` looks for a matching lock entry before loading `lucee.json`:

1. Determine environment key:
   - `_default` when no `--env`/`--environment` was specified.
   - The value of `--env`/`--environment` otherwise.
2. Load `lucee-lock.json` from the project directory.
3. If there is an active lock for that env (`locked: true` and an `effectiveConfig`), LuCLI:
   - Uses the locked `effectiveConfig` snapshot as the server configuration.
   - Compares the current config file (`lucee.json` by default) to the stored `configHash`.
   - If the hash is different, prints a warning similar to:

   ```text
   ⚠️  Server configuration is LOCKED for env 'prod' (lucee-lock.json)
       - Using locked configuration snapshot
       - Detected changes in lucee.json since the lock was created (hash mismatch)
       - To roll these changes into the lock:
           lucli server lock --env=prod --update
       - To remove the lock:
           lucli server unlock --env=prod
   ```

4. If there is **no** active lock for that env, LuCLI behaves as before:
   - Loads `lucee.json`.
   - Applies any `--env` environment overrides.
   - Starts the server from the live configuration.

### Important notes

- Locks are **per environment**, not per server **name**.
- A lock only affects the configuration resolution step; all other behaviour (port conflict checks, Tomcat generation, CFConfig, extension deployment) remains the same.
- Because runtime behaviour is lenient, a locked project will still start successfully even if someone has edited `lucee.json`; the warning explains why the new values were not applied.

## Effect on `server config set`

`server config set` writes directly to `lucee.json`. When any environment is locked, LuCLI:

- Still allows `--dry-run` to preview what would be written.
- Refuses to write the file without `--dry-run` and prints a message like:

```text
❌ Cannot modify lucee.json via 'server config set' because server configuration is LOCKED.
   Locked environments: _default, prod

To change configuration:
  - Unlock:        lucli server unlock --env=<env>
  - Or update lock: lucli server lock --env=<env> --update
```

This makes it explicit that `lucee-lock.json` is the source of truth for locked environments.

## Typical workflows

### 1. Locking production configuration

```bash
# Validate config for production
lucli server start --env=prod --dry-run

# Lock production configuration
lucli server lock --env=prod

# Start a production server later
lucli server start --env=prod
```

Even if a developer later edits `lucee.json`, the production starts will continue to use the locked configuration, with a warning about drift.

### 2. Updating a lock after editing `lucee.json`

```bash
# Edit lucee.json (change ports, HTTPS, etc.)
vi lucee.json

# Preview the effect
lucli server start --env=prod --dry-run

# Refresh the lock from the new configuration
lucli server lock --env=prod --update

# Start with updated locked config
lucli server start --env=prod
```

### 3. Temporarily changing config in development

```bash
# Project is locked for default env
lucli server lock

# You want to experiment with different ports locally
lucli server unlock
lucli server config set port=8085
lucli server start

# Once you are happy with the new config, re-lock it
lucli server lock --update
```

## Autocompletion hints

The interactive terminal completion now includes:

- `server lock` and `server unlock` as subcommands.
- Emojis for lock/unlock in completion lists when emojis are enabled.

You can type `server l<Tab>` or `server u<Tab>` to quickly access these commands in terminal mode.
