---
title: Installation
layout: docs

---
LuCLI is distributed as a runnable JAR and as a self‑contained binary. You need a recent Java runtime (Java 17+) to run LuCLI or to build the binary.

## Requirements

- Java 17 or later on your PATH (`java -version`)
- (Optional) Maven 3.x if you want to build LuCLI yourself
- macOS ,  Linux or Windows (via .bat file)

## Installing from a JAR

If you have a pre‑built JAR:

```bash
java -jar lucli.jar --version
```

You can add a shell alias for convenience:

```bash
alias lucli='java -jar /path/to/lucli.jar'
```

## Installing the Self-Contained Binary
The self-coontained binary runs the jar without needing to type `java -jar` each time. We provide pre-built binaries for macOS and Linux. As well as a windows .bat file.

1. Download the latest binary from the [releases page](https://github.com/cybersonic/LuCLI/releases/latest).
1. Make it executable:

```bash
chmod +x lucli
``` 
3. Move it to a directory on your PATH, e.g.:

```bash
mv lucli /usr/local/bin/
```

4. Verify installation:

```bash
lucli --version
``` 
## Building from source

From the LuCLI project root:
```
./build.sh
```

This produces:

-  `target/lucli.jar` – runnable "fat" JAR with all dependencies included
-  `target/lucli` - self-contained binary (Linux or macOS)
-  `lucli-<version>.jar` - distributable JAR (not deps)


## Keeping LuCLI up to date

Because LuCLI is a CLI tool, the simplest update process is:

1. Download or build a new JAR/binary.
2. Replace your existing lucli.jar or lucli binary on disk.
3. Re‑open your shell or update any aliases if the path changed.