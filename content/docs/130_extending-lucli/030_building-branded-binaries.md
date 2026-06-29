---
title: Building Branded Binaries
layout: docs
---

This guide shows how to build a standalone binary for a module (for example `markspresso`, `bitbucket`, or your own module) using LuCLI's build-time branding properties.

The goal is to produce a downloadable binary where:

- the executable name is your module name (for example `markspresso`)
- profile defaults (display name, prompt prefix, home directory) are branded
- the binary runs your module command surface as the primary UX

## Quickstart (copy/paste)

### Markspresso

```bash
mvn clean package \
  -Dbranding.enabled=true \
  -Dbranding.binaryName=markspresso \
  -Dbranding.profileName=markspresso \
  -Dbranding.displayName=Markspresso \
  -Dbranding.promptPrefix=markspresso \
  -Dbranding.homeDirName=.markspresso \
  -Dbranding.backupsDirName=.markspresso_backups \
  -Dbranding.bannerText="Markspresso\nPowered by LuCLI"

install -m 755 target/lucli ~/.local/bin/markspresso
LUCLI_HOME=$HOME/.markspresso lucli modules install markspresso --url=https://github.com/cybersonic/markspresso.git --force
markspresso --help
```

### Bitbucket

```bash
mvn clean package \
  -Dbranding.enabled=true \
  -Dbranding.binaryName=bitbucket \
  -Dbranding.profileName=bitbucket \
  -Dbranding.displayName="Bitbucket CLI" \
  -Dbranding.promptPrefix=bitbucket \
  -Dbranding.homeDirName=.bitbucket \
  -Dbranding.backupsDirName=.bitbucket_backups \
  -Dbranding.bannerText="Bitbucket CLI\nPowered by LuCLI"

install -m 755 target/lucli ~/.local/bin/bitbucket
LUCLI_HOME=$HOME/.bitbucket lucli modules install bitbucket --url=https://github.com/your-org/lucli-bitbucket.git --force
bitbucket --help
```

## Prerequisites

- A clone of the LuCLI repository
- Java 21+
- Maven 3.x
- Your module code available as a LuCLI module (for example in a git repo)

## Build a branded binary

From the LuCLI repo root, run Maven with `branding.*` properties:

```bash
mvn clean package \
  -Dbranding.enabled=true \
  -Dbranding.binaryName=mytool \
  -Dbranding.profileName=mytool \
  -Dbranding.displayName=MyTool \
  -Dbranding.promptPrefix=mytool \
  -Dbranding.homeDirName=.mytool \
  -Dbranding.backupsDirName=.mytool_backups \
  -Dbranding.bannerText="MyTool\nPowered by LuCLI"
```

### What each property controls

- `branding.enabled`: turns build-time branding on
- `branding.binaryName`: binary name that should activate this branded profile
- `branding.profileName`: internal profile name
- `branding.displayName`: shown in version output
- `branding.promptPrefix`: prompt prefix in interactive usage
- `branding.homeDirName`: profile home directory under the user home
- `branding.backupsDirName`: optional explicit backups directory name
- `branding.bannerText`: banner text (`\n` becomes real newlines)

If branding is not enabled or the binary name does not match, LuCLI falls back to normal built-in profile resolution.

## Create the self-executing binary

The quick build script already assembles a self-executing binary at `target/lucli`:

```bash
./build.sh
```

If you want a branded build via `build.sh`, pass Maven branding properties through environment variables in your build process (or run the explicit Maven command above first, then assemble as needed).

## Install as your module binary

Install the built binary under your module command name:

```bash
install -m 755 target/lucli ~/.local/bin/mytool
```

Now invoking `mytool` sets `lucli.binary.name=mytool`, which activates the branded profile when `branding.binaryName=mytool`.

## Module location and first-run setup

Profile-aware paths mean module directories are profile-scoped. For example:

- `lucli` profile modules: `~/.lucli/modules/`
- branded `mytool` profile modules: `~/.mytool/modules/`

Install your module into the branded home:

```bash
LUCLI_HOME=$HOME/.mytool lucli modules install mytool --url=https://github.com/your-org/your-module.git --force
```

After this, running `mytool ...` can route directly to your module command surface.

## Example: Bitbucket binary

```bash
mvn clean package \
  -Dbranding.enabled=true \
  -Dbranding.binaryName=bitbucket \
  -Dbranding.profileName=bitbucket \
  -Dbranding.displayName=Bitbucket CLI \
  -Dbranding.promptPrefix=bitbucket \
  -Dbranding.homeDirName=.bitbucket \
  -Dbranding.backupsDirName=.bitbucket_backups \
  -Dbranding.bannerText="Bitbucket CLI\nPowered by LuCLI"

install -m 755 target/lucli ~/.local/bin/bitbucket
LUCLI_HOME=$HOME/.bitbucket lucli modules install bitbucket --url=https://github.com/your-org/lucli-bitbucket.git --force
```

## Release workflow suggestion

For module repositories, a practical release pipeline is:

1. checkout LuCLI
2. build with module-specific `branding.*` values
3. rename/install artifact as your module binary name
4. publish binary as a release asset in the module repo
5. optionally run a smoke check (`<binary> --version`, `<binary> --help`)

This keeps one shared LuCLI codebase while allowing branded module distributions.
