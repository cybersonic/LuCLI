#!/bin/bash
buildDockerImage=false
buildDockerImageLocal=false
installLocally=false
purgeDependencies=false
moduleSources=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    docker)
      buildDockerImage=true
      ;;
    dockerlocal)
      buildDockerImageLocal=true
      ;;
    install)
      installLocally=true
      ;;
    clear)
      purgeDependencies=true
      ;;
    --module-source)
      if [[ -z "${2:-}" ]]; then
        echo "Error: --module-source requires a directory path."
        exit 1
      fi
      moduleSources+=("$2")
      shift
      ;;
    --module-source=*)
      moduleSources+=("${1#*=}")
      ;;
    *)
      echo "Warning: unknown option '$1' ignored."
      ;;
  esac
  shift
done
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
if [[ "$purgeDependencies" == true ]]; then
    mvn dependency:purge-local-repository -DreResolve=false -DactTransitively=false
fi

# Bump the patch version in pom.xml (e.g. 0.2.2-SNAPSHOT → 0.2.3-SNAPSHOT)
# This replicates the version bump that the -Pbinary profile used to do.
if [[ "${LUCLI_SKIP_VERSION_BUMP:-false}" == "true" || "${LUCLI_SKIP_VERSION_BUMP:-0}" == "1" ]]; then
    echo "Skipping patch version bump (LUCLI_SKIP_VERSION_BUMP=${LUCLI_SKIP_VERSION_BUMP})."
else
    echo "Bumping patch version..."
    mvn -q build-helper:parse-version versions:set \
        -DnewVersion='${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion}-SNAPSHOT' \
        -DgenerateBackupPoms=false
fi
INSTALL_TARGET="${LUCLI_INSTALL_TARGET:-$HOME/.local/bin/lucli}"

# Build the JAR and binary
# Use -Dmaven.test.skip=true so Maven does not compile or run tests during this
# quick-build path. The recommended way to run the full test suite remains
# ./tests/test.sh when you want full verification.
#
# Note: we intentionally do NOT activate the "binary" Maven profile here.
# That profile's additional packaging steps interfere with the shaded JAR
# contents and result in "ClassNotFoundException: org.lucee.lucli.LuCLI".
# A plain `mvn clean package` produces a correct shaded jar, so we build that
# first and then create the self-executing binary ourselves below.
echo "Building LuCLI JAR and binary..."
rm -rf target

if [[ ${#moduleSources[@]} -gt 0 ]]; then
    mkdir -p target/modules-install
    echo "Bundling module sources into target/modules-install:"
    for moduleSource in "${moduleSources[@]}"; do
        moduleSource="${moduleSource%/}"
        if [[ ! -d "$moduleSource" ]]; then
            echo "Error: module source directory does not exist: $moduleSource"
            exit 1
        fi
        if [[ ! -f "$moduleSource/Module.cfc" ]]; then
            echo "Error: module source '$moduleSource' is missing Module.cfc"
            exit 1
        fi
        moduleName="$(basename "$moduleSource")"
        targetModuleDir="target/modules-install/$moduleName"
        rm -rf "$targetModuleDir"
        mkdir -p "$targetModuleDir"
        (
          cd "$moduleSource" && tar --exclude='.git' -cf - .
        ) | (
          cd "$targetModuleDir" && tar -xf -
        )
        echo "  - $moduleName ($moduleSource)"
    done
fi
mvn package -q -Dmaven.test.skip=true -Djreleaser.dry.run=true
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
    echo "Installing lucli binary to ${INSTALL_TARGET}..."
    INSTALL_DIR=$(dirname "$INSTALL_TARGET")
    mkdir -p "$INSTALL_DIR"
    if [[ ! -w "$INSTALL_DIR" ]]; then
        echo "Error: install directory is not writable: $INSTALL_DIR"
        exit 1
    fi
    cp target/lucli "$INSTALL_TARGET"
    if [ $? -ne 0 ]; then
        echo "Error: failed to install lucli binary to ${INSTALL_TARGET}"
        exit 1
    fi
    echo "Installed lucli binary to ${INSTALL_TARGET}"
fi

if [[ "$buildDockerImageLocal" == true ]]; then
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

