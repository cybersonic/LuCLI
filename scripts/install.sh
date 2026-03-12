#!/usr/bin/env sh
set -eu

REPO="${LUCLI_REPO:-cybersonic/LuCLI}"
BIN_NAME="lucli"
INSTALL_DIR="${LUCLI_INSTALL_DIR:-}"
REQUESTED_VERSION="${LUCLI_VERSION:-latest}"
GH_API="${LUCLI_GITHUB_API:-https://api.github.com}"
GH_DOWNLOAD_BASE="${LUCLI_DOWNLOAD_BASE:-https://github.com}"

info() { printf '%s\n' "$*"; }
warn() { printf 'warning: %s\n' "$*" >&2; }
err() { printf 'error: %s\n' "$*" >&2; exit 1; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || err "required command not found: $1"
}

http_get() {
  url="$1"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$url"
    return
  fi
  if command -v wget >/dev/null 2>&1; then
    wget -qO- "$url"
    return
  fi
  err "curl or wget is required"
}

download_to() {
  url="$1"
  out="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 2 --connect-timeout 15 -o "$out" "$url"
    return
  fi
  if command -v wget >/dev/null 2>&1; then
    wget -O "$out" "$url"
    return
  fi
  err "curl or wget is required"
}

normalize_os() {
  os="$(uname -s | tr '[:upper:]' '[:lower:]')"
  case "$os" in
    darwin*) printf 'macos' ;;
    linux*) printf 'linux' ;;
    *)
      err "unsupported OS: $os (supported: macOS, Linux)"
      ;;
  esac
}

normalize_arch() {
  arch="$(uname -m | tr '[:upper:]' '[:lower:]')"
  case "$arch" in
    x86_64|amd64) printf 'x86_64' ;;
    arm64|aarch64) printf 'aarch64' ;;
    *) printf '%s' "$arch" ;;
  esac
}

resolve_latest_tag() {
  api_url="${GH_API}/repos/${REPO}/releases/latest"
  body="$(http_get "$api_url")" || err "failed to query GitHub releases API"
  tag="$(printf '%s' "$body" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
  [ -n "$tag" ] || err "could not determine latest release tag"
  printf '%s' "$tag"
}

resolve_tag() {
  version="$1"
  if [ "$version" = "latest" ]; then
    resolve_latest_tag
    return
  fi
  case "$version" in
    v*) printf '%s' "$version" ;;
    *) printf 'v%s' "$version" ;;
  esac
}

pick_install_dir() {
  if [ -n "$INSTALL_DIR" ]; then
    printf '%s' "$INSTALL_DIR"
    return
  fi
  if [ -n "${HOME:-}" ] && [ -d "$HOME/.local/bin" ]; then
    printf '%s' "$HOME/.local/bin"
    return
  fi
  if [ -w "/usr/local/bin" ]; then
    printf '/usr/local/bin'
    return
  fi
  if [ -n "${HOME:-}" ]; then
    printf '%s' "$HOME/.local/bin"
    return
  fi
  printf '/usr/local/bin'
}

main() {
  need_cmd uname
  os="$(normalize_os)"
  arch="$(normalize_arch)"

  # Current published assets are OS-specific but not arch-specific.
  # Keep arch detection for future-proofing and visibility.
  info "Detected platform: ${os} (${arch})"

  tag="$(resolve_tag "$REQUESTED_VERSION")"
  version="${tag#v}"
  asset="${BIN_NAME}-${version}-${os}"
  url="${GH_DOWNLOAD_BASE}/${REPO}/releases/download/${tag}/${asset}"

  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT INT TERM
  tmp_bin="${tmp_dir}/${BIN_NAME}"

  info "Downloading ${asset} from ${tag}..."
  download_to "$url" "$tmp_bin" || err "download failed: $url"
  chmod +x "$tmp_bin"

  target_dir="$(pick_install_dir)"
  target_path="${target_dir}/${BIN_NAME}"
  mkdir -p "$target_dir"

  if [ -w "$target_dir" ]; then
    install_cmd="cp \"$tmp_bin\" \"$target_path\""
  else
    if command -v sudo >/dev/null 2>&1; then
      install_cmd="sudo cp \"$tmp_bin\" \"$target_path\""
    else
      err "no write access to ${target_dir} and sudo is unavailable; set LUCLI_INSTALL_DIR to a writable directory"
    fi
  fi

  # shellcheck disable=SC2086
  sh -c "$install_cmd"
  chmod +x "$target_path" 2>/dev/null || true

  info "Installed ${BIN_NAME} to ${target_path}"
  if command -v "$BIN_NAME" >/dev/null 2>&1; then
    info "Verifying install..."
    "$BIN_NAME" --version || warn "installed, but version check failed"
  else
    warn "${target_dir} is not currently on PATH"
    info "Add this to your shell config:"
    info "  export PATH=\"${target_dir}:\$PATH\""
  fi
}

main "$@"
