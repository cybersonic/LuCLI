# Lifecycle Events + npm Build Example
This example shows how LuCLI server lifecycle events can run frontend build steps so browser assets are prepared before the server starts.

## What this example demonstrates
- `lucee.json` uses `events.before.serverStart` hooks.
- LuCLI runs:
  - `npm install`
  - `npm run build`
- The build outputs `webroot/assets/bundle.js`.
- `webroot/index.cfm` loads that bundle and uses `canvas-confetti` in the browser.

## Relevant configuration
In `lucee.json`, the lifecycle hook is:

```json
"events": {
  "before": {
    "serverStart": [
      "echo 'Before server start'",
      "npm install",
      "npm run build"
    ]
  }
}
```

The npm build script in `package.json` is:

```json
"scripts": {
  "build": "vite build"
}
```

Vite is configured to emit the bundle into `webroot/assets/bundle.js`.

## How to run
From this directory:

```bash
lucli server start
```

On server start, LuCLI executes the lifecycle hooks, installs packages (if needed), and builds frontend assets before serving `webroot`.

Then open the example app and click **Launch Confetti** to verify the npm-built bundle is loaded.
