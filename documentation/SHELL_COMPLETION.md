# Shell Completion Support

LuCLI now supports tab-completion for bash and zsh shells on macOS and Linux.

## Quick Start

### Install bash completion:
```bash
lucli completion bash | sudo tee /etc/bash_completion.d/lucli
```

### Install zsh completion:
```bash
lucli completion zsh | sudo tee /usr/share/zsh/site-functions/_lucli
```

Then reload your shell or source the completion file directly.

## How It Works

### Two-Part System

**1. Hidden `__complete` Endpoint**
- Tests and powers shell integration
- Can be called directly to test completions
- Example: `lucli __complete --words="lucli server" --current=1`
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
LUCLI_DEBUG_COMPLETION=1 lucli __complete --words="lucli server" --current=1
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

## Future Enhancements

- PowerShell completion support for Windows users
- Improved option descriptions in completions
- Custom completion providers for dynamic values (e.g., server names)
- Installation helpers for system-wide completion setup
