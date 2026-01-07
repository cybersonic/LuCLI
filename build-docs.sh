#!/bin/bash

# Build LuCLI Documentation
# This script regenerates the command reference from picocli and builds the docs site

set -e

echo "🔨 Building LuCLI..."
mvn clean package -DskipTests -q

echo "📝 Generating command reference from picocli..."
java -jar target/lucli.jar completion md > content/docs/060_reference/010_command-reference.md

echo "📚 Building documentation site..."
lucli markspresso build clean

echo "✅ Documentation build complete!"
echo "   View at: file://$(pwd)/public/docs/index.html"
