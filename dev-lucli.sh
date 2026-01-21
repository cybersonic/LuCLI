#!/bin/bash

# Try to load SDKMAN! and apply .sdkmanrc, but don't hard-fail if missing
if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
  # shellcheck source=/dev/null
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  if command -v sdk >/dev/null 2>&1; then
    echo "Using SDKMAN! environment from .sdkmanrc (if present)..."
    sdk env || echo "Warning: 'sdk env' failed; continuing with current Java"
  else
    echo "Warning: SDKMAN! init script sourced but 'sdk' not available; continuing"
  fi
else
  echo "Warning: SDKMAN! not found at \$HOME/.sdkman; using current Java:"
  java -version 2>&1 | head -n 1
fi
mvn exec:java --quiet -Dexec.mainClass="org.lucee.lucli.LuCLI"

# mvn clean package --activate-profiles binary --quiet
# ./target/lucli