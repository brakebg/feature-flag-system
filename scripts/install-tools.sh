#!/usr/bin/env bash
# Downloads the pinned gate tools into build/tools (gates 1, 10, 13).
# Each archive is checked against the checksum file of the same release.
# Usage: scripts/install-tools.sh [gitleaks|trivy|promtool|k6 ...]   (default: all)
set -euo pipefail

GITLEAKS_VERSION=8.30.1
TRIVY_VERSION=0.75.0
PROMETHEUS_VERSION=3.15.0
K6_VERSION=2.3.0

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DEST="$ROOT/build/tools"
mkdir -p "$DEST"

os=$(uname -s | tr '[:upper:]' '[:lower:]')
arch=$(uname -m)
case "$arch" in x86_64|amd64) arch=amd64 ;; arm64|aarch64) arch=arm64 ;; *) echo "unsupported arch $arch" >&2; exit 1 ;; esac

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

fetch() { curl -fsSL --retry 3 -o "$2" "$1"; }

verify_sum() { # file, checksum file
  local name sum
  name=$(basename "$1")
  sum=$(grep -E "[ *]${name}\$" "$2" | awk '{print $1}' | head -1)
  [ -n "$sum" ] || { echo "no checksum for $name" >&2; exit 1; }
  echo "$sum  $1" | shasum -a 256 -c - >/dev/null || { echo "checksum mismatch for $name" >&2; exit 1; }
}

install_gitleaks() {
  [ -x "$DEST/gitleaks" ] && "$DEST/gitleaks" version 2>/dev/null | grep -q "$GITLEAKS_VERSION" && return
  local a; case "$os-$arch" in darwin-arm64) a=darwin_arm64 ;; darwin-amd64) a=darwin_x64 ;; linux-amd64) a=linux_x64 ;; linux-arm64) a=linux_arm64 ;; esac
  local base="https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}"
  local f="gitleaks_${GITLEAKS_VERSION}_${a}.tar.gz"
  fetch "$base/$f" "$tmp/$f"; fetch "$base/gitleaks_${GITLEAKS_VERSION}_checksums.txt" "$tmp/gl.sums"
  verify_sum "$tmp/$f" "$tmp/gl.sums"
  tar -xzf "$tmp/$f" -C "$tmp" gitleaks && mv "$tmp/gitleaks" "$DEST/gitleaks"
}

install_trivy() {
  [ -x "$DEST/trivy" ] && "$DEST/trivy" --version 2>/dev/null | grep -q "$TRIVY_VERSION" && return
  local a; case "$os-$arch" in darwin-arm64) a=macOS-ARM64 ;; darwin-amd64) a=macOS-64bit ;; linux-amd64) a=Linux-64bit ;; linux-arm64) a=Linux-ARM64 ;; esac
  local base="https://github.com/aquasecurity/trivy/releases/download/v${TRIVY_VERSION}"
  local f="trivy_${TRIVY_VERSION}_${a}.tar.gz"
  fetch "$base/$f" "$tmp/$f"; fetch "$base/trivy_${TRIVY_VERSION}_checksums.txt" "$tmp/tv.sums"
  verify_sum "$tmp/$f" "$tmp/tv.sums"
  tar -xzf "$tmp/$f" -C "$tmp" trivy && mv "$tmp/trivy" "$DEST/trivy"
}

install_promtool() {
  [ -x "$DEST/promtool" ] && "$DEST/promtool" --version 2>&1 | grep -q "$PROMETHEUS_VERSION" && return
  local d="prometheus-${PROMETHEUS_VERSION}.${os}-${arch}"
  local base="https://github.com/prometheus/prometheus/releases/download/v${PROMETHEUS_VERSION}"
  fetch "$base/$d.tar.gz" "$tmp/$d.tar.gz"; fetch "$base/sha256sums.txt" "$tmp/pm.sums"
  verify_sum "$tmp/$d.tar.gz" "$tmp/pm.sums"
  tar -xzf "$tmp/$d.tar.gz" -C "$tmp" "$d/promtool" && mv "$tmp/$d/promtool" "$DEST/promtool"
}

install_k6() {
  [ -x "$DEST/k6" ] && "$DEST/k6" version 2>/dev/null | grep -q "$K6_VERSION" && return
  local base="https://github.com/grafana/k6/releases/download/v${K6_VERSION}"
  local d f
  case "$os" in darwin) d="k6-v${K6_VERSION}-macos-${arch}"; f="$d.zip" ;; linux) d="k6-v${K6_VERSION}-linux-${arch}"; f="$d.tar.gz" ;; esac
  fetch "$base/$f" "$tmp/$f"; fetch "$base/k6-v${K6_VERSION}-checksums.txt" "$tmp/k6.sums"
  verify_sum "$tmp/$f" "$tmp/k6.sums"
  case "$f" in *.zip) (cd "$tmp" && unzip -q "$f") ;; *) tar -xzf "$tmp/$f" -C "$tmp" ;; esac
  mv "$tmp/$d/k6" "$DEST/k6"
}

tools=("$@"); [ ${#tools[@]} -eq 0 ] && tools=(gitleaks trivy promtool k6)
for t in "${tools[@]}"; do "install_$t"; done
echo "tools ready in $DEST"
