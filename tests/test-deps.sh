#!/bin/bash
# LuCLI Dependency Management Test Suite
# Focused on deps install dry-run and nested dependency introspection

cd "$(dirname "$0")/.." || exit 1

REPO_ROOT="$(pwd)"
LUCLI_JAR="$REPO_ROOT/target/lucli.jar"

# Build if needed
if [ ! -f "$LUCLI_JAR" ]; then
  echo "Building LuCLI..."
  mvn package -DskipTests -Pbinary -q || exit 1
fi

echo "🧪 LuCLI Dependency Management Test Suite"
echo "========================================="
echo ""

FAILED=0
TOTAL=0

assert_contains() {
  local name="$1"
  local haystack="$2"
  local needle="$3"

  TOTAL=$((TOTAL + 1))
  if echo "$haystack" | grep -Fq "$needle"; then
    echo "✅ $name"
  else
    echo "❌ $name"
    echo "  Expected to find: $needle"
    echo "  Output (first 10 lines):"
    echo "$haystack" | head -n 10 | sed 's/^/    /'
    FAILED=$((FAILED + 1))
  fi
  echo ""
}

assert_not_contains() {
  local name="$1"
  local haystack="$2"
  local needle="$3"

  TOTAL=$((TOTAL + 1))
  if echo "$haystack" | grep -Fq "$needle"; then
    echo "❌ $name"
    echo "  Expected NOT to find: $needle"
    echo "  Output (first 10 lines):"
    echo "$haystack" | head -n 10 | sed 's/^/    /'
    FAILED=$((FAILED + 1))
  else
    echo "✅ $name"
  fi
  echo ""
}

assert_exit_code() {
  local name="$1"
  local exit_code="$2"
  local expected="$3"

  TOTAL=$((TOTAL + 1))
  if [ "$exit_code" -eq "$expected" ]; then
    echo "✅ $name (exit=$exit_code)"
  else
    echo "❌ $name (exit=$exit_code, expected=$expected)"
    FAILED=$((FAILED + 1))
  fi
  echo ""
}

# Use an isolated LUCLI_HOME to avoid polluting user config
LUCLI_HOME_TMP="$(pwd)/tests/tmp-deps-lucli-home-$$"
export LUCLI_HOME="$LUCLI_HOME_TMP"
rm -rf "$LUCLI_HOME_TMP"
mkdir -p "$LUCLI_HOME_TMP"

# Create a temporary dependency test project with nested lucee.json files
TMP_ROOT="$(pwd)/tests/tmp-deps-$$"
ROOT_PROJECT="$TMP_ROOT/root-project"
NESTED1="$ROOT_PROJECT/dependencies/nested1"
NESTED2="$NESTED1/dependencies/nested2"
NESTED3="$NESTED2/dependencies/nested3"
NESTED4="$NESTED3/dependencies/nested4"  # For depth-limit check

rm -rf "$TMP_ROOT"
mkdir -p "$NESTED4"

# Root project lucee.json with one git dependency and a dev environment
cat > "$ROOT_PROJECT/lucee.json" << 'EOF'
{
  "name": "root-project",
  "dependencies": {
    "nested1": {
      "source": "git",
      "installPath": "dependencies/nested1"
    }
  },
  "dependencySettings": {
    "installDevDependencies": true
  },
  "environments": {
    "dev": {
      "dependencySettings": {
        "installDevDependencies": false
      }
    }
  }
}
EOF

# Nested level 1 with dependency to level 2, no environments (to trigger lenient env handling)
cat > "$NESTED1/lucee.json" << 'EOF'
{
  "name": "nested1",
  "dependencies": {
    "nested2": {
      "source": "git",
      "installPath": "dependencies/nested2"
    }
  }
}
EOF

# Nested level 2 with dependency to level 3
cat > "$NESTED2/lucee.json" << 'EOF'
{
  "name": "nested2",
  "dependencies": {
    "nested3": {
      "source": "git",
      "installPath": "dependencies/nested3"
    }
  }
}
EOF

# Nested level 3 with dependency to level 4
cat > "$NESTED3/lucee.json" << 'EOF'
{
  "name": "nested3",
  "dependencies": {
    "nested4": {
      "source": "git",
      "installPath": "dependencies/nested4"
    }
  }
}
EOF

# Nested level 4 has no further dependencies (terminal project)
cat > "$NESTED4/lucee.json" << 'EOF'
{
  "name": "nested4",
  "dependencies": {}
}
EOF

# Move into root project for running deps install
cd "$ROOT_PROJECT" || exit 1

# 1) Basic dry-run should only report root project deps
OUTPUT_ROOT_ONLY=$(java -jar "$LUCLI_JAR" deps install --dry-run 2>&1)
EC_ROOT_ONLY=$?

assert_exit_code "deps install --dry-run (root project) exits 0" "$EC_ROOT_ONLY" 0
assert_contains "deps install --dry-run shows root header" "$OUTPUT_ROOT_ONLY" "Would install:"
assert_not_contains "deps install --dry-run does not show nested header" "$OUTPUT_ROOT_ONLY" "[Nested:"

# 2) Dry-run with --include-nested-deps should report nested projects
OUTPUT_WITH_NESTED=$(java -jar "$LUCLI_JAR" deps install --dry-run --include-nested-deps 2>&1)
EC_WITH_NESTED=$?

assert_exit_code "deps install --dry-run --include-nested-deps exits 0" "$EC_WITH_NESTED" 0
assert_contains "dry-run with nested shows first nested project" "$OUTPUT_WITH_NESTED" "[Nested: dependencies/nested1"
assert_contains "dry-run with nested shows second nested project" "$OUTPUT_WITH_NESTED" "[Nested: dependencies/nested1/dependencies/nested2"

# 3) Dry-run with env=dev should be strict for root but lenient for nested projects
OUTPUT_ENV_DEV=$(java -jar "$LUCLI_JAR" deps install --dry-run --include-nested-deps --env=dev 2>&1)
EC_ENV_DEV=$?

assert_exit_code "deps install --dry-run --include-nested-deps --env=dev exits 0" "$EC_ENV_DEV" 0
assert_contains "nested project without env logs lenient warning" "$OUTPUT_ENV_DEV" "Environment 'dev' not found in nested project"

# 4) Depth limit enforcement: level 4 should be skipped
assert_contains "depth limit warning for deeply nested project" "$OUTPUT_WITH_NESTED" "maximum nested dependency depth"

# Cleanup
cd "$REPO_ROOT" || exit 1
rm -rf "$TMP_ROOT" "$LUCLI_HOME_TMP"

echo ""
echo "Dependency Management Tests: $((TOTAL - FAILED)) passed, $FAILED failed (total $TOTAL)"

if [ "$FAILED" -eq 0 ]; then
  echo "✅ Dependency tests passed"
  exit 0
else
  echo "❌ Dependency tests failed"
  exit 1
fi
