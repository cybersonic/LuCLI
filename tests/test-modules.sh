#!/bin/bash
# LuCLI Modules Test Suite

cd "$(dirname "$0")/.." || exit 1
LUCLI_JAR="target/lucli.jar"

# Build if needed
if [ ! -f "$LUCLI_JAR" ]; then
    echo "Building LuCLI..."
    mvn package -DskipTests -q || exit 1
fi

echo "🧪 LuCLI Modules Test Suite"
echo "==========================="
echo ""

java -jar $LUCLI_JAR modules --help 2>&1 | grep -q "Manage LuCLI modules" && echo "✅ modules help" || echo "❌ modules help"
java -jar $LUCLI_JAR modules list 2>&1 | grep -q "LuCLI Modules" && echo "✅ modules list" || echo "❌ modules list"

MODULE_NAME="test-module-$$"
java -jar $LUCLI_JAR modules init $MODULE_NAME --no-git 2>&1 | grep -q "Successfully created" && echo "✅ modules init" || echo "❌ modules init"
test -f ~/.lucli/modules/$MODULE_NAME/Module.cfc && echo "✅ module files" || echo "❌ module files"
rm -rf ~/.lucli/modules/$MODULE_NAME

echo ""
echo "✅ Complete"
