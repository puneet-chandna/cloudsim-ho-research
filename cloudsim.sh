#!/usr/bin/env bash
set -euo pipefail
launcher_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 -B "$launcher_root/scripts/cloudsim.py" "$@"
