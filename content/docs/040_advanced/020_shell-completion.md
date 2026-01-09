---
title: Shell Completion
layout: docs
---

# Shell Completion Support

LuCLI now supports tab-completion for bash and zsh shells on macOS and Linux.

## Quick Start

### Install bash completion:
```bash
lucli completion bash | sudo tee /etc/bash_completion.d/lucli
```

Then reload bash or add to `.bashrc`:
```bash
source /etc/bash_completion.d/lucli
```

### Install zsh completion:

The generated zsh completion script is a **bash-style completion script** that works in zsh via `bashcompinit`.
It must be **sourced** (zsh will not auto-load it via `site-functions`).

```bash
# Save the script
mkdir -p ~/.zsh
lucli completion zsh > ~/.zsh/lucli_completion

# Source it from ~/.zshrc
echo 'source ~/.zsh/lucli_completion' >> ~/.zshrc

# Reload zsh
exec zsh
```

### Manual Installation (without sudo)

If you don't have sudo access or prefer local installation:

**Bash:**
```bash
# Create user completion directory if it doesn't exist
mkdir -p ~/.bash_completion.d

# Generate and save completion script
lucli completion bash > ~/.bash_completion.d/lucli

# Add to ~/.bashrc
echo 'for file in ~/.bash_completion.d/*; do source "$file"; done' >> ~/.bashrc
```

**Zsh:**
```bash
# Save the script somewhere stable
mkdir -p ~/.zsh
lucli completion zsh > ~/.zsh/lucli_completion

# Source it from ~/.zshrc
echo 'source ~/.zsh/lucli_completion' >> ~/.zshrc

# Reload zsh
exec zsh
```

## How It Works

### Dynamic Version Completion

LuCLI uses a **dynamic completion system** for Lucee versions that automatically stays up-to-date:

**1. Hidden `versions-list` Command**
- Fetches available Lucee versions from `https://update.lucee.org/rest/update/provider/list`
- Caches results in `~/.lucli/lucee-versions.json` for 24 hours
- Falls back to cache if API is unavailable
- Called automatically by shell completion scripts

**2. Completion Script Generation**
- When you run `lucli completion bash` or `lucli completion zsh`, picocli generates a completion script
- LuCLI post-processes the script to replace hardcoded version arrays with: 
  ```bash
  local version_option_args=($(lucli versions-list 2>/dev/null))
  ```
- This means **you never need to regenerate the completion script** when new Lucee versions are released
- Versions are fetched dynamically at completion time and cached for performance

**3. Cache Management**
- Cache refreshes automatically every 24 hours
- To force refresh: `rm ~/.lucli/lucee-versions.json`
- To view cached versions: `lucli versions-list`

## Architecture

### Java Components

**CompletionCommand** (`cli/commands/CompletionCommand.java`)
- Generates bash completion scripts using picocli's built-in `AutoComplete` generator
- For zsh, we reuse the same bash completion script (it includes `bashcompinit` glue)
- LuCLI post-processes the generated script to make `--version` completion dynamic via `lucli versions-list`

**VersionsListCommand** (`cli/commands/VersionsListCommand.java`)
- Hidden command used by completion scripts to fetch Lucee versions
- Fetches from `https://update.lucee.org/rest/update/provider/list` and caches results for 24 hours

## Testing

Run the completion test suite:
```bash
./tests/test-completion.sh
```

Tests cover:
- Completion script generation
- Syntax validation (bash)
- Dynamic Lucee version listing (`versions-list`)

## Supported Shells

- ✅ **Bash** - Linux and macOS
- ✅ **Zsh** - Linux and macOS
- ❌ **PowerShell** - Not yet supported

## Platform Support

- ✅ **macOS** - Full support via bash/zsh
- ✅ **Linux** - Full support via bash/zsh
- ❌ **Windows** - PowerShell completion planned for future release

## Debugging

If completions look wrong after you upgrade LuCLI:
1. Regenerate your completion script: `lucli completion bash` or `lucli completion zsh`
2. Re-source your shell config (or restart your shell)
3. For zsh, rebuild the completion cache if needed: `rm -f ~/.zcompdump* && exec zsh`

## Integration Notes

The generated completion script is a bash-style completion function (`_lucli_completion`) that also works in zsh via `bashcompinit`.

Dynamic Lucee version completion is handled by calling `lucli versions-list` on demand (with caching).

## Troubleshooting

### Completions not working after installation

**Bash:**
1. Verify the script is in the right location: `ls -la /etc/bash_completion.d/lucli` or `~/.bash_completion.d/lucli`
2. Reload bash: `exec bash`
3. Test with: `lucli <TAB>`

**Zsh:**
1. Verify the script exists: `ls -la ~/.zsh/lucli_completion`
2. Verify it is sourced from your `~/.zshrc`: `grep 'source ~/.zsh/lucli_completion' ~/.zshrc`
3. Reload zsh: `exec zsh`
4. Test with: `lucli <TAB>`

### Completions are slow

Most completion suggestions are handled locally by the generated shell script. The main time you may notice is when completing `--version`, because it calls `lucli versions-list` (with a 24h cache).

If completions feel slow:
1. Consider building and using the self-executing binary (`mvn package -Pbinary`) to reduce startup time
2. Verify your completion script is being loaded only once (avoid sourcing it multiple times)
3. Ensure `lucli versions-list` is working and the cache file exists (`~/.lucli/lucee-versions.json`)

## Future Enhancements

- PowerShell completion support for Windows users
- Improved option descriptions in completions
- Custom completion providers for dynamic values (e.g., server names)
- Installation helper script (`lucli completion install`)
- Auto-detection of shell and installation
