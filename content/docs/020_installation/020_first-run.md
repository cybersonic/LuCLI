---
title: First Run
layout: docs
---

This guide walks you from a fresh LuCLI install to a running Lucee server and a basic CFML page.

## Verify the installation

First, confirm that LuCLI is available on your PATH:

```bash
lucli --version
```

You should see the LuCLI version and the bundled Lucee version.

```
lucli --version
LuCLI Version: 0.1.276-SNAPSHOT

 _           ____ _     ___ 
| |   _   _ / ___| |   |_ _|
| |  | | | | |   | |    | | 
| |__| |_| | |___| |___ | | 
|_____\__,_|\____|_____|___|

Lucee Version: 7.0.2.51-SNAPSHOT

Copyright (c) Mark Drew https://github.com/cybersonic
Repository: https://github.com/cybersonic/lucli
```

## Start a server in a new project folder

Create a new directory and start a server:

```bash
mkdir my-lucli-app
cd my-lucli-app
lucli server start
```

On first run, LuCLI will:

- Create a default `lucee.json` configuration if one doesn’t exist.
- Download the selected Lucee Express distribution (if not cached).
- Provision a Tomcat/Lucee server instance under `~/.lucli/servers/<name>`.

It will look something like this (paths will vary based on your OS and project location):

```
Starting server '"my-lucli-app' on:
  HTTP port:     8001
  Shutdown port: 9001
Deployed UrlRewriteFilter to project docBase:
  JAR: /my-lucli-app/WEB-INF/lib/urlrewritefilter-5.1.3.jar
  web.xml: /my-lucli-app/WEB-INF/web.xml
  urlrewrite.xml: /my-lucli-app/WEB-INF/urlrewrite.xml
Server started successfully on port 8001
Starting Lucee server in: /my-lucli-app
   Server Name:   my-lucli-app
   Process ID:    23387
   HTTP Port:     8001
   Shutdown Port: 9001
   Web Root:      /my-lucli-app
   Server Dir:    ~/.lucli/servers/my-lucli-app
   URL:           http://localhost:8001
   Admin URL:     http://localhost:8001/lucee/admin.cfm
✅ Server started successfully!
```


When the server is ready, LuCLI prints the URL and (optionally) opens a browser.

## Add a simple CFML page

Create an `index.cfm` in your project directory:

```cfm
<cfoutput>
  Hello from LuCLI! The time is #now()#.
</cfoutput>
```

Reload the browser. You should see the rendered CFML page from your LuCLI‑managed Lucee server.

## Stopping the server

From the same project directory:

```bash
lucli server stop
```

This finds and stops the server instance associated with the current project. You can always use `lucli server status` to confirm whether a server is running.

Once you have this working, you’re ready to explore the CLI basics, CFML execution shortcuts, and full configuration with `lucee.json`. 
