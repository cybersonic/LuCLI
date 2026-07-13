---
title: Lifecycle Hooks
layout: docs
---

LuCLI supports lifecycle hooks in `lucee.json` so you can run commands before/after key server events, as well as  when startup fails.

## Example hooks

```json
{
  "events": {
    "before": {
      "serverStart": [
        "./hooks/before-start.sh"
      ],
      "serverStop": [
        "./hooks/before-stop.sh"
      ],
      "serverRestart": [
        "./hooks/before-restart.sh"
      ],
      "depsInstall": [
        "./hooks/before-deps.sh"
      ]
    },
    "after": {
      "serverStart": [
        "./hooks/after-start.sh"
      ],
      "serverStop": [
        "./hooks/after-stop.sh"
      ],
      "serverRestart": [
        "./hooks/after-restart.sh"
      ],
      "depsInstall": [
        "./hooks/after-deps.sh"
      ]
    },
    "on": {
      "serverStartFailure": [
        "./hooks/on-start-failure.sh"
      ]
    }
  }
}
```

## Events

The events that you can hook into are:

| Event key | When it runs |
| --- | --- |
| `events.before.serverStart` | Immediately before `lucli server start` or `lucli server run` launches the runtime process. |
| `events.after.serverStart` | After a successful background startup (`start`) once LuCLI confirms the server is up. |
| `events.before.serverStop` | Right before LuCLI begins stopping a running server. |
| `events.after.serverStop` | After LuCLI successfully stops a running server. |
| `events.before.serverRestart` | Right before restart begins (before the stop/start sequence). |
| `events.after.serverRestart` | After restart finishes (after stop + start complete). |
| `events.before.depsInstall` | Before startup auto-installs dependencies (only when `dependencySettings.autoInstallOnServerStart` is enabled). |
| `events.after.depsInstall` | After startup auto-installs dependencies (only when `dependencySettings.autoInstallOnServerStart` is enabled). |
| `events.on.serverStartFailure` | If startup fails and throws an error during server start/run. |
`server start` and `server run` both use the same `serverStart` hook keys.

All hook values can be either:
- a single command string, or
- an array of command strings (executed in order).

## What can be executed?

Hooks run as shell commands from your project directory, so anything runnable from your shell can be used, including:

- shell commands (for example `mkdir -p logs && touch logs/start.log`)
- shell scripts (for example `./hooks/before-start.sh`)
- LuCLI commands (for example `lucli deps install`)
- `.lucli` scripts (for example `lucli ./hooks/after-start.lucli`)

If your script file is directly executable in your shell, you can also run it directly.

## Execution behavior

- Commands run in the order they are listed.
- A non-zero exit code fails the hook and stops the current lifecycle action.
- Each hook command has a 15 minute timeout.
- Hooks inherit environment variables loaded by LuCLI (`envFile` and `envVars`).

### `serverStartFailure`

`events.on.serverStartFailure` runs when server startup throws an error. Use this for notifications or cleanup.

### `depsInstall`

`events.before.depsInstall` and `events.after.depsInstall` run only when dependency auto-install at startup is enabled (`dependencySettings.autoInstallOnServerStart`).

## Environment-specific hooks

Because hooks are in `lucee.json`, they can be overridden per environment using the `environments` block.

Example:

```json
{
  "events": {
    "before": {
      "serverStart": [
        "./hooks/base-checks.sh"
      ]
    }
  },
  "environments": {
    "prod": {
      "events": {
        "before": {
          "serverStart": [
            "./hooks/prod-checks.sh"
          ]
        }
      }
    }
  }
}
```
