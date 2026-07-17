#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "" || "${2:-}" == "" ]]; then
  echo "Usage: $0 <lucli-binary-path> <version>"
  exit 1
fi

if ! command -v dpkg-deb >/dev/null 2>&1; then
  echo "Error: dpkg-deb is required to build Debian packages."
  exit 1
fi

BINARY_PATH="$1"
VERSION="$2"
ARCH="${3:-amd64}"
PACKAGE_NAME="lucli"
TARGET_DIR="target"
WORK_DIR="$TARGET_DIR/deb-build"

if [[ ! -f "$BINARY_PATH" ]]; then
  echo "Error: binary not found at $BINARY_PATH"
  exit 1
fi

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/DEBIAN" "$WORK_DIR/usr/bin"

cat > "$WORK_DIR/DEBIAN/control" <<EOF
Package: $PACKAGE_NAME
Version: $VERSION
Section: utils
Priority: optional
Architecture: $ARCH
Maintainer: Mark Drew <mark@lucli.dev>
Depends: default-jre-headless | java-runtime-headless
Homepage: https://lucli.dev
Description: LuCLI - Lucee Command Line Interface
 LuCLI is a command-line interface for Lucee CFML with server management,
 module tooling, and script execution support.
EOF

install -m 0755 "$BINARY_PATH" "$WORK_DIR/usr/bin/lucli"

OUTPUT="$TARGET_DIR/${PACKAGE_NAME}_${VERSION}_${ARCH}.deb"
dpkg-deb --build "$WORK_DIR" "$OUTPUT" >/dev/null
echo "Built Debian package: $OUTPUT"
