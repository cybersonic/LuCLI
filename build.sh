#!/bin/bash
buildDockerImage=false
installLocally=false
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


if [[ "$1" == "docker" ]]; then
    buildDockerImage=true
fi

if [[ "$1" == "install" ]]; then
    installLocally=true
fi

if [[ "$1" == "clear" ]]; then
    mvn dependency:purge-local-repository -DreResolve=false -DactTransitively=false
fi

# Build the JAR and binary
# Use -Dmaven.test.skip=true so Maven does not compile or run tests during this
# quick-build path. The recommended way to run the full test suite remains
# ./tests/test.sh when you want full verification.
#
# Note: we intentionally do NOT activate the "binary" Maven profile here.
# That profile performs version-bumping and additional packaging steps which
# currently interfere with the shaded JAR contents and result in
# "ClassNotFoundException: org.lucee.lucli.LuCLI" when running the jar.
# A plain `mvn clean package` produces a correct shaded jar, so we build that
# first and then create the self-executing binary ourselves below.
echo "Building LuCLI JAR and binary..."
mvn clean package -q -Dmaven.test.skip=true
if [ $? -ne 0 ]; then
    echo "Maven build failed!"
    exit 1
fi

# Create the self-executing Unix binary (lucli shell + shaded JAR)
# This mirrors the antrun "create-unix-binary" task from the binary profile
# but avoids the version-bumping side effects of that profile for quick builds.
cat src/bin/lucli.sh target/lucli.jar > target/lucli
chmod 755 target/lucli

# Test the JAR and binary
echo "Testing the built JAR and binary..."

# Run the shaded JAR and capture its version. If this fails, abort immediately
if ! jarversion=$(java -jar target/lucli.jar --version); then
    echo "Error: failed to execute shaded JAR (target/lucli.jar)."
    echo "See the error above. Aborting build; binary will not be installed."
    exit 1
fi

# Run the self-executing binary and capture its version. If this fails, abort
if ! binaryversion=$(target/lucli --version); then
    echo "Error: failed to execute lucli binary (target/lucli)."
    echo "See the error above. Aborting build; binary will not be installed."
    exit 1
fi

if [ "$jarversion" != "$binaryversion" ] ; then
    echo "Version mismatch between JAR and binary!"
    echo "JAR version: $jarversion"
    echo "Binary version: $binaryversion"
    echo "Aborting build; binary will not be installed."
    exit 1
fi
echo "$jarversion"
echo "----------------"

echo "Binary file size:"
echo "$(ls -lh target/lucli | awk '{print $5}')"
echo "----------------"

echo "Jar file size:"
echo "$(ls -lh target/lucli.jar | awk '{print $5}')"
echo "----------------"


if [ "$installLocally" = true ] ; then
    echo "Installing lucli binary to ~/bin/lucli..."
    cp target/lucli ~/bin/lucli
    echo "Installed lucli binary to ~/bin/lucli"
fi

if [[ "$1" == "dockerlocal" ]]; then
    VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
    echo "Building local Docker image...'markdrew/lucli:${VERSION}'"

    docker build -t markdrew/lucli:${VERSION} .
    
    echo "Testing Docker image..."
    docker run --rm markdrew/lucli:${VERSION} --version
    echo "  docker run --rm markdrew/lucli:${VERSION} [options]"
fi



if [ "$buildDockerImage" = false ] ; then
    echo "✅ Build and tests completed successfully."
    exit 0
fi
# Build Docker image
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
echo "Building multi-platform Docker image...'markdrew/lucli:${VERSION}'"

# Create and use buildx builder if it doesn't exist
docker buildx create --name multiarch --use 2>/dev/null || docker buildx use multiarch

# Build multi-platform images
docker buildx build \
    --platform linux/amd64,linux/arm64/v8,linux/arm64 \
    -t markdrew/lucli:${VERSION} \
    -t markdrew/lucli:latest \
    --push \
    .
    # Report remote image sizes (requires docker and jq)
    
echo "Testing Docker image..."
docker run --rm markdrew/lucli:${VERSION} --version

# docker run -it --entrypoint /bin/bash markdrew/lucli:latest

echo "✅ Build and tests completed successfully."
echo "Multi-platform images pushed for: linux/amd64, linux/arm64/v8"
echo "You can now run the Docker image with:"
echo "  docker run --rm markdrew/lucli:${VERSION} [options]"

