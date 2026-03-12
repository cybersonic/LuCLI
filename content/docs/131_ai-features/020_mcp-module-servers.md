---
title: MCP Module Servers
layout: docs
---

This page documents LuCLI's MCP server command, which exposes LuCLI modules as MCP tools over stdio JSON-RPC.

## Quick start

```bash
# List available modules first
lucli modules list

# Start MCP server for an existing module
lucli mcp bitbucket

# Debug helper: simulate one MCP method and exit
lucli mcp bitbucket --once tools/list
```

## Create a new module and expose it as MCP tools

### 1) Scaffold a module

```bash
lucli modules init mcp-demo
```

This creates:

- `~/.lucli/modules/mcp-demo/Module.cfc`
- `~/.lucli/modules/mcp-demo/module.json`
- `~/.lucli/modules/mcp-demo/README.md`

### 2) Add MCP-exposed functions in `Module.cfc`

Functions in your module become MCP tools. A simple example:

```cfml
component extends="modules.BaseModule" {
    function main() {
        return "mcp-demo ready";
    }

    /**
     * Return a simple pong response.
     */
    function ping() {
        return "pong";
    }

    /**
     * Greet someone by name.
     */
    function greet(required string name) {
        return "Hello, " & arguments.name & "!";
    }
}
```

### 3) Start MCP server for your module

```bash
lucli mcp mcp-demo
```

### 4) Verify tool discovery quickly

```bash
lucli mcp mcp-demo --once tools/list
```

### 5) Exercise it with JSON-RPC over stdio

```bash
printf '%s\n%s\n%s\n' \
'{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}' \
'{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
'{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"greet","arguments":{"name":"Mark"}}}' \
| lucli mcp mcp-demo
```

You should receive JSON-RPC responses including discovered tools and a `tools/call` result.

## Overview

Use:

```bash
lucli mcp <module-name>
```

This starts an MCP server for the selected module. LuCLI inspects the module's function metadata and exposes functions as MCP tools.

## Command shape

```bash
lucli mcp <module-name>
lucli mcp --module <module-name>
```

Developer helper:

```bash
lucli mcp <module-name> --once tools/list
```

`--once` simulates one MCP request and exits, useful for quick debugging.

## Transport and protocol

Current transport behavior:

- stdio JSON-RPC
- one JSON message per line
- batch requests supported
- non-protocol output must not go to stdout

Supported MCP methods include:

- `initialize`
- `notifications/initialized`
- `ping`
- `tools/list`
- `tools/call`

If a method is unknown, LuCLI returns JSON-RPC `-32601` method-not-found.

## Tool discovery

`tools/list` is derived from module function metadata:

- function name -> MCP tool name
- first line of function hint -> description
- function parameters -> JSON Schema input properties

LuCLI maps common CFML argument types to JSON Schema types (`string`, `number`, `boolean`, `array`, `object`).

## Tool execution

`tools/call` executes the module function by:

1. resolving the requested tool name
2. validating it exists in discovered tools
3. converting MCP arguments to module argument strings (`key=value`)
4. executing the module function
5. returning captured output as MCP `content` text

If execution fails, the result is returned with `isError: true`.

## Example flow (conceptual)

1. client sends `initialize`
2. client sends `tools/list`
3. client sends `tools/call` with `name` and `arguments`
4. server returns text content from module output

## Troubleshooting

### `mcp: missing module name`

Pass a module name:

```bash
lucli mcp bitbucket
```

### `mcp: module not found`

Install or verify the module:

```bash
lucli modules list
```

### `Server not initialized`

Your MCP client must call `initialize` before `tools/list` or `tools/call`.

### Unexpected parse errors

Ensure the client sends valid JSON-RPC objects (one per line in stdio mode).
