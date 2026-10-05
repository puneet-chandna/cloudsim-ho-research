#!/usr/bin/env bash
# Compatibility entry point; use ./lattora.sh for source development.
set -euo pipefail
launcher_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "$launcher_root/lattora.sh" "$@"
