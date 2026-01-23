---
title: Common Workflows
layout: docs
---

This page shows a few end‑to‑end examples that combine the concepts from the getting‑started guides and reference docs.

## Build and run a simple CFML script

1. Create a project directory and script:

   ```bash
   mkdir -p ~/projects/hello-lucli
   cd ~/projects/hello-lucli
   echo "writeOutput('Hello from LuCLI' & chr(10));" > hello.cfs
   ```

2. Run the script:

   ```bash
   lucli hello.cfs
   ```

3. Add arguments and handle them in CFML (see [Shortcuts & Direct Execution](../040_cfml-execution/010_shortcuts-and-direct-execution/)):

   ```bash
   lucli hello.cfs one two three
   ```

## Start a project server with URL rewriting

1. In your project directory, create `lucee.json`:

   ```json
   {
     "name": "my-app",
     "port": 8080,
     "urlRewrite": {
       "enabled": true,
       "routerFile": "index.cfm"
     }
   }
   ```

2. Create a basic `index.cfm` router.
3. Run:

   ```bash
   lucli server start
   ```

4. Open `http://localhost:8080/` and test routes like `/about` or `/blog`.

See [URL Rewriting](../080_https-and-routing/010_url-rewriting/) for router patterns and advanced configuration.

## Turn a script into a reusable module

1. Create and refine a `.cfs` script until you use it regularly.
2. Scaffold a module:

   ```bash
   lucli modules init my-utility
   ```

3. Move your logic into `~/.lucli/modules/my-utility/Module.cfc`.
4. Run it anywhere:

   ```bash
   lucli my-utility arg1 arg2
   ```

See the [Modules](../040_cfml-execution/040_modules/) page for more on module structure and arguments.

## Debug a server with a Java agent

1. Add an agent block to your `lucee.json` (for example, a debugger or Prometheus exporter):

   ```json
   {
     "monitoring": {
       "enabled": true,
       "jmx": { "port": 8999 }
     },
     "agents": {
       "luceedebug": {
         "enabled": false,
         "jvmArgs": [
           "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:9999"
         ],
         "description": "Lucee step debugger agent"
       }
     }
   }
   ```

2. Start the server with the agent enabled:

   ```bash
   lucli server start --enable-agent luceedebug
   ```

3. Attach your debugger or scrape metrics, depending on the agent.
4. If startup fails and you suspect agents, retry without them:

   ```bash
   lucli server start --no-agents
   ```

For full agent configuration options, see [Server Agents](../090_monitoring-and-agents/020_server-agents/).

## Typical development loop

A common day‑to‑day loop looks like:

1. Start or restart a project server:

   ```bash
   lucli server start
   ```

2. Tail logs while you develop:

   ```bash
   lucli server log --follow
   ```

3. Use the monitoring dashboard when you need more detail:

   ```bash
   lucli server monitor
   ```

4. Run one‑off CFML scripts for maintenance or data fixes:

   ```bash
   lucli scripts/cleanup.cfs
   ```

5. Once a script stabilizes, promote it to a module so you can call it from any directory.

These examples should give you a feel for how the pieces fit together. For deeper explanations, follow the links to the relevant sections in the docs tree.