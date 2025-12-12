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
```bash
lucli completion zsh | sudo tee /usr/share/zsh/site-functions/_lucli
```

For zsh, the completion function is auto-loaded from `site-functions`. Reload your shell:
```bash
exec zsh
```

Or manually source it:
```bash
source /usr/share/zsh/site-functions/_lucli
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
# Create user completion directory if it doesn't exist
mkdir -p ~/.zsh/completions

# Generate and save completion script
lucli completion zsh > ~/.zsh/completions/_lucli

# Add to ~/.zshrc
echo 'fpath=(~/.zsh/completions $fpath)' >> ~/.zshrc
echo 'autoload -Uz compinit && compinit' >> ~/.zshrc

# Reload zsh
exec zsh
```

## How It Works

### Two-Part System

**1. Hidden `__complete` Endpoint**
- Tests and powers shell integration
- Can be called directly to test completions
- Example: `lucli __complete --words="lucli server s" --current=2` returns `set\nstart\nstatus\nstop`
- Returns newline-separated completion candidates
- Testable without shell integration

**2. Public `completion` Subcommand**
- Generates shell-specific completion scripts
- Scripts source the `__complete` endpoint
- Subcommands:
  - `lucli completion bash` - generates bash completion script
  - `lucli completion zsh` - generates zsh completion script

## Architecture

### Java Components

**CompletionProvider** (`cli/completion/CompletionProvider.java`)
- Introspects Picocli's CommandSpec model
- Extracts available commands, subcommands, and options
- Navigates command hierarchy

**DynamicCompleter** (`cli/completion/DynamicCompleter.java`)
- Parses shell completion context
- Resolves command paths
- Filters candidates by prefix
- Formats output for shell consumption

**CompleteCommand** (`cli/commands/CompleteCommand.java`)
- Hidden Picocli command (`__complete`)
- Receives `--words` and `--current` arguments
- Returns completion candidates
- Supports debug mode via `LUCLI_DEBUG_COMPLETION` environment variable

**CompletionCommand** (`cli/commands/CompletionCommand.java`)
- Public subcommand for script generation
- Nested commands: `bash`, `zsh`
- Generates appropriate shell-specific completion functions

## Testing

Run the completion test suite:
```bash
./tests/test-completion.sh
```

Tests cover:
- Dynamic completion endpoint functionality
- Completion script generation
- Syntax validation (bash and zsh)
- Edge cases and error handling
- 16 tests, 100% pass rate

## Supported Shells

- ✅ **Bash** - Linux and macOS
- ✅ **Zsh** - Linux and macOS
- ❌ **PowerShell** - Not yet supported

## Platform Support

- ✅ **macOS** - Full support via bash/zsh
- ✅ **Linux** - Full support via bash/zsh
- ❌ **Windows** - PowerShell completion planned for future release

## Debugging

To debug completion issues, set the environment variable:
```bash
LUCLI_DEBUG_COMPLETION=1 lucli __complete --words="lucli server s" --current=2
```

This will output debug information to stderr while returning completions to stdout.

## Integration Examples

### Bash Integration Flow
1. User types `lucli server st<TAB>` in bash
2. Bash calls the `_lucli_completion` function
3. Function invokes: `lucli __complete --words="lucli server st" --current=2`
4. LuCLI returns: `start`
5. Bash completes to: `lucli server start`

### Zsh Integration Flow
1. User types `lucli modules li<TAB>` in zsh
2. Zsh calls the `_lucli` function
3. Function invokes: `lucli __complete --words="lucli modules li" --current=2`
4. LuCLI returns: `list`
5. Zsh completes to: `lucli modules list`

## Troubleshooting

### Completions not working after installation

**Bash:**
1. Verify the script is in the right location: `ls -la /etc/bash_completion.d/lucli` or `~/.bash_completion.d/lucli`
2. Reload bash: `exec bash`
3. Test with: `lucli <TAB>`

**Zsh:**
1. Verify the script is in the right location: `ls -la /usr/share/zsh/site-functions/_lucli` or `~/.zsh/completions/_lucli`
2. Rebuild completion cache: `rm -f ~/.zcompdump && exec zsh`
3. Test with: `lucli <TAB>`

### Completions are slow

Completions are generated on-demand by invoking `lucli __complete`. If they're slow:
1. Check if `lucli` is in your PATH: `which lucli`
2. Verify the JAR or binary is executable and accessible
3. Consider pre-building the binary with `mvn package -Pbinary` for faster startup

### Test completion directly

Debug completions without shell integration:
```bash
# Enable debug output
LUCLI_DEBUG_COMPLETION=1 lucli __complete --words="lucli server s" --current=2

# Check what completions are available
lucli __complete --words="lucli " --current=1
```

## Future Enhancements

- PowerShell completion support for Windows users
- Improved option descriptions in completions
- Custom completion providers for dynamic values (e.g., server names)
- Installation helper script (`lucli completion install`)
- Auto-detection of shell and installation
