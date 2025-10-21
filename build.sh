#!/bin/bash
buildDockerImage=false
if [[ "$1" == "docker" ]]; then
    buildDockerImage=true
fi

# Build the JAR and binary
mvn clean package -Pbinary -q

# Test the JAR and binary
echo "Testing the built JAR and binary..."
java -jar target/lucli.jar --version
echo "Testing the built binary..."
target/lucli --version
target/lucli --help

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
    --platform linux/amd64,linux/arm/v7,linux/arm64/v8 \
    -t markdrew/lucli:${VERSION} \
    -t markdrew/lucli:latest \
    --push \
    .
    # Report remote image sizes (requires docker and jq)
    
echo "Testing Docker image..."
docker run --rm markdrew/lucli:${VERSION} --version

echo "Build and tests completed successfully."
echo "Multi-platform images pushed for: linux/amd64, linux/arm/v7, linux/arm64/v8"
echo "You can now run the Docker image with:"
echo "  docker run --rm markdrew/lucli:${VERSION} [options]"

