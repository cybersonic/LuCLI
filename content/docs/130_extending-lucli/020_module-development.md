---
title: Module Development
layout: docs
---

**LuCLI modules** are add-on commands written in CFML. Each module is a component under your LuCLI home that LuCLI loads and runs when you use `lucli modules run <name>`. You can add custom workflows, linters, generators, or any CLI tool you want—all in CFML, with access to LuCLI’s runtime (working directory, verbosity, timing, output helpers).

This guide is for **developers** who want to create or change modules: how to scaffold one, how `Module.cfc` and `init()` work, how LuCLI turns CLI arguments into CFML arguments, and how to implement subcommands and flags.

## What is a LuCLI module?

A module is a CFML component that LuCLI executes as a first-class command. It lives in your LuCLI home:

- **Directory:** `~/.lucli/modules/<module-name>/`
- **Entry point:** `Module.cfc` (typically `component extends="modules.BaseModule"`)
- **Metadata:** optional `module.json` and `README.md`

For **using** modules (running them, shortcuts, arguments), see [Running Modules](../040_cfml-execution/040_modules/).

## Creating a new module

The easiest way to start is with the built‑in template:

```bash
lucli modules init my-awesome-module
```

This will create:

- `~/.lucli/modules/my-awesome-module/Module.cfc`
- `~/.lucli/modules/my-awesome-module/module.json`
- `~/.lucli/modules/my-awesome-module/README.md`

The generated `Module.cfc` looks like this (simplified):

```cfml
component extends="modules.BaseModule" {

    function init(
        verboseEnabled = false,
        timingEnabled  = false,
        cwd            = "",
        timer          = nullValue()
    ) {
        variables.verbose       = arguments.verboseEnabled;
        variables.timingEnabled = arguments.timingEnabled;
        variables.cwd           = arguments.cwd;
        variables.timer         = arguments.timer ?: {};
        return this;
    }

    function main(string myArgument = "") {
        out("Hello from my-awesome-module!");
        return "Module executed successfully";
    }
}
```

You can customize both `init()` and `main()`; the template is just a starting point.

## How modules are executed

When you run a module, **LuCLI always ends up calling a function on your `Module.cfc`**—`main()` by default, or another function when you use subcommands.

At a high level LuCLI:

1. Resolves the module directory under `~/.lucli/modules/<name>/`.
2. Loads `Module.cfc` as `modules.<name>.Module`.
3. Calls `init()` once to pass runtime context (verbosity, timing, `cwd`, timer helper).
4. Parses CLI arguments into:
   - A `subcommand` (defaults to `main`), and
   - A normalized **argument collection** (positional + named/flag arguments).
5. Invokes `modules[subcommand](argumentCollection = argCollection)`.

In CFML land this is roughly equivalent to:

```cfml
modules = createObject("component", "modules.<name>.Module").init(
    verboseEnabled = verbose,
    timingEnabled  = timing,
    cwd            = __cwd,
    timer          = Timer
);

results = modules[subcommand](argumentCollection = argCollection);
```

For most module authors, the **main work happens in `main()` and other subcommand functions**. `init()` is primarily how LuCLI passes context into your module.

## Arguments: how CLI input maps to CFML

The CLI rules are the same as documented in [Running Modules](../040_cfml-execution/040_modules/), but here is the condensed developer view.

### Subcommand vs. main()

- If the first argument **does not** contain `=` and does **not** start with `-` or `--`, LuCLI treats it as the **subcommand** name.
- Otherwise, the subcommand defaults to `main`.

Examples:

```bash
# Calls main()
lucli reports

# Calls cleanup() subcommand
lucli reports cleanup
```

Inside CFML you can implement matching functions:

```cfml
component extends="modules.BaseModule" {

    function main() {
        // default path when no subcommand is given
    }

    function cleanup() {
        // runs when user passes `cleanup` as first arg
    }
}
```

### Positional arguments

Any argument (after the optional subcommand) that does **not** contain `=` is treated as a positional value.

```bash
lucli mymodule foo bar baz
```

These become `arg1`, `arg2`, `arg3`, … in the argument collection; CFML will map them onto your function signature by position:

```cfml
function main(string arg1, string arg2, string arg3) {
    // arg1="foo", arg2="bar", arg3="baz"
}
```

### Named arguments and flags

LuCLI normalizes several CLI forms into normal CFML named arguments.

All of the following end up calling `main(required string name, boolean force=false)` with the same values:

```bash
# key=value
lucli mymodule name=hello force=true

# long flags with =
lucli mymodule --name=hello --force=true

# boolean flags
lucli mymodule --name=hello --force
lucli mymodule --name=hello --no-force
```

Normalization rules (simplified):

- `key=value` → argument `key` with value `"value"`.
- `--key=value` or `-k=value` → argument `key` with value `"value"`.
- `--key` → boolean argument `key` with value `true`.
- `--no-key` → boolean argument `key` with value `false`.

So this function:

```cfml
function main(
    required string name,
    boolean        force = false
) {
    // name & force populated from CLI
}
```

will be populated correctly for all of the examples above.

### Mixed subcommand, positional, and named

```bash
lucli reports generate year=2025 format=csv --force
```

Results in a call roughly like:

```cfml
modules.generate(
    argumentCollection = {
        year   = "2025",
        format = "csv",
        force  = true
    }
);
```

## The `init()` contract

LuCLI calls `init()` on your `Module.cfc` with four arguments:

```cfml
function init(
    boolean verboseEnabled = false,
    boolean timingEnabled  = false,
    string  cwd            = "",
    any     timer
) {
    // Your setup here
    return this;
}
```

### Parameters

- `verboseEnabled` (boolean)
  - `true` when LuCLI was started with `--verbose`.
  - You can use this to gate debug/diagnostic output.

- `timingEnabled` (boolean)
  - `true` when LuCLI was started with `--timing`.
  - Lets you decide whether to emit timing‑related output.

- `cwd` (string)
  - The current working directory when the module was invoked.
  - Use this as the base path for resolving relative file paths.

- `timer` (struct/component)
  - A timing helper provided by LuCLI (backed by Java timing utilities).
  - Exposes at least `start(label)` and `stop(label)` functions.
  - You can safely treat it as an object with those two methods.

### Recommended pattern

If you extend `modules.BaseModule`, you usually **don’t need** to think about `init()` at all. Focus on implementing `main()` (and any additional subcommands) and let the base class handle context wiring.

The base implementation already:

```cfml
function init(
    boolean verboseEnabled = false,
    boolean timingEnabled  = false,
    string  cwd            = "",
    any     timer
) {
    variables.verboseEnabled = arguments.verboseEnabled;
    variables.timingEnabled = arguments.timingEnabled;
    variables.cwd = arguments.cwd;
    variables.timer = arguments.timer ?: {
        "start": function(){},
        "stop":  function(){}
    };
    return this;
}
```

So in your own module you can usually omit `init()` entirely and just rely on:

- `variables.verboseEnabled`
- `variables.timingEnabled`
- `variables.cwd`
- `variables.timer`

If you do override `init()`, make sure you:

- Keep the same parameters (so LuCLI can still call it), and
- Call `super.init()` if you want the base behavior, e.g.:

```cfml
component extends="modules.BaseModule" {

    function init(
        boolean verboseEnabled = false,
        boolean timingEnabled  = false,
        string  cwd            = "",
        any     timer
    ) {
        super.init(
            verboseEnabled = arguments.verboseEnabled,
            timingEnabled  = arguments.timingEnabled,
            cwd            = arguments.cwd,
            timer          = arguments.timer
        );

        // Your own initialization here
        return this;
    }
}
```

## Verbose output from modules

`modules.BaseModule` gives you a convenience helper for verbose logging:

```cfml
function verbose(any message) {
    if (variables.verboseEnabled) {
        out(message, "magenta", "italic");
    }
}
```

Usage inside your module:

```cfml
function main() {
    verbose("Starting main() in my-awesome-module");
    // ...
}
```

From the CLI, users enable this with:

```bash
lucli --verbose my-awesome-module
lucli --verbose modules run my-awesome-module
```

When `--verbose` is present, `variables.verboseEnabled` is `true` and `verbose()` will emit colored debug output.

## Timing and performance

If LuCLI is started with `--timing`, the `timingEnabled` flag and `timer` object let your module integrate with LuCLI’s timing output.

Typical module usage:

```cfml
function main() {
    
    variables.timer.start("my-awesome-module main");
    

    // Do some work …

    
    variables.timer.stop("my-awesome-module main");
    
}
```

As long as you have a timer object, you can start and stop timers. Making sure you use the same label for start and stop.

This lets your module’s work show up alongside LuCLI’s own timing measurements.

## Working with arguments and subcommands

Modules receive arguments exactly as described in
[Running Modules](../040_cfml-execution/040_modules/), but from a developer perspective you typically:

- Implement `function main()` for the default path.
- Optionally add additional functions like `function cleanup()` or `function report()`.
- Declare CFML arguments (`required string year`, `boolean force=false`, etc.).

Example:

```cfml
component extends="modules.BaseModule" {

    function main(required string path, boolean force = false) {
        verbose("Cleaning path: " & path);
        // …
    }

    function report(string format = "text") {
        if (format == "json") {
            out({ status = "ok", cwd = variables.cwd });
        } else {
            out("Status: ok (cwd=" & variables.cwd & ")");
        }
    }
}
```

CLI examples for this module:

```bash
# Calls main(path, force=false)
lucli cleaner /tmp/cache

# Calls main(path, force=true)
lucli cleaner /tmp/cache --force

# Calls report(format="json")
lucli cleaner report format=json
```

## Accessing environment and filesystem

`modules.BaseModule` also provides helpers you can use from any module:

- `getEnv(envKeyName, defaultValue="")`
  - Looks in `server.env` and `SERVER.system.environment` for environment variables.
  - Use this instead of `getEnvironmentVariable()` directly for consistency.

- `getAbsolutePath(cwd, path)`
  - Normalizes a (possibly relative) `path` against the module `cwd`.

Example:

```cfml
function main() {
    var apiKey = getEnv("MY_API_KEY", "");
    if (!len(apiKey)) {
        err("Missing MY_API_KEY in environment");
        return;
    }

    var target = getAbsolutePath(variables.cwd, "./data/output.json");
    verbose("Writing to " & target);
}
```

## Summary

- Use `lucli modules init <name>` to scaffold a module under `~/.lucli/modules`.
- Extend `modules.BaseModule` to get `out`, `err`, `verbose`, `getEnv`, and path helpers.
- Let LuCLI manage `init()` unless you have special needs; otherwise, preserve its signature.
- Use `variables.verboseEnabled` and `verbose()` for extra logging controlled by `--verbose`.
- Use `variables.timingEnabled` and `variables.timer` to integrate with `--timing`.
- Implement `main()` and optional subcommand functions to handle your module’s behavior.
