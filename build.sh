#!/bin/bash
buildDockerImage=false
installLocally=false

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
mvn clean package --activate-profiles binary -Dmaven.test.skip=true --quiet
if [ $? -ne 0 ]; then
    echo "Maven build failed!"
    exit 1
fi

# Test the JAR and binary
echo "Testing the built JAR and binary..."
jarversion=$(java -jar target/lucli.jar --version)
binaryversion=$(target/lucli --version)
if [ "$jarversion" != "$binaryversion" ] ; then
    echo "Version mismatch between JAR and binary!"
    echo "JAR version: $jarversion"
    echo "Binary version: $binaryversion"
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
    echo "Build and tests completed successfully."
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

echo "Build and tests completed successfully."
echo "Multi-platform images pushed for: linux/amd64, linux/arm64/v8"
echo "You can now run the Docker image with:"
echo "  docker run --rm markdrew/lucli:${VERSION} [options]"

